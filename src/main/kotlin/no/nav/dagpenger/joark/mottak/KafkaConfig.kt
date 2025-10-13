package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import mu.KotlinLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.Properties

private val logger = KotlinLogging.logger {}

object KafkaConfig {
    internal fun joarkAivenConsumer(
        topicNames: Set<String>,
        env: Map<String, String>,
    ): KafkaConsumer<String, GenericRecord> {
        val maxPollRecords = 50
        val maxPollIntervalMs = Duration.ofSeconds(60 + maxPollRecords * 2.toLong()).toMillis()
        return KafkaConsumer<String, GenericRecord>(
            aivenConfig(env).also {
                it[ConsumerConfig.GROUP_ID_CONFIG] = JOURNALFOERING_REPLICATOR_GROUPID
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
                it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
                it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = env.getValue("KAFKA_SCHEMA_REGISTRY")
                it[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
                it[SchemaRegistryClientConfig.USER_INFO_CONFIG] =
                    env.getValue("KAFKA_SCHEMA_REGISTRY_USER") + ":" + env.getValue("KAFKA_SCHEMA_REGISTRY_PASSWORD")
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = maxPollRecords
                it[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = "$maxPollIntervalMs"
            },
        ).also {
            logger.info { "Subscribing to topics: $topicNames" }
            it.subscribe(topicNames)
        }
    }

    internal fun aivenProducer(env: Map<String, String>): KafkaProducer<String, String> {
        val properties =
            aivenConfig(env).apply {
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.LINGER_MS_CONFIG, "0")
                put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
            }
        return KafkaProducer(properties, StringSerializer(), StringSerializer())
    }

    private fun aivenConfig(env: Map<String, String>): Properties =
        Properties().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getValue("KAFKA_BROKERS"))
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        }
}
