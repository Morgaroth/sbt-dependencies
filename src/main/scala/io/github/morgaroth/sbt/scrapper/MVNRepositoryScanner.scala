package io.github.morgaroth.sbt.scrapper

import java.util.Locale

import net.ruippeixotog.scalascraper.model.Element
import org.joda.time.format.DateTimeFormat

import scala.annotation.tailrec

/**
  * Created by morgaroth on 05.04.2016.
  */
object MVNRepositoryScanner extends RepoScanner {
  //  def checkMVNRepository(organisation: String, cache: DependencyDAO) {
  //    val data: List[Dependency] = findPackagesOf(organisation)
  //    val newData = data.flatMap { d =>
  //      val newest = cache.updateVersions(d)
  //      Some(d.copy(versions = newest)).filter(_.versions.nonEmpty)
  //    }
  //    println(s"Fetched ${data.length} artifacts for $organisation, ${newData.length} with new versions")
  //  }

  def findLibrariesOf(organisation: OrganisationCfg): List[Dependency] = {
    def excludedFilter(key: String) = {
      val isExamples = key.toLowerCase.contains("examples")
      val isSpringSource = key.toLowerCase.contains("com.springsource")
      !(isExamples || isSpringSource)
    }

    MVNRepositoryScanner.scanOrganisation(organisation.name)
      .filterKeys(excludedFilter)
      .values.par
      .map(MVNRepositoryScanner.scanVersions).toList
  }

  val serviceUrl = "http://mvnrepository.com/artifact"
  val browser = new net.ruippeixotog.scalascraper.browser.JsoupBrowser


  def organisationPage(organisation: String, pageNum: Int) = s"$serviceUrl/$organisation?p=$pageNum"

  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import net.ruippeixotog.scalascraper.dsl.DSL._

  def scanOrganisation(organisation: String) = {
    def scanPage(pageNum: Int) = {
      val doc = browser.get(organisationPage(organisation, pageNum))
      (doc >> elementList(".im-title")).map(_ ~/~ validator(attr("href")("a")) { x =>
        x.nonEmpty
      } >> (attr("href")("a"), text("a"))).map(_.right.get).flatMap { case (x, humanName) =>
        val data = x.split(Array('_', '/')).toList
        if (data.head == organisation) {
          val libType: String = data.drop(2).headOption.getOrElse("java")
          Some(s"${data(1)}_$libType" -> Dependency(organisation, humanName, data(1), libType))
        } else None
      }.toMap
    }

    @tailrec
    def rec(acc: Map[String, Dependency] = Map.empty, currentNum: Int = 1): Map[String, Dependency] = {
      val newResults: Map[String, Dependency] = scanPage(currentNum)
      if (newResults.nonEmpty && newResults.keySet.diff(acc.keySet).nonEmpty) {
        rec(newResults ++ acc, currentNum + 1)
      } else {
        acc
      }
    }

    rec()
  }

  val dateReader = DateTimeFormat.forPattern("(MMM dd, yyyy)").withLocale(Locale.ENGLISH).withZoneUTC()

  def scanVersions(dependency: Dependency) = {
    val url: String = s"$serviceUrl/${dependency.organisation}/${dependency.artifactName}"
    val document = browser.get(url)
    val versionRow = document >> element(".grid.versions") >> element("tbody") >> elementList("tr")
    val versionElems: List[String] = (versionRow >> elementList(".vbtn") >> attr("href")("a")).flatten.filterNot(_.contains("parent.version"))
    if (versionElems.isEmpty) {
      dependency
    } else {
      val data = versionElems.map { link =>
        val infoTable = browser.get(s"$serviceUrl/${dependency.organisation}/$link") >> element("#maincontent > .grid")
        val elem = (infoTable >> element("tr:has(th:contains(Date)) > td")).innerHtml
        link -> dateReader.parseDateTime(elem)
      }
      val versions = data.map {
        case (link, date) =>
          val name = link.split('/').last
          Version(name, s"$serviceUrl/${dependency.organisation}/$link", date)
      }
      dependency.copy(versions = versions)
    }
  }
}
