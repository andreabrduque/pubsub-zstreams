import com.google.pubsub.v1.PubsubMessage
import zio.{ IO, Task }

package object subscriber {

  final case class RawMessage(
      value: PubsubMessage,
      ack: Task[Unit],
      nack: Task[Unit]
  )

  final case class DecodedMessage[A](
      value: IO[DecodingFailure, A],
      ack: Task[Unit],
      nack: Task[Unit]
  )

  final case class DecodingFailure(cause: Throwable) extends Throwable("Decoding subscription message failed", cause)
  final case class PubSubError(cause: Throwable) extends Throwable("PubSub subscription failed", cause)

}
