sbtPlugin := true

name := """Sbt Commons"""

organization := """io.github.morgaroth"""

val major = 0
val minor = 18

version := s"$major.$minor"

pomExtra := githubPom(name.value, "Mateusz Jaje", "Morgaroth")

publishTo := publishRepoForVersion(version.value)

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey]("minor" -> minor, "major" -> major)

buildInfoPackage := "io.github.morgaroth.sbt.commons"