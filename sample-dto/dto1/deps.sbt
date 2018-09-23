resolvers += Resolver.bintrayRepo(owner = "djx314",repo="releases")
resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "net.scalax" %% "asuna-mapper" % "0.0.1-M3"
)