package io.github.morgaroth.sbt.scrapper

/**
  * Created by morgaroth on 30.12.16.
  */
trait RepoScanner {

  def findLibrariesOf(organisation: OrganisationCfg): List[Dependency]
}
