package subscriber

import com.google.api.core.ApiService.{ Listener, State }
import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.core.InstantiatingExecutorProvider
import com.google.cloud.pubsub.v1.{ AckReplyConsumer, MessageReceiver, Subscriber => GSubscriber }
import com.google.pubsub.v1.{ ProjectSubscriptionName, PubsubMessage }
import org.threeten.bp.Duration
import zio._
import zio.blocking._
import zio.stream.ZStream

import java.util.concurrent.TimeUnit

object PubSubSubscriber {

  def subscribe[A](
      projectId: String,
      subscription: String,
      config: PubSubSubscriberConfig,
      decoder: MessageDecoder[A]
  ): ZStream[Blocking, SubscriptionError, DecodedMessage[A]] =
    ZStream.effectAsyncManaged[Blocking, SubscriptionError, DecodedMessage[A]] { callback =>
      val receiver: MessageReceiver =
        (message: PubsubMessage, consumer: AckReplyConsumer) => {
          callback(
            //currently decoding failures will not fail the stream, but this can be modified if needed
            ZIO.succeed(
              Chunk(DecodedMessage(decoder.decode(message), IO.effect(consumer.ack), IO.effect(consumer.nack())))
            )
          )
        }

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
          .newBuilder(name, receiver)
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
          ): Unit = callback(ZIO.fail(Some(PubSubError(t))))
        },
        executorProvider.getExecutor()
      )

      effectBlocking(subscriber.startAsync().awaitRunning())
        .mapBoth(PubSubError, _ => subscriber)
        .toManaged(s =>
          effectBlocking(
            s.stopAsync.awaitTerminated(config.awaitTerminatePeriod.toSeconds, TimeUnit.SECONDS)
          ).ignore
        )

    }

}
