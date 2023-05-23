import Dependencies._

ThisBuild / scalaVersion     := "2.13.10"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "dp-closure-expiration-event-generation",
    libraryDependencies ++= Seq(
      sqsClient,
      lambdaCore,
      pureConfig,
      pureConfigCats,
      preservicaClientFs2,
      scalaTest % Test,
      mockito % Test
    )]
  )

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "closure-event-generation.jar"

scalacOptions ++= Seq("-Wunused:imports", "-Werror")

