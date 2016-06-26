package io.github.morgaroth.sbt.scrapper

import net.ruippeixotog.scalascraper.browser.Browser
import org.joda.time.DateTime

import scala.annotation.tailrec

/**
  * Created by morgaroth on 05.04.2016.
  */
object MVNRepositoryScanner {
  def checkMVNRepository(organisation: String, cache: DependencyDAO) {
    val data: List[Dependency] = findPackagesOf(organisation)
    val newData = data.flatMap { d =>
      val newest = cache.updateVersions(d)
      Some(d.copy(versions = newest)).filter(_.versions.nonEmpty)
    }
    println(s"Fetched ${data.length} artifacts for $organisation, ${newData.length} with new versions")
  }

  def findPackagesOf(organisation: String): List[Dependency] = {
    MVNRepositoryScanner.scanOrganisation(organisation).values.par.map(MVNRepositoryScanner.scanVersions).toList
  }

  val serviceUrl = "http://mvnrepository.com/artifact"
  val browser = new Browser

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
    val versions =
      (browser.get(s"$serviceUrl/${dependency.organisation}/${dependency.artifactName}") >> elementList(".release") >>(attr("href")("a"), text("a"))).map {
        case (link, name) => Version(name, DateTime.now())
      }
    dependency.copy(versions = versions)
  }
}
