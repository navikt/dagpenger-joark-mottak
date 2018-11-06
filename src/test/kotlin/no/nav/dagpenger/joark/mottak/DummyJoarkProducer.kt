package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import no.nav.dagpenger.streams.configureGenericAvroSerde
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties
import java.util.Random
import java.util.concurrent.TimeUnit

class DummyJoarkProducer(journalpostProducerProperties: Properties) {

    private val LOGGER = KotlinLogging.logger {}
    private val journalpostProducer = KafkaProducer(
        journalpostProducerProperties,
        JOARK_EVENTS.keySerde.serializer(),
        JOARK_EVENTS.copy(
            valueSerde = configureGenericAvroSerde(
                mapOf(
                    AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to journalpostProducerProperties.getProperty(
                        AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
                    )
                )
            )
        ).valueSerde.serializer()
    )

    private val schemaSource = """

                  {
                      "namespace" : "no.nav.joarkinngaaendehendelser.producer",
                      "type" : "record",
                      "name" : "InngaaendeHendelseRecord",
                      "fields" : [
                        {"name": "hendelsesId", "type": "string"},
                        {"name": "versjon", "type": "int"},
                        {"name": "hendelsesType", "type": "string"},
                        {"name": "journalpostId", "type": "long"},
                        {"name": "journalpostStatus", "type": "string"},
                        {"name": "temaGammelt", "type": "string"},
                        {"name": "temaNytt", "type": "string"},
                        {"name": "mottaksKanal", "type": "string"},
                        {"name": "kanalReferanseId", "type": "string"}
                      ]
                    }

                """.trimIndent()

    fun produceEvent(journalpostId: Long, tema: String) {
        val avroSchema = Schema.Parser().parse(schemaSource)
        val joarkJournalpost: GenericData.Record = GenericData.Record(avroSchema).apply {
            put("journalpostId", journalpostId)
            put("hendelsesId", journalpostId.toString())
            put("versjon", journalpostId)
            put(
                "hendelsesType",
                listOf("MidlertidigJournalført", "EndeligJournalført", "TemaEndret").shuffled().take(1)[0]
            )
            put("journalpostStatus", "journalpostStatus")
            put("temaGammelt", tema)
            put("temaNytt", tema)
            put("mottaksKanal", "mottaksKanal")
            put("kanalReferanseId", "kanalReferanseId")
        }

        LOGGER.info { "Creating InngåendeJournalpost $journalpostId to topic ${JOARK_EVENTS.name}" }
        val record: RecordMetadata = journalpostProducer.send(
            ProducerRecord(JOARK_EVENTS.name, journalpostId.toString(), joarkJournalpost)
        ).get()
        LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val bootstrapServersConfig = System.getenv("BOOTSTRAP_SERVERS_CONFIG") ?: "localhost:9092"
            val applicationIdConfig = "joark-dummy-producer"
            val props = Properties().apply {
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersConfig)
                put(StreamsConfig.CLIENT_ID_CONFIG, applicationIdConfig)
                put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            }
            val dummyJoarkProducer = DummyJoarkProducer(props)
            while (true) {
                dummyJoarkProducer.produceEvent(journalpostId = Random().nextLong(), tema = "DAG")
                TimeUnit.SECONDS.sleep(5)
            }
        }
    }
}