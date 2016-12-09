package io.github.morgaroth.sbt.commons

import scala.util.Try

/**
 * Created by mateusz on 19.09.15.
 */
trait CheckNewerVersion {
  def thisVersion: (Int, Int)

  def needUpgrade(fromInternet: (Int, Int)) = {
    if (thisVersion == fromInternet) {
      false
    } else if (thisVersion._1 < fromInternet._1) {
      // major is newer
      true
    } else if (thisVersion._1 == fromInternet._1 && thisVersion._2 < fromInternet._2) {
      // minor is newer
      true
    } else {
      // local is newer than internet?
      false
    }
  }


  def getInternetVersion = {
    val b = "https://oss.sonatype.org/content/repositories/releases/io/github/morgaroth/sbt-commons_2.10_0.13/"
    val r2 = """<td><a href=.*>(\d+\.\d+)/</a></td>""".r
    val r3 = """<td><a href=.*>(\d+\.\d+\.\d+)/</a></td>""".r
    Try {
      val data = Http(b).asString.body
      val versions = r2.findAllMatchIn(data).toList.map(_.group(1)).map(_.split( """\.""").toList.map(_.toInt))
      val majors = versions.groupBy(_.head).mapValues(_.map(_.tail))
      val major = majors.keys.max
      val minors = majors(major).groupBy(_.head).mapValues(_.map(_.tail))
      val minor = minors.keys.max
      val allempty = minors.values.forall(_.forall(_.isEmpty))
      val allnonempty = minors.values.forall(_.forall(_.nonEmpty))
      if (allempty && !allnonempty) {
        // all are in format major.minor x.y
        Some(major -> minor)
      } else {
        None
      }
    }.getOrElse(None)
  }
}
