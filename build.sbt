name := "libraries-scraper"

version := "1.0"

scalaVersion := "2.11.8"

//enableSbtPlugins(SbtCommons)

libraryDependencies ++= Seq(
  "net.ruippeixotog" %% "scala-scraper" % "0.1.2",
  "org.eclipse.aether" % "aether-api" % "1.1.0",
  Salat.`1.9.9`, Morgaroth.UtilsMongo.last,
  "org.scalatra.scalate" %% "scalate-core" % "1.7.1"
)