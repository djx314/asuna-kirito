scalaVersion := "2.12.6"

lazy val dto1 = (project in file("./dto1"))

lazy val dto = (project in file(".")).dependsOn(dto1).aggregate(dto1)