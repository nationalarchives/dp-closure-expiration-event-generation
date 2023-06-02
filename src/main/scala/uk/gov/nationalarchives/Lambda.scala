package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import uk.gov.nationalarchives.Lambda.Config
import uk.gov.nationalarchives.ExpiredEntitiesProcessor.findExpiredEntitiesAndSendToSqs
import uk.gov.nationalarchives.dp.client.fs2.Fs2Client.contentClient

import java.io.{InputStream, OutputStream}

class Lambda extends RequestStreamHandler {

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val sqsClient = DASQSClient[IO]()
    val result = for {
      config <- ConfigSource.default.loadF[IO, Config]()
      contentClient <- contentClient(config.preservicaUrl)
      res <- findExpiredEntitiesAndSendToSqs(sqsClient, contentClient, config)
    } yield res
    result.unsafeRunSync()
  }
}
object Lambda {
  case class Config(preservicaUrl: String, secretName: String, queueUrl: String)
}
