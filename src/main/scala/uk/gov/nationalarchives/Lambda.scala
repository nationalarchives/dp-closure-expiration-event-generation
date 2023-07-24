package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxOptionId
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import fs2.compression.Compression
import fs2.interop.reactivestreams._
import fs2.io._
import fs2.{Chunk, Pipe, Stream, text}
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

import java.io.{BufferedInputStream, InputStream, OutputStream}
import java.time.LocalDate
import java.util.UUID
import upickle.default._

import java.time.format.DateTimeFormatter


class Lambda extends RequestStreamHandler {
  implicit val jsonReader: Reader[TREJson] = macroR[TREJson]
  implicit val parameterReader: Reader[Parameters] = macroR
  implicit val parserReader: Reader[Parser] = macroR
  implicit val dateReader: Reader[LocalDate] = readwriter[String].bimap[LocalDate](
    date => date.format(DateTimeFormatter.ISO_LOCAL_DATE),
    dateString => LocalDate.parse(dateString)
  )

  val s3: DAS3Client[IO] = DAS3Client[IO]()
  private val bucket = "dp-sam-test-bucket"
  val consignmentRef = "TDR-2023-RMW"
  val key = s"TRE-$consignmentRef.tar.gz"
  case class TREJson(parameters: Parameters)
  case class Parser(uri: String, court: String, cite: String, date: LocalDate, name: String, attachments: List[String] = Nil, `error-messages`: List[String] = Nil)
  case class Payload(fileName: String, sha256: String)
  case class TREParams(payload: Payload)
  case class Parameters(PARSER: Parser, TRE: TREParams)
  val chunkSize: Int = 1024 * 64
  def copyFilesToBucket(): IO[Map[String, UUID]] = {
    s3.download(bucket, key)
      .flatMap(
        _.toStreamBuffered[IO](1024 * 1024)
          .flatMap(bf => Stream.chunk(Chunk.byteBuffer(bf)))
          .through(Compression[IO].gunzip())
          .flatMap(_.content)
          .through(unarchiveToS3)
          .compile
          .toList
      ).map(_.toMap)
  }

  private def parseJson(str: Stream[IO, Byte]): Stream[IO, TREJson] = {
    str
      .through(text.utf8.decode)
      .through(text.lines)
      .map(jsonString => read[TREJson](jsonString))

  }

  private def unarchiveToS3: Pipe[IO, Byte, (String, UUID)] = { stream =>
    stream
      .through(toInputStream[IO])
      .map(new BufferedInputStream(_, chunkSize))
      .flatMap(is => Stream.resource(Resource.fromAutoCloseable(IO.blocking(new TarArchiveInputStream(is)))))
      .flatMap { tarInputStream =>
        def readEntriesAndUpload: Stream[IO, (String, UUID)] = {
          Stream
            .eval(IO.blocking(Option(tarInputStream.getNextTarEntry)))
            .flatMap(Stream.fromOption[IO](_))
            .flatMap { tarEntry =>
              Stream
                .eval(IO(readInputStream(IO.pure[InputStream](tarInputStream), chunkSize, closeAfterUse = false)))
                .flatMap(stream => {
                  val id = UUID.randomUUID()
                  Stream.eval[IO, (String, UUID)](
                    stream.chunks
                      .map(_.toByteBuffer)
                      .toUnicastPublisher
                      .use(s3.upload(bucket, id.toString, tarEntry.getSize, _))
                      .map(_ => tarEntry.getName -> id)
                  )
                }) ++
                readEntriesAndUpload
            }
        }
        readEntriesAndUpload
      }
  }

  private def processFiles(map: Map[String, UUID]): IO[TREJson] = {
    for {
      metadataFile <- IO.fromOption(map.get(s"$consignmentRef/TRE-$consignmentRef-metadata.json"))(new RuntimeException(s"Cannot find metadata for $consignmentRef"))
      s3Stream <- s3.download(bucket, metadataFile.toString)
      contentString <- s3Stream.toStreamBuffered[IO](1024 * 64)
          .flatMap(bf => Stream.chunk(Chunk.byteBuffer(bf)))
          .through(parseJson).compile.toList
      parsedJson <- IO.fromOption(contentString.headOption)(new RuntimeException("Error parsing json"))
    } yield parsedJson
  }

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    for {
      fileToId <- copyFilesToBucket()
      json <- processFiles(fileToId)
      _ <- IO.println(json)
      fileName <- IO.fromOption(fileToId.get(json.parameters.TRE.payload.fileName).map(_.toString))(new RuntimeException("Document not found"))

    } yield json
  }.unsafeRunSync()

}

object Lambda {
  case class Config(preservicaUrl: String, secretName: String, queueUrl: String)
}
