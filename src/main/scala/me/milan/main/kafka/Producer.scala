package me.milan.main.kafka

import java.util.Properties

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
          "payment-id",
          "payment-id"
        )
      )
      .get()

    Thread.sleep(10000)
  }

}
