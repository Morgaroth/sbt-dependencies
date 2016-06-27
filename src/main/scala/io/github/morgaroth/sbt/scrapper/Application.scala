package io.github.morgaroth.sbt.scrapper

import better.files.Cmds._
import better.files.File
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.novus.salat.grater
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.fusesource.scalate.TemplateEngine

import scala.util.Try

/**
  * Created by morgaroth on 29.03.2016.
  */
object Application {

  val dao = new DependencyDAO

  val organisations = List(
    "joda-time",
    //    "org.joda",
    "io.spray"
    //      "ch.qos.logback"
  )

  def main(args: Array[String]): Unit = {
    //    organisations.foreach(x => MVNRepositoryScanner.checkMVNRepository(x, dao))
    doWork(organisations.map(x => OrganisationCfg(x)))
  }

  def escape(s: String) = s

  def doWork(config: List[OrganisationCfg]) = {
    val outputDir: File = pwd / "plugin" / "src" / "libraries"
    Try(mkdirs(outputDir))
    outputDir.clear()
    val engine = new TemplateEngine
    config.map { orgCfg =>
      val libraries = MVNRepositoryScanner.findPackagesOf(orgCfg).map { d =>
        val data = Map("d" -> d)
        val output = engine.layout("/library.ssp", data)
        outputDir / s"${orgCfg.capitalizedName}_${d.capitalizedName}.scala" < output
        d
      }
      outputDir / s"${orgCfg.capitalizedName}.scala" < engine.layout("/organisation.ssp", Map("libs" -> libraries, "org" -> orgCfg))
    }
  }
}

case class ExcludedLibrary(name: String, excludedVersions: Set[String] = Set.empty)

case class OrganisationCfg(name: String, excludedLibraries: Set[ExcludedLibrary] = Set.empty) {
  val capitalizedName = name.split(Array('-', '.')).map(_.capitalize).mkString("")
}

case class Dependency(organisation: String, humanName: String, name: String, scala: String, versions: List[Version] = List.empty, _id: String = null) {
  val name_escaped = humanName.replace(" ", "")

  lazy val capitalizedName = name_escaped
  lazy val capitalizedOrganization = organisation.split(Array('-', '.')).map(_.capitalize).mkString("")

  lazy val fullName = s"${capitalizedOrganization}_$name_escaped"

  def artifactName = {
    val versionPart = Option(scala).filter(_ != "java").map(x => s"_$x").getOrElse("")
    s"$name$versionPart"
  }

  lazy val mongoID = s"${organisation}__${name}__$scala"
  lazy val sortedVersions = versions
  lazy val last = sortedVersions.head
}

case class Version(name: String, link: String, date: String)

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