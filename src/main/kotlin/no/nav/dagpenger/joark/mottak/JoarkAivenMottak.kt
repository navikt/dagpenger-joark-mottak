package no.nav.dagpenger.joark.mottak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.dagpenger.plain.consumerConfig
import no.nav.dagpenger.streams.KafkaCredential
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
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

fun consumer(bootstrapServerUrl: String, credential: KafkaCredential): KafkaConsumer<String, String> {
    return KafkaConsumer<String, String>(
        consumerConfig(
            groupId = "dagpenger-joark-mottak-aiven-replicator",
            bootstrapServerUrl = bootstrapServerUrl,
            credential = credential
        ).also {
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        }
    ).also {
        it.subscribe(
            listOf(
                "privat-dagpenger-soknadsdata-v1",
                "privat-dagpenger-journalpost-mottatt-v1"
            )
        )
    }
}

fun createAivenProducer(env: Map<String, String>): KafkaProducer<String, String> {
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

        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    return KafkaProducer(properties, StringSerializer(), StringSerializer())
}

class JoarkAivenMottak(
    private val consumer: Consumer<String, String>,
    private val producer: Producer<String, String>,
    private val configuration: Configuration
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    private val job: Job = Job()

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::shutdownHook))
    }

    fun start() {
        logger.info("starting JoarkAivenMottak")
        launch {
            run()
        }
    }

    fun stop() {
        logger.info("stopping JoarkAivenMottak")
        consumer.wakeup()
    }

    val aivenTopic = mapOf(
        configuration.kafka.søknadsdataTopic.name to "teamdagpenger.soknadsdata.v1",
        configuration.kafka.dagpengerJournalpostTopic.name to "teamdagpenger.journalforing.v1"
    )

    private fun run() {
        try {
            while (job.isActive) {
                onRecords(consumer.poll(Duration.ofMillis(500)))
            }
        } catch (e: WakeupException) {
            logger.info(e) { "Consumeren stenges" }
            if (job.isActive) throw e
        } catch (e: Exception) {
            logger.error(e) { "Noe feil skjedde i consumeringen" }
            throw e
        } finally {
            closeResources()
        }
    }

    private fun onRecords(records: ConsumerRecords<String, String>) {
        if (records.isEmpty) return // poll returns an empty collection in case of rebalancing
        val currentPositions = records
            .groupBy { TopicPartition(it.topic(), it.partition()) }
            .mapValues { it.value.minOf { it.offset() } }
            .toMutableMap()
        try {
            records.onEach { record ->
                val aivenTopic = requireNotNull(aivenTopic[record.topic()])
                if (record.topic() == "privat-dagpenger-soknadsdata-v1") {
                    producer.send(ProducerRecord(aivenTopic, record.key(), record.value()))
                    logger.info { "Migrerte soknadsdata: ${record.key()} til aiven topic" }
                }
                currentPositions[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
            }
        } catch (err: Exception) {
            logger.info(
                "due to an error during processing, positions are reset to each next message (after each record that was processed OK):" +
                    currentPositions.map { "\tpartition=${it.key}, offset=${it.value}" }.joinToString(separator = "\n", prefix = "\n", postfix = "\n"),
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
