package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Future

class JoarkAivenMottakTest {
    val journalpostMottattTopic = "privat-dagpenger-journalpost-mottatt-v1"
    val søknadsdataTopic = "privat-dagpenger-soknadsdata-v1"
    val journalpostPartition = TopicPartition(journalpostMottattTopic, 1)
    val soknadsdataPartition = TopicPartition(søknadsdataTopic, 1)

    val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).also {
        it.assign(listOf(journalpostPartition, soknadsdataPartition))
        it.updateBeginningOffsets(
            mapOf(
                journalpostPartition to 0L,
                soknadsdataPartition to 0L
            )
        )
    }
    val recordSlots = mutableListOf<ProducerRecord<String, String>>()

    @BeforeEach
    fun reset() {
        mockConsumer.updateBeginningOffsets(
            mapOf(
                journalpostPartition to 0L,
                soknadsdataPartition to 0L
            )
        )
    }

    @Test
    fun `sender til riktig topic`() = runBlocking {
        val mockProducer = mockk<Producer<String, String>>()
        coEvery { mockProducer.send(capture(recordSlots)) } returns mockk<Future<RecordMetadata>>()

        val joarkAivenMottak = JoarkAivenMottak(
            mockConsumer,
            mockProducer,
            Configuration()
        ).also {
            it.start()
        }

        mockConsumer.addRecord(ConsumerRecord(journalpostMottattTopic, 1, 0, "jdpid", "enverdi"))
        mockConsumer.addRecord(ConsumerRecord(søknadsdataTopic, 1, 0, "jdpid", "søknadsverdi"))

        verify { mockProducer.send(any()) }
        recordSlots.let {
            it.first().topic() shouldBe "teamdagpenger.journalforing.v1"
            it.first().key() shouldBe "jdpid"
            it.first().value() shouldBe "enverdi"
            it.last().key() shouldBe "jdpid"
            it.last().topic() shouldBe "teamdagpenger.soknadsdata.v1"
            it.last().value() shouldBe "søknadsverdi"
            it.size shouldBe 2
        }
        val offsetData = mockConsumer.committed(setOf(journalpostPartition, soknadsdataPartition))
        offsetData[journalpostPartition]?.offset() shouldBe 1L
        offsetData[soknadsdataPartition]?.offset() shouldBe 1L

        joarkAivenMottak.isAlive() shouldBe true
    }

    @Test
    fun `committer ikke når det skjer feil i konsumering`() = runBlocking {
        val mockProducer = mockk<Producer<String, String>>()
        coEvery { mockProducer.send(any()) } throws RuntimeException()
        coEvery { mockProducer.close() } just Runs

        val joarkAivenMottak = JoarkAivenMottak(
            mockConsumer,
            mockProducer,
            Configuration()
        ).also {
            it.start()
        }
        mockConsumer.addRecord(ConsumerRecord(journalpostMottattTopic, 1, 0, "key", "enverdi"))
        mockConsumer.addRecord(ConsumerRecord(søknadsdataTopic, 1, 0, "key", "søknadsverdi"))

        val offsetData = mockConsumer.committed(setOf(journalpostPartition, soknadsdataPartition))
        offsetData[journalpostPartition]?.offset() shouldBe null
        offsetData[soknadsdataPartition]?.offset() shouldBe null

        repeat(5) {
            if (!mockConsumer.closed()) {
                delay(2000)
            }
        }

        verify { mockProducer.close() }
        joarkAivenMottak.isAlive() shouldBe false
    }
}
