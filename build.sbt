val libraryName = "webdriver-factory"

val scala2_12 = "2.12.17"
val scala2_13 = "2.13.10"

val compileDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"            % "3.9.5",
  "org.seleniumhq.selenium"     % "selenium-java"            % "4.9.1",
  "org.seleniumhq.selenium"     % "selenium-http-jdk-client" % "4.9.1",
  "org.slf4j"                   % "slf4j-simple"             % "2.0.7"
)

val testDependencies = Seq(
  "com.vladsch.flexmark" % "flexmark-all" % "0.62.2"  % Test,
  "org.mockito"          % "mockito-all"  % "1.10.19" % Test,
  "org.scalatest"       %% "scalatest"    % "3.2.16"  % Test
)

lazy val library = Project(libraryName, file("."))
  .settings(
    majorVersion := 0,
    scalaVersion := scala2_12,
    crossScalaVersions := Seq(scala2_12, scala2_13),
    libraryDependencies ++= compileDependencies ++ testDependencies,
    isPublicArtefact := true
  )
