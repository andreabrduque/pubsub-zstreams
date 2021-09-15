package subscriber

import com.google.api.core.ApiService.{ Listener, State }
import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.core.InstantiatingExecutorProvider
import com.google.cloud.pubsub.v1.{ AckReplyConsumer, MessageReceiver, Subscriber => GSubscriber }
import com.google.pubsub.v1.{ ProjectSubscriptionName, PubsubMessage }
import org.threeten.bp.Duration
import zio.blocking._
import zio.stream.ZStream
import zio.{ IO, Queue, ZIO, ZManaged }

import java.util.concurrent.TimeUnit

object PubSubSubscriber {

  def subscribe[A](
      projectId: String,
      subscription: String,
      config: PubSubSubscriberConfig,
      decoder: MessageDecoder[A]
  ): ZManaged[Blocking, PubSubError, ZStream[Any, Throwable, DecodedMessage[A]]] =
    for {
      queue <-
        Queue
          .bounded[IO[Throwable, RawMessage]](config.maxOutstandingElementCount)
          .toManaged(_.shutdown)

      runtime <- ZIO.runtime[Any].toManaged_
      _ <- createSubscriber(
        projectId,
        subscription,
        config,
        value =>
          runtime.unsafeRunAsync_(
            queue.offer(
              value
            )
          )
      )
    } yield ZStream.repeatEffect(takeNextAndDecode(queue, decoder))

  private def createSubscriber(
      projectId: String,
      subscription: String,
      config: PubSubSubscriberConfig,
      callback: IO[PubSubError, RawMessage] => Unit
  ): ZManaged[Blocking, PubSubError, GSubscriber] = {

    //uses the default executor provider
    //default number of threads is number of CPUs and opens only one stream parallel pull count (1)
    val executorProvider = InstantiatingExecutorProvider
      .newBuilder()
      .build()

    val flowControlSettings = FlowControlSettings
      .newBuilder()
      .setMaxOutstandingElementCount(config.maxOutstandingElementCount)
      .setMaxOutstandingRequestBytes(config.maxOutstandingRequestBytes)
      .build()

    val name = ProjectSubscriptionName.of(projectId, subscription)
    val subscriber =
      GSubscriber
        .newBuilder(name, new PubSubMessageReceiver(callback))
        .setParallelPullCount(1)
        .setFlowControlSettings(flowControlSettings)
        .setExecutorProvider(executorProvider)
        .setMaxAckExtensionPeriod(Duration.ofMillis(config.maxAckExtensionPeriod.toMillis))
        .build()

    subscriber.addListener(
      new Listener {
        override def failed(
            state: State,
            t: Throwable
        ): Unit = callback(ZIO.fail(PubSubError(t)))
      },
      executorProvider.getExecutor()
    )

    effectBlocking(subscriber.startAsync().awaitRunning())
      .mapBoth(PubSubError, _ => subscriber)
      .toManaged(s =>
        effectBlockingInterrupt(
          s.stopAsync.awaitTerminated(config.awaitTerminatePeriod.toSeconds, TimeUnit.SECONDS)
        ).ignore
      )

  }

  def takeNextAndDecode[A](
      queue: Queue[IO[Throwable, RawMessage]],
      decoder: MessageDecoder[A]
  ): ZIO[Any, Throwable, DecodedMessage[A]] =
    for {
      message <- queue.take
      value: RawMessage <- message
    } yield DecodedMessage(decoder.decode(value.value), value.ack, value.nack)

  class PubSubMessageReceiver(
      callback: IO[PubSubError, RawMessage] => Unit
  ) extends MessageReceiver {

    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit =
      callback(
        ZIO.succeed(RawMessage(message, IO.effect(consumer.ack()), IO.effect(consumer.nack())))
      )

  }

}
