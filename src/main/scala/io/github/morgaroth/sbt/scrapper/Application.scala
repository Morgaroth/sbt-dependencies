package io.github.morgaroth.sbt.scrapper

import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.novus.salat.grater
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.joda.time.DateTime

/**
  * Created by morgaroth on 29.03.2016.
  */
object Application {

  val dao = new DependencyDAO

  val organisations = Seq(
    ,
    "joda-time",
    "org.joda",
    "io.spray",
    "ch.qos.logback"
  )

  def main(args: Array[String]): Unit = {
    organisations.foreach(x => MVNRepositoryScanner.checkMVNRepository(x, dao))
  }

}

case class Dependency(organisation: String, humanName: String, name: String, scala: String, versions: List[Version] = List.empty, _id: String = null) {
  def artifactName = {
    val versionPart = Option(scala).filter(_ != "java").map(x => s"_$x").getOrElse("")
    s"$name$versionPart"
  }

  lazy val mongoID = s"${organisation}__${name}__$scala"
}

case class Version(name: String, date: DateTime)

class DependencyDAO extends MongoDAOJodaSupport[Dependency](ConfigFactory.parseString("""uri="mongodb://localhost/SbtCommons""""), "SbtCommons") {

  def insertNew(d: Dependency): Option[String] = {
    insert(d.copy(_id = d.mongoID))
  }

  val versionSerializer = grater[Version]

  def updateVersions(dependency: Dependency): List[Version] = {
    try {
      findOneById(dependency.mongoID).map { found =>
        val existing = found.versions.map(x => x.name).toSet
        val maybeNew = dependency.versions.map(x => x.name -> x).toMap
        val completelyNew = maybeNew.filterKeys(x => !existing.contains(x))
        if (completelyNew.nonEmpty) {
          update(MongoDBObject("_id" -> dependency.mongoID),
            MongoDBObject("$push" -> MongoDBObject(
              "versions" -> completelyNew.values.map(versionSerializer.asDBObject)
            ))
          )
        }
        completelyNew.values.toList
      }.getOrElse {
        insertNew(dependency).map(_ => dependency.versions).get
      }
    } catch {
      case a: IllegalArgumentException =>
        println(s"illegal argument for data $dependency ${a.getMessage}")
        Nil
    }
  }
}