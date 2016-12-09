name := "libraries-scraper"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
  "org.eclipse.aether" % "aether-api" % "1.1.0",
  Salat.`1.9.9`, Morgaroth.UtilsMongo.last,
  "org.scalatra.scalate" %% "scalate-core" % "1.7.1",
  Pathikrit.BetterFiles.last
)