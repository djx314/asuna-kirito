scalaVersion := "2.12.6"

lazy val dto1 = (project in file("./dto1"))
lazy val dto2 = (project in file("./dto2"))

lazy val dto = (project in file(".")).dependsOn(dto1).aggregate(dto1).dependsOn(dto2).aggregate(dto2)