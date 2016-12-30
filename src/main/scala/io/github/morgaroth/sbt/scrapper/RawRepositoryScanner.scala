package io.github.morgaroth.sbt.scrapper

import java.util.Locale

import net.ruippeixotog.scalascraper.model.Element
import org.joda.time.format.DateTimeFormat

import scala.annotation.tailrec

/**
  * Created by morgaroth on 30.12.2016.
  */
class RawRepositoryScanner(rootUrl: String) extends RepoScanner {

  def findLibrariesOf(organisation: OrganisationCfg): List[Dependency] = {
    def excludedFilter(key: String) = {
      val isExamples = key.toLowerCase.contains("examples")
      val isSpringSource = key.toLowerCase.contains("com.springsource")
      !(isExamples || isSpringSource)
    }

    scanOrganisation(organisation.name)
      .filterKeys(excludedFilter)
      .values.par
      .map(scanVersions).toList
  }

  val browser = new net.ruippeixotog.scalascraper.browser.JsoupBrowser

  def organisationPage(organisation: String) = s"$rootUrl/${organisation.split('.').mkString("/")}"

  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import net.ruippeixotog.scalascraper.dsl.DSL._

  def scanOrganisation(organisation: String): Map[String, Dependency] = {
    val doc = browser.get(organisationPage(organisation))
    (doc >> elementList("pre > a")).map(_ ~/~ validator(attr("href")("a")) { x =>
      x.split('/').count(_.nonEmpty) > 1
    } >> attr("href")("a")).collect { case Right(v) => v }.flatMap { case x =>
      val data = x.split('/').filter(_.nonEmpty).toList
      val artifact = data.last.split('_').toList
      println(organisation, artifact)
      if (data.init.mkString(".") == organisation && artifact.length <= 2) {
        val libType: String = artifact.tail.headOption.getOrElse("java")
        val humanName = artifact.head.split('-').map(_.capitalize).mkString(" ")
        Some(s"${artifact.head}_$libType" -> Dependency(organisation, humanName, artifact.head, libType))
      } else None
    }.toMap
  }

  val dateReader = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ENGLISH).withZoneUTC()

  def scanVersions(dependency: Dependency) = {
    val convertedOrg = dependency.organisation.split('.').mkString("/")
    val url: String = s"$rootUrl/$convertedOrg/${dependency.artifactName}"
    val document = browser.get(url)
    val versionRows = (document >> element("pre")).text.split("\n").toList.map(_.split("/\\s+").toList).filter { row =>
      row.size == 2
    }
    if (versionRows.isEmpty) {
      dependency
    } else {
      val data = versionRows.map { info =>
        val ver = info.head
        val date = info(1)
        ver -> dateReader.parseDateTime(date)
      }
      val versions = data.map {
        case (verName, date) =>
          Version(verName, s"$rootUrl/$convertedOrg/${dependency.artifactName}/$verName/", date)
      }
      dependency.copy(versions = versions)
    }
  }
}
