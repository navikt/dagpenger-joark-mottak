package no.nav.dagpenger.joark.mottak

import io.mockk.mockk
import io.mockk.verify
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.dagpenger.plain.defaultProducerConfig
import no.nav.dagpenger.plain.producerConfig
import no.nav.dagpenger.streams.KafkaCredential
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test

class JoarkAivenMottakTest {
    private object Kafka {
        val username = "srvkafkaclient"
        val password = "kafkaclient"
        val instance by lazy {

            KafkaEnvironment(
                users = listOf(JAASCredential(username, password)),
                autoStart = false,
                withSchemaRegistry = true,
                withSecurity = true,
                topicInfos = listOf(
                    KafkaEnvironment.TopicInfo("privat-dagpenger-journalpost-mottatt-v1"),
                    KafkaEnvironment.TopicInfo("privat-dagpenger-soknadsdata-v1"),
                )
            ).also {
                it.start()
            }
        }
    }

    @Test
    fun `greier å lese topic`() {
        val mokk = mockk<KafkaProducer<String, String>>()
        JoarkAivenMottak(
            consumer(
                Kafka.instance.brokersURL,
                KafkaCredential(Kafka.username, Kafka.password)
            ),
            mokk
        ).start()
        val producer = KafkaProducer<String, String>(
            producerConfig(
                clientId = "test",
                bootstrapServers = Kafka.instance.brokersURL,
                credential = KafkaCredential(Kafka.username, Kafka.password),
                properties = defaultProducerConfig.apply {
                    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                }
            ).also {
                it[ProducerConfig.ACKS_CONFIG] = "all"
            }
        )
        producer.send(ProducerRecord("privat-dagpenger-journalpost-mottatt-v1", "enverdi"))
        Thread.sleep(2000)
        verify { mokk.send(any()) }
    }
}
