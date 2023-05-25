package uk.gov.nationalarchives

import cats.effect.IO
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.Lambda.Config
import uk.gov.nationalarchives.dp.client.ContentClient
import io.circe.generic.auto._
import cats.implicits._

object Processor {
  def processDocuments(
      sqsClient: DASQSClient[IO],
      contentClient: ContentClient[IO],
      config: Config
  ): IO[List[SendMessageResponse]] = for {
    expiredClosedEntities <- contentClient.findExpiredClosedDocuments(config.secretName)
    res <- expiredClosedEntities.map(entity => sqsClient.sendMessage(config.queueUrl)(entity)).sequence
  } yield res
}
