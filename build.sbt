scalaVersion := "2.12.6"

scalacOptions ++= Seq("-feature", "-deprecation", "-Ywarn-unused-import")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.6" cross CrossVersion.binary)

lazy val dto = (project in file("./sample-dto"))

lazy val kirito = (project in file(".")).dependsOn(dto).aggregate(dto)