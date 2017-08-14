package xenomorph

import kafka.message.MessageAndMetadata

case class DecodedEvent[K, M](messageAndMetadata: MessageAndMetadata[K, M]) {
  val key: K     = messageAndMetadata.key()
  val message: M = messageAndMetadata.message()
}
