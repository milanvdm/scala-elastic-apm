val lightbendToken = IO.readLines(file(".") / ".credentials").head

ThisBuild / resolvers += "lightbend-commercial-mvn" at
  s"https://repo.lightbend.com/pass/$lightbendToken/commercial-releases"
ThisBuild / resolvers += Resolver.url(
  "lightbend-commercial-ivy",
  url(s"https://repo.lightbend.com/pass/$lightbendToken/commercial-releases")
)(Resolver.ivyStylePatterns)
