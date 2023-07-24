package de.lhns.fs2.compress

import cats.effect.{Async, Deferred, Resource}
import cats.syntax.functor._
import fs2.io._
import fs2.{Pipe, Stream}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}

import java.io.{BufferedInputStream, InputStream}

class TarUnarchiver[F[_] : Async](chunkSize: Int) {

  def unarchive: Pipe[F, Byte, (TarArchiveEntry, Stream[F, Byte])] = { stream =>
    stream
      .through(toInputStream[F]).map(new BufferedInputStream(_, chunkSize))
      .flatMap { inputStream =>
        Stream.resource(Resource.make(
          Async[F].blocking(new TarArchiveInputStream(inputStream))
        )(s =>
          Async[F].blocking(s.close())
        ))
      }
      .flatMap { tarInputStream =>
        def readEntries: Stream[F, (TarArchiveEntry, Stream[F, Byte])] =
          Stream.eval(Async[F].blocking(Option(tarInputStream.getNextTarEntry)))
            .flatMap(Stream.fromOption[F](_))
            .flatMap { tarEntry =>
              Stream.eval(Deferred[F, Unit])
                .flatMap { deferred =>
                  Stream.emit(
                    readInputStream(Async[F].pure[InputStream](tarInputStream), chunkSize, closeAfterUse = false) ++
                      Stream.exec(deferred.complete(()).void)
                  ) ++
                    Stream.exec(deferred.get)
                }
                .map(stream => (tarEntry, stream)) ++
                readEntries
            }
        readEntries
      }
  }
}
