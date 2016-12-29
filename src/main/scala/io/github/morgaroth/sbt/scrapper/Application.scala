package io.github.morgaroth.sbt.scrapper

import better.files.Cmds._
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global.ctx
import com.novus.salat.grater
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.utils.mongodb.salat.MongoDAOJodaSupport
import org.fusesource.scalate.TemplateEngine
import org.joda.time.DateTime

import scala.util.Try

/**
  * Created by morgaroth on 29.03.2016.
  */
object Application {

  val dao = new DependencyDAO

  val organisations = List(
    "joda-time",
    "org.joda",
    "io.spray",
    "ch.qos.logback"
  )

  def main(args: Array[String]): Unit = {
    //    organisations.foreach(x => MVNRepositoryScanner.checkMVNRepository(x, dao))
    doWork(organisations.map(x => OrganisationCfg(x)))
  }

  val rootPackage = "io.github.morgaroth.sbt.commons"
  val rootPwd = pwd / "plugin"
  val sourcesRoot = rootPackage.split('.').foldLeft(rootPwd / "src" / "main" / "scala") { case (f, p) => f / p }

  def escape(s: String) = s

  def doWork(config: List[OrganisationCfg]) = {
    Try(rootPwd.clear())
    Try(mkdirs(sourcesRoot))
    Try(mkdirs(sourcesRoot / "libraries"))
    Try(mkdirs(sourcesRoot / "organisations"))
    pwd / "src" / "main" / "resources" / "raw" copyTo rootPwd
    val engine = new TemplateEngine
    config.map { orgCfg =>
      val libraries: List[Dependency] = MVNRepositoryScanner.findLibrariesOf(orgCfg).groupBy(_.capitalizedName).mapValues { theSameNameDeps =>
        val (scalaLibs, javaLibs) = theSameNameDeps.partition(_.scala != "java")
        if (scalaLibs.nonEmpty && javaLibs.isEmpty) {
          val allVersions = scalaLibs.flatMap(x => x.versions.map(_.copy(scala = x.scala)))
          scalaLibs.head.copy(versions = allVersions, scala = "scala")
        } else if (javaLibs.size == 1) {
          javaLibs.head
        } else {
          println(s"WTF $javaLibs")
          val allVersions = javaLibs.flatMap(_.versions)
          javaLibs.head.copy(versions = allVersions)
        }
      }.values.toList
      //            val libraries: List[Dependency] = MVNRepositoryScanner.findPackagesOf(orgCfg)
      libraries.foreach { d =>
        val versions: List[(String, List[Version])] = if (d.scala == "scala") {
          d.versions.groupBy(_.name).mapValues(_.sortBy(_.date.getMillis)).toList
        } else d.sortedVersions.map(x => x.name -> List(x))
        val output = engine.layout("/library.ssp", Map("d" -> d, "v" -> versions, "p" -> rootPackage))
        sourcesRoot / "libraries" / s"${orgCfg.capitalizedName}_${d.capitalizedName}.scala" < output
      }
      sourcesRoot / "organisations" / s"${orgCfg.capitalizedName}.scala" < engine.layout("/organisation.ssp", Map("libs" -> libraries, "org" -> orgCfg, "p" -> rootPackage))
    }
    sourcesRoot / "Libraries.scala" < engine.layout("/libraries.ssp", Map("organisations" -> config, "p" -> rootPackage))
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
  lazy val sortedVersions = versions.sortBy(_.date.getMillis).reverse
  lazy val last = sortedVersions.head
}

case class Version(name: String, link: String, date: DateTime, scala: String = "")

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