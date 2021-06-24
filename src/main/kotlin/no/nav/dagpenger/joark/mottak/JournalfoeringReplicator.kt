package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.dagpenger.plain.consumerConfig
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.HealthStatus
import no.nav.dagpenger.streams.KafkaCredential
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

internal const val JOURNALFOERING_REPLICATOR_GROUPID = "dagpenger-journalfoering-aiven-replicator-v1"
internal const val AIVEN_JOURNALFOERING_TOPIC_NAME = "teamdagpenger.mottak.v1"

internal fun joarkConsumer(
    bootstrapServerUrl: String,
    credential: KafkaCredential,
    schemaUrl: String,
    topicName: String
): KafkaConsumer<String, GenericRecord> {
    val maxPollRecords = 50
    val maxPollIntervalMs = Duration.ofSeconds(60 + maxPollRecords * 2.toLong()).toMillis()
    return KafkaConsumer<String, GenericRecord>(
        consumerConfig(
            groupId = JOURNALFOERING_REPLICATOR_GROUPID,
            bootstrapServerUrl = bootstrapServerUrl,
            credential = credential,
            Properties().also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
                it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
                it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaUrl
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = maxPollRecords
                it[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = "$maxPollIntervalMs"
            }
        )
    ).also {
        it.subscribe(listOf(topicName))
    }
}

internal fun aivenProducer(env: Map<String, String>): KafkaProducer<String, String> {
    val properties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getValue("KAFKA_BROKERS"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))

        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    return KafkaProducer(properties, StringSerializer(), StringSerializer())
}

internal class JournalfoeringReplicator(
    private val consumer: Consumer<String, GenericRecord>,
    private val producer: Producer<String, String>
) : CoroutineScope, HealthCheck {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    private val job: Job = Job()

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::shutdownHook))
    }

    override fun status(): HealthStatus {
        return when (
            job.isActive && isAlive { producer.partitionsFor(AIVEN_JOURNALFOERING_TOPIC_NAME) }
        ) {
            false -> HealthStatus.DOWN
            true -> HealthStatus.UP
        }
    }

    fun start() {
        logger.info("starting JournalfoeringReplicator")
        launch {
            run()
        }
    }

    private fun isAlive(check: () -> Any): Boolean = runCatching(check).fold(
        { true },
        {
            logger.error("Alive sjekk feilet", it)
            false
        }
    )

    fun stop() {
        logger.info("stopping JournalfoeringReplicator")
        consumer.wakeup()
        job.cancel()
    }

    private fun run() {
        try {
            while (job.isActive) {
                onRecords(consumer.poll(Duration.ofSeconds(1)))
            }
        } catch (e: WakeupException) {
            if (job.isActive) throw e
        } catch (e: Exception) {
            logger.error(e) { "Noe feil skjedde i consumeringen" }
            throw e
        } finally {
            closeResources()
        }
    }

    private fun onRecords(records: ConsumerRecords<String, GenericRecord>) {
        if (records.isEmpty) return // poll returns an empty collection in case of rebalancing
        val currentPositions = records
            .groupBy { TopicPartition(it.topic(), it.partition()) }
            .mapValues { partition -> partition.value.minOf { it.offset() } }
            .toMutableMap()
        try {
            records.onEach { record ->
                if (record.value().isTemaDagpenger()) {
                    producer.send(
                        ProducerRecord(
                            AIVEN_JOURNALFOERING_TOPIC_NAME,
                            record.value().journalPostId(),
                            record.value().toJson()
                        )
                    ).get(500, TimeUnit.MILLISECONDS)
                    logger.info { "Migrerte ${record.topic()} med nøkkel: ${record.value().journalPostId()} til aiven topic" }
                }
                currentPositions[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
            }
        } catch (err: Exception) {
            logger.info(
                "due to an error during processing, positions are reset to each next message (after each record that was processed OK):" +
                    currentPositions.map { "\tpartition=${it.key}, offset=${it.value}" }
                        .joinToString(separator = "\n", prefix = "\n", postfix = "\n"),
                err
            )
            currentPositions.forEach { (partition, offset) -> consumer.seek(partition, offset) }
            throw err
        } finally {
            consumer.commitSync()
        }
    }

    private fun closeResources() {
        tryAndLog(producer::close)
        tryAndLog(consumer::unsubscribe)
        tryAndLog(consumer::close)
    }

    private fun tryAndLog(block: () -> Unit) {
        try {
            block()
        } catch (err: Exception) {
            logger.error(err.message, err)
        }
    }

    private fun shutdownHook() {
        logger.info("received shutdown signal, stopping app")
        stop()
    }
}

private fun GenericRecord.isTemaDagpenger(): Boolean = "DAG" == this.get("temaNytt").toString()
private fun GenericRecord.journalPostId() = this.get("journalpostId").toString()

private data class JournalfoeringHendelse(
    val hendelsesId: String,
    val versjon: Int,
    val hendelsesType: String,
    val journalpostId: Long,
    val journalpostStatus: String,
    val temaGammelt: String,
    val temaNytt: String,
    val mottaksKanal: String,
    val kanalReferanseId: String,
    val behandlingstema: String,
)

private fun GenericRecord.toJson() = jacksonObjectMapper().writeValueAsString(
    JournalfoeringHendelse(
        hendelsesId = get("hendelsesId").toString(),
        versjon = get("versjon") as Int,
        hendelsesType = get("hendelsesType").toString(),
        journalpostId = get("journalpostId") as Long,
        journalpostStatus = get("journalpostStatus").toString(),
        temaGammelt = get("temaGammelt").toString(),
        temaNytt = get("temaNytt").toString(),
        mottaksKanal = get("mottaksKanal").toString(),
        kanalReferanseId = get("kanalReferanseId").toString(),
        behandlingstema = get("behandlingstema")?.toString() ?: ""
    )
)
