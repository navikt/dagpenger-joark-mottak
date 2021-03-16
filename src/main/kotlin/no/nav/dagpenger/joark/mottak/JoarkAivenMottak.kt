package no.nav.dagpenger.joark.mottak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.dagpenger.plain.consumerConfig
import no.nav.dagpenger.streams.KafkaCredential
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import kotlin.coroutines.CoroutineContext

fun consumer(bootstrapServerUrl: String, credential: KafkaCredential): KafkaConsumer<String, String> {
    return KafkaConsumer<String, String>(
        consumerConfig(
            groupId = "dagpenger-joark-mottak",
            bootstrapServerUrl = bootstrapServerUrl,
            credential = credential
        ).also {
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
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

    fun stop() {
        job.cancel()
    }

    private fun run() {
        while (job.isActive) {
            consumer.poll(Duration.ofMillis(500))
                .forEach {
                    when (it.topic()) {
                        configuration.kafka.søknadsdataTopic.name -> producer.send(
                            ProducerRecord(
                                "teamdagpenger.soknadsdata.v1",
                                it.value()
                            )
                        )
                        configuration.kafka.dagpengerJournalpostTopic.name -> producer.send(
                            ProducerRecord(
                                "teamdagpenger.journalforing.v1",
                                it.value()
                            )
                        )
                    }
                }
        }
    }
}
