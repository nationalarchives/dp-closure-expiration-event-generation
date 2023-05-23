package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Encoder
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentCaptor, MockitoSugar}
import uk.gov.nationalarchives.Processor._
import org.scalatest.flatspec.AnyFlatSpec
import uk.gov.nationalarchives.dp.client.ContentClient
import uk.gov.nationalarchives.dp.client.Entities.Entity
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.Lambda.Config

import java.util.UUID
import scala.jdk.CollectionConverters._

class ProcessorSpec extends AnyFlatSpec with MockitoSugar {

  "processDocuments" should "send messages to sqs" in {
    val sqsClient = mock[DASQSClient[IO]]
    val preservicaClient = mock[ContentClient[IO]]
    val entityOneRef = UUID.randomUUID()
    val entityTwoRef = UUID.randomUUID()
    val entityOne = Entity("IO", entityOneRef, None, deleted = false, "io/path")
    val entityTwo = Entity("SO", entityTwoRef, None, deleted = false, "so/path")
    val messageCaptor: ArgumentCaptor[Entity] = ArgumentCaptor.forClass(classOf[Entity])
    def sendMessageResponse(id: String) = SendMessageResponse.builder().messageId(id).build()
    val config = Config("http://preservicaUrl", "secretName", "queueUrl")

    when(preservicaClient.findExpiredClosedDocuments(any[String]))
      .thenReturn(IO(List(entityOne, entityTwo)))
    when(sqsClient.sendMessage(any[String])(messageCaptor.capture())(any[Encoder[Entity]]))
      .thenReturn(IO(sendMessageResponse("1")), IO(sendMessageResponse("2")))

    val response = processDocuments(sqsClient, preservicaClient, config).unsafeRunSync()

    response.size should equal(2)
    response.head.messageId() should equal("1")
    response.last.messageId() should equal("2")

    val messageValues = messageCaptor.getAllValues.asScala
    val first = messageValues.head
    val second = messageValues.last
    first.ref should equal(entityOneRef)
    second.ref should equal(entityTwoRef)

    verify(preservicaClient, times(1)).findExpiredClosedDocuments(any[String])
    verify(sqsClient, times(2)).sendMessage(any[String])(any[Entity])(any[Encoder[Entity]])
  }

  "processDocuments" should "not send to the sqs queue if no entities are returned" in {
    val sqsClient = mock[DASQSClient[IO]]
    val preservicaClient = mock[ContentClient[IO]]
    val sendMessageResponse = SendMessageResponse.builder().messageId("1").build()
    val config = Config("http://preservicaUrl", "secretName", "queueUrl")

    when(preservicaClient.findExpiredClosedDocuments(any[String])).thenReturn(IO(Nil))
    when(sqsClient.sendMessage(any[String])(any[Entity])(any[Encoder[Entity]]))
      .thenReturn(IO(sendMessageResponse))

    val response = processDocuments(sqsClient, preservicaClient, config).unsafeRunSync()

    verify(preservicaClient, times(1)).findExpiredClosedDocuments(any[String])
    verify(sqsClient, times(0)).sendMessage(any[String])(any[Entity])(any[Encoder[Entity]])
    response.isEmpty should be(true)
  }

  "processDocuments" should "send the correct config values to the clients" in {
    val sqsClient = mock[DASQSClient[IO]]
    val preservicaClient = mock[ContentClient[IO]]
    val sendMessageResponse = SendMessageResponse.builder().messageId("1").build()
    val config = Config("http://preservicaUrl", "secretName", "queueUrl")
    val secretNameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val queueUrlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val entity = Entity("IO", UUID.randomUUID(), None, deleted = false, "io/path")

    when(preservicaClient.findExpiredClosedDocuments(secretNameCaptor.capture())).thenReturn(IO(List(entity)))
    when(sqsClient.sendMessage(queueUrlCaptor.capture())(any[Entity])(any[Encoder[Entity]]))
      .thenReturn(IO(sendMessageResponse))

    processDocuments(sqsClient, preservicaClient, config).unsafeRunSync()
    secretNameCaptor.getValue should equal("secretName")
    queueUrlCaptor.getValue should equal("queueUrl")
  }

  "processDocuments" should "return an error if the preservica client returns an error" in {
    val sqsClient = mock[DASQSClient[IO]]
    val preservicaClient = mock[ContentClient[IO]]
    val sendMessageResponse = SendMessageResponse.builder().messageId("1").build()
    val config = Config("http://preservicaUrl", "secretName", "queueUrl")

    when(preservicaClient.findExpiredClosedDocuments(any[String]))
      .thenReturn(IO.raiseError(new RuntimeException("PreservicaError")))
    when(sqsClient.sendMessage(any[String])(any[Entity])(any[Encoder[Entity]])).thenReturn(IO(sendMessageResponse))

    val ex = intercept[RuntimeException](processDocuments(sqsClient, preservicaClient, config).unsafeRunSync())

    ex.getMessage should equal("PreservicaError")
  }

  "processDocuments" should "return an error if the sqs client returns an error" in {
    val sqsClient = mock[DASQSClient[IO]]
    val preservicaClient = mock[ContentClient[IO]]
    val config = Config("http://preservicaUrl", "secretName", "queueUrl")
    val entity = Entity("IO", UUID.randomUUID(), None, deleted = false, "io/path")

    when(preservicaClient.findExpiredClosedDocuments(any[String])).thenReturn(IO(List(entity)))
    when(sqsClient.sendMessage(any[String])(any[Entity])(any[Encoder[Entity]]))
      .thenReturn(IO.raiseError(new RuntimeException("SQSError")))

    val ex = intercept[RuntimeException](processDocuments(sqsClient, preservicaClient, config).unsafeRunSync())

    ex.getMessage should equal("SQSError")
  }
}
