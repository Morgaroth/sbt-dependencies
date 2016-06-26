package io.github.morgaroth.sbt.scrapper

/**
  * Created by morgaroth on 05.04.2016.
  */
class AkkaTemplater(cache: DependencyDAO) {

  def handle() = {
    val updated = MVNRepositoryScanner.findPackagesOf("com.typesafe.akka")

  }

}


object AkkaTemplater
