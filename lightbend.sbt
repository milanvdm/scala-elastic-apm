val lightbendToken = IO.readLines(file(".") / ".credentials").head

resolvers in ThisBuild += "lightbend-commercial-mvn" at
  s"https://repo.lightbend.com/pass/$lightbendToken/commercial-releases"
resolvers in ThisBuild += Resolver.url("lightbend-commercial-ivy",
  url(s"https://repo.lightbend.com/pass/$lightbendToken/commercial-releases"))(Resolver.ivyStylePatterns)