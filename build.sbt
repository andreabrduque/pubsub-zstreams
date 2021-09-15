ThisBuild / scalaVersion := "2.13.6"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "pubsub-zstreams",
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %% "zio-magic" % "0.3.8",
      "dev.zio" %% "zio" % "1.0.11",
      "dev.zio" %% "zio-test" % "1.0.11" % Test,
      "com.google.cloud" % "google-cloud-pubsub" % "1.104.1"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
