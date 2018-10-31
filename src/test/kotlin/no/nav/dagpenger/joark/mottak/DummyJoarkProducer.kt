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
import java.util.Random
import java.util.Properties

import java.util.concurrent.TimeUnit

class DummyJoarkProducer(journalpostProducerProperties: Properties) {

    private val LOGGER = KotlinLogging.logger {}
    private val journalpostProducer = KafkaProducer(
            journalpostProducerProperties,
            JOARK_EVENTS.keySerde.serializer(),
            JOARK_EVENTS.copy(valueSerde = configureGenericAvroSerde(mapOf(
                    AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to journalpostProducerProperties.getProperty(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG)))).valueSerde.serializer()
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

    fun produceDummyMessage() {
        val id = Random().nextInt(2000).toString()
        val avroSchema = Schema.Parser().parse(schemaSource)
        val joarkJournalpost = GenericData.Record(avroSchema)
        joarkJournalpost.put("hendelsesId", id)
        joarkJournalpost.put("versjon", id.toInt())
        joarkJournalpost.put("journalpostId", id.toLong())
        joarkJournalpost.put("hendelsesType", listOf("MidlertidigJournalført", "EndeligJournalført", "TemaEndret").shuffled().take(1)[0])
        joarkJournalpost.put("journalpostStatus", "journalpostStatus")
        joarkJournalpost.put("temaGammelt", "temaGammelt")
        joarkJournalpost.put("temaNytt", "temaNy")
        joarkJournalpost.put("mottaksKanal", "mottaksKanal")
        joarkJournalpost.put("kanalReferanseId", "kanalReferanseId")

        LOGGER.info { "Creating InngåendeJournalpost $id to topic ${JOARK_EVENTS.name}" }
        val record: RecordMetadata = journalpostProducer.send(
                ProducerRecord(JOARK_EVENTS.name, id, joarkJournalpost)
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
                dummyJoarkProducer.produceDummyMessage()
                TimeUnit.SECONDS.sleep(5)
            }
        }
    }
}