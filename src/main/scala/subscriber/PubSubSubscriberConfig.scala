package subscriber

import scala.concurrent.duration._

case class PubSubSubscriberConfig(
    maxOutstandingElementCount: Int = 10000,
    maxOutstandingRequestBytes: Int = 100 * 1024 * 1024, //100MB
    maxAckExtensionPeriod: FiniteDuration = 10.seconds,
    awaitTerminatePeriod: FiniteDuration = 5.seconds
)
