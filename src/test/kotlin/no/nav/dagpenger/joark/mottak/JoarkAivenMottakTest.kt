package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.streams.HealthStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JoarkAivenMottakTest {
    val journalpostMottattTopic = "privat-dagpenger-journalpost-mottatt-v1"
    val journalpostPartition = TopicPartition(journalpostMottattTopic, 1)

    val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).also {
        it.assign(listOf(journalpostPartition))
        it.updateBeginningOffsets(
            mapOf(
                journalpostPartition to 0L,
            )
        )
    }

    @BeforeEach
    fun reset() {
        mockConsumer.updateBeginningOffsets(
            mapOf(
                journalpostPartition to 0L,
            )
        )
    }

    @Test
    fun `videresender journalpost fra onprem til aiventopic`() = runBlocking {

        val mockProducer = MockProducer(true, StringSerializer(), StringSerializer())

        val joarkAivenMottak = JoarkAivenMottak(
            mockConsumer,
            mockProducer,
        ).also {
            it.start()
        }
        mockConsumer.addRecord(ConsumerRecord(journalpostMottattTopic, 1, 0, "jdpid", "enverdi"))

        delay(500)

        mockProducer.history().first().let {
            it.topic() shouldBe "teamdagpenger.journalforing.v1"
            it.key() shouldBe "jdpid"
            it.value() shouldBe "enverdi"
        }
        val offsetData = mockConsumer.committed(setOf(journalpostPartition))
        offsetData[journalpostPartition]?.offset() shouldBe 1L

        joarkAivenMottak.status() shouldBe HealthStatus.UP
    }

    @Test
    fun `committer ikke når det skjer feil i konsumering`() = runBlocking {

        val mockProducer = MockProducer(false, StringSerializer(), StringSerializer())

        val joarkAivenMottak = JoarkAivenMottak(
            mockConsumer,
            mockProducer,
        ).also {
            it.start()
        }

        mockConsumer.addRecord(ConsumerRecord(journalpostMottattTopic, 1, 0, "jdpid", "enverdi"))

        delay(500)
        mockProducer.errorNext(TopicAuthorizationException("Simulere feil")) shouldBe true
        delay(500)

        mockConsumer.closed() shouldBe true
        mockProducer.closed() shouldBe true
        joarkAivenMottak.status() shouldBe HealthStatus.DOWN
    }
}
