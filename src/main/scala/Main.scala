import com.google.pubsub.v1.PubsubMessage
import subscriber.{ DecodingFailure, MessageDecoder, PubSubSubscriber, PubSubSubscriberConfig }
import zio._
import zio.blocking.Blocking
import zio.clock._
import zio.console._
import zio.duration._

object MessagesAsString {

  implicit val decoder = (message: PubsubMessage) => message.getData.toString("UTF-8")
  val stringDecoder: MessageDecoder[String] = MessageDecoder[String]

  val config = PubSubSubscriberConfig()

  val subscriptionStream =
    PubSubSubscriber.subscribe("hf-data-staging", "my-subscription", config, stringDecoder)

  val program: ZIO[Console with Blocking with Clock, Throwable, Option[Unit]] = subscriptionStream.use { stream =>
    stream.mapM { value =>
      for {
        decoded <- value.value
        print <- putStrLn(s"decoded: ${decoded}") *> value.ack
      } yield print
    }.runDrain
      .timeout(30.seconds)
  }

}

object MessagesAsAttributes {
  implicit val decoder = (message: PubsubMessage) => {
    val messageAsMap = message.getAttributesMap
    val att1 = messageAsMap.get("att1").head.toString
    val att2 = messageAsMap.get("att2")
    MessageAttributes(att1, att2)
  }
  val attributesDecoder: MessageDecoder[MessageAttributes] = MessageDecoder[MessageAttributes]

  val config = PubSubSubscriberConfig()
  val subscriptionStream =
    PubSubSubscriber.subscribe("hf-data-staging", "my-subscription", config, attributesDecoder)
  val program: ZIO[Console with Blocking with Clock, Throwable, Option[Unit]] = subscriptionStream.use { stream =>
    stream.mapM { value =>
      val action: ZIO[Console, Throwable, Unit] = for {
        decoded <- value.value
        print <- putStrLn(s"decoded: ${decoded}") *> value.ack
      } yield print

      action.catchSome {
        case _: DecodingFailure =>
          putStrLn("decoding failed") *> value.nack
      }
    }.runDrain
      .timeout(30.seconds)

  }

  //this decoder will cause a decoder failure
  case class MessageAttributes(att1: String, att2: String)

}

object Main extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    MessagesAsString.program.exitCode

}
