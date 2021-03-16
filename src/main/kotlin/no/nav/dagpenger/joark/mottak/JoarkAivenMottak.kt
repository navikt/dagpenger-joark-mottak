package no.nav.dagpenger.joark.mottak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.dagpenger.plain.consumerConfig
import no.nav.dagpenger.streams.KafkaCredential
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
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

private fun createAivenProducer(env: Map<String, String>): KafkaProducer<String, String>? {
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

    fun start() {
        launch {
            run()
        }
    }

    suspend fun stop() {
        consumer.wakeup()
        producer.flush()
        producer.close()
        delay(3000)
        job.cancel()
    }

    val aivenTopic = mapOf(
        configuration.kafka.søknadsdataTopic.name to "teamdagpenger.soknadsdata.v1",
        configuration.kafka.dagpengerJournalpostTopic.name to "teamdagpenger.journalforing.v1"
    )

    private fun run() {
        try {
            while (job.isActive) {
                consumer.poll(Duration.ofMillis(500))
                    .forEach {
                        val aivenTopic = requireNotNull(aivenTopic[it.topic()])
                        producer.send(ProducerRecord(aivenTopic, it.value()))
                    }
                consumer.commitSync()
            }
        } catch (e: WakeupException) {
            logger.info(e) { "Consumeren stenges" }
        } catch (e: Exception) {
            logger.error(e) { "Noe feil skjedde" }
            throw e
        } finally {
            consumer.close()
        }
    }
}