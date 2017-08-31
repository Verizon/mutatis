package xenomorph

import java.util.concurrent.atomic.AtomicBoolean

import kafka.consumer.KafkaStream
import kafka.message.MessageAndMetadata

private case class Init[K, V](
    streams: Seq[KafkaStream[K, V]],
    commitOffset: (MessageAndMetadata[K, V]) => Unit,
    shutdown: ()                             => Unit,
    stopCommitter: AtomicBoolean)

