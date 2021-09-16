import zio.{ IO, Task }

package object subscriber {
  abstract class SubscriptionError(msg: String, cause: Throwable) extends Throwable(msg, cause)

  final case class DecodedMessage[A](
      value: IO[DecodingFailure, A],
      ack: Task[Unit],
      nack: Task[Unit]
  )

  final case class DecodingFailure(cause: Throwable) extends SubscriptionError("Decoding message failed", cause)
  final case class PubSubError(cause: Throwable) extends SubscriptionError("PubSub subscription failed", cause)

}
