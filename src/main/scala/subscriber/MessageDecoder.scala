package subscriber

import com.google.pubsub.v1.PubsubMessage
import zio.{IO, ZIO}

trait MessageDecoder[A] {

  def decode(message: PubsubMessage): IO[DecodingFailure, A]

}

object MessageDecoder {

  def apply[A](implicit decoder: PubsubMessage => A): MessageDecoder[A] =
    (message: PubsubMessage) => ZIO.effect(decoder(message)).mapError(DecodingFailure)
}
