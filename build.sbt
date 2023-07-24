import Dependencies._
import uk.gov.nationalarchives.sbt.Log4j2MergePlugin.log4j2MergeStrategy

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "dp-closure-expiration-event-generation",
    logLevel := Level.Info,
    libraryDependencies ++= Seq(
      sqsClient,
      lambdaCore,
      pureConfig,
      pureConfigCats,
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.5.1",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.20.0",
      "org.apache.logging.log4j" % "log4j-core" % "2.20.0",
      "org.apache.logging.log4j" % "log4j-api" % "2.20.0",
      "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.20.0",
      "co.fs2" %% "fs2-reactive-streams" % "3.7.0",
      "org.apache.commons" % "commons-compress" % "1.23.0",
      "software.amazon.nio.s3" % "aws-java-nio-spi-for-s3" % "1.2.1",
      "co.fs2" %% "fs2-reactive-streams" % "3.7.0",
      preservicaClientFs2,
      scalaTest % Test,
      mockito % Test
    )
  )

(assembly / assemblyMergeStrategy) := {
  case PathList(ps @ _*) if ps.last == "Log4j2Plugins.dat" => log4j2MergeStrategy
  case _                                                   => MergeStrategy.first
}

(assembly / assemblyJarName) := "closure-event-generation.jar"
