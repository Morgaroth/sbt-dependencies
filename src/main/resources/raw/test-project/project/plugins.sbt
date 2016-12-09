// build root project
lazy val root = Project("test", file(".")) dependsOn developedPlugin

// depends on the developed plugin
lazy val developedPlugin = file("..").getAbsoluteFile.toURI