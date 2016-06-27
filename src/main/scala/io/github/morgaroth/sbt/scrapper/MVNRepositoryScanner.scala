package io.github.morgaroth.sbt.scrapper

import scala.annotation.tailrec

/**
  * Created by morgaroth on 05.04.2016.
  */
object MVNRepositoryScanner {
  //  def checkMVNRepository(organisation: String, cache: DependencyDAO) {
  //    val data: List[Dependency] = findPackagesOf(organisation)
  //    val newData = data.flatMap { d =>
  //      val newest = cache.updateVersions(d)
  //      Some(d.copy(versions = newest)).filter(_.versions.nonEmpty)
  //    }
  //    println(s"Fetched ${data.length} artifacts for $organisation, ${newData.length} with new versions")
  //  }

  def findPackagesOf(organisation: OrganisationCfg): List[Dependency] = {
    def excludedFilter(key: String) = {
      true
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
      } >>(attr("href")("a"), text("a"))).map(_.right.get).flatMap { case (x, humanName) =>
        val data = x.split(Array('_', '/')).toList
        if (data.head == organisation) {
          val libType: String = data.drop(2).headOption.getOrElse("java")
          Some(s"${data(1)}_$libType" -> Dependency(organisation, humanName, data(1), libType))
        } else None
      }.toMap.filterNot(_._1.contains("sample"))
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

  def scanVersions(dependency: Dependency) = {
    val url: String = s"$serviceUrl/${dependency.organisation}/${dependency.artifactName}"
    val document = browser.get(url)
    val versions = (document >> element(".grid.versions") >> element("tbody") >> elementList("tr") >>
      (element(".vbtn.release") >>(attr("href")("a"), text("a")), elementList("td") map (_.last.innerHtml))
      ).map {
      case ((link, name), date) =>
        Version(name, s"$serviceUrl/${dependency.organisation}/$link", date.filterNot(x => x == '(' || x == ')'))
    }
    dependency.copy(versions = versions)
  }
}
