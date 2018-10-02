package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties
import java.util.Random
import org.apache.avro.generic.GenericData
import org.apache.kafka.clients.producer.RecordMetadata

class DummyJoarkProducer {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val journalpostProducer =
                    kafkaProducer()

            while (true) {
                val id = Random().nextInt(2000).toString()

                val schemaSource = """

                    {"namespace": "example.jp",
                     "type": "record",
                     "name": "JournalPost",
                     "fields": [
                         {"name": "journalpostId", "type": "long"}
                     ]
                    }


                """.trimIndent()

                val avroSchema = Schema.Parser().parse(schemaSource)
                val joarkJournalpost = GenericData.Record(avroSchema)
                joarkJournalpost.put("journalpostId", id.toLong())

                System.out.println("Creating InngåendeJournalpost $id to topic $JOARK_EVENTS")
                val record: RecordMetadata = journalpostProducer.send(
                        ProducerRecord(JOARK_EVENTS.name, id, joarkJournalpost)
                ).get()
                println("Produced -> ${record.topic()}  to offset ${record.offset()}")
                Thread.sleep(5000L)
            }
        }

        private fun kafkaProducer(): KafkaProducer<String, GenericRecord> {
            val bootstrapServersConfig = System.getenv("BOOTSTRAP_SERVERS_CONFIG") ?: "localhost:9092"
            val applicationIdConfig = "joark-dummy-producer"
            var props = Properties().apply {
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersConfig)
                put(StreamsConfig.CLIENT_ID_CONFIG, applicationIdConfig)
                put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            }

            return KafkaProducer(
                    props,
                    JOARK_EVENTS.keySerde.serializer(),
                    JOARK_EVENTS.valueSerde.serializer()
            )
        }
    }
}