package no.nav.dagpenger.joark.mottak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
import kotlin.coroutines.CoroutineContext

class JoarkAivenMottak(private val configuration: Configuration) : CoroutineScope {

    private val consumer: KafkaConsumer<String, GenericRecord> = TODO()
    private val aivenProducer: KafkaProducer<String, String> = TODO()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    lateinit var job: Job

    init {
        consumer.subscribe(
            listOf(
                configuration.kafka.søknadsdataTopic.name,
                configuration.kafka.dagpengerJournalpostTopic.name
            )
        )
    }

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
                        configuration.kafka.søknadsdataTopic.name -> aivenProducer.send(ProducerRecord("topic", "internvalue"))
                        configuration.kafka.dagpengerJournalpostTopic.name -> aivenProducer.send(ProducerRecord("topic", "internvalue"))
                    }
                }
        }
    }
}
