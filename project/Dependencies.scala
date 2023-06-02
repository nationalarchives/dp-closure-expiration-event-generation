import sbt._

object Dependencies {
  lazy val lambdaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.2"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val preservicaClientFs2 = "uk.gov.nationalarchives" %% "preservica-client-fs2" % "0.0.5"
  lazy val pureConfigCats = "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.4"
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.17.4"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
  lazy val sqsClient = "uk.gov.nationalarchives" %% "da-sqs-client" % "0.1.3"
}
