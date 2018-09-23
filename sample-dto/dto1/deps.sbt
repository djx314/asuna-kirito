resolvers += Resolver.bintrayRepo(owner = "djx314",repo="releases")
resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "net.scalax" %% "asuna-helper" % "0.0.1-M2"
)