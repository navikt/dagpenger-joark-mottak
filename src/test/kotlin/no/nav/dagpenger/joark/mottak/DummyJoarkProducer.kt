package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import java.util.Properties
import java.util.Random
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig

class DummyJoarkProducer(properties: Properties) {

    private val LOGGER = KotlinLogging.logger {}
    private val journalpostProducer = KafkaProducer<String, GenericRecord>(properties)

    fun produceEvent(journalpostId: Long, tema: String, hendelsesType: String) {
        val joarkJournalpost: GenericData.Record = lagJoarkHendelse(journalpostId, tema, hendelsesType)
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
            val schemaRegisterUrl = System.getenv("KAFKA_SCHEMA_REGISTRY_URL") ?: "http://localhost:8081"
            val applicationIdConfig = "joark-dummy-producer"
            val props = Properties().apply {
                put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersConfig)
                put(StreamsConfig.CLIENT_ID_CONFIG, applicationIdConfig)
                put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegisterUrl)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java.name)
                put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=igroup password=itest;")
            }
            val dummyJoarkProducer = DummyJoarkProducer(props)
            while (true) {
                dummyJoarkProducer.produceEvent(journalpostId = Random().nextLong(), tema = "DAG", hendelsesType = listOf("MidlertidigJournalført", "EndeligJournalført", "TemaEndret").shuffled().take(1)[0])
                TimeUnit.SECONDS.sleep(5)
            }
        }
    }
}
