package me.milan.main.akka

import java.util.{ Properties, UUID }

import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord }

object Producer extends App {

  val producerProps = new Properties()
  producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  producerProps.put(
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringSerializer"
  )
  producerProps.put(
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringSerializer"
  )

  val producer = new KafkaProducer[String, String](producerProps)

  while (true) {
    producer
      .send(
        new ProducerRecord(
          "payments",
          UUID.randomUUID().toString,
          "payment-id"
        )
      )
      .get()
  }

}
