package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import java.util.concurrent.Future

class JoarkAivenMottakTest {
    @Test
    fun `greier å lese topic`() {
        val journalpostMottattTopic = "privat-dagpenger-journalpost-mottatt-v1"
        val søknadsdataTopic = "privat-dagpenger-soknadsdata-v1"

        val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).also {
            val topicPartition1 = TopicPartition(journalpostMottattTopic, 1)
            val topicPartition2 = TopicPartition(søknadsdataTopic, 1)
            it.assign(listOf(topicPartition1, topicPartition2))
            it.updateBeginningOffsets(
                mapOf(
                    topicPartition1 to 0L,
                    topicPartition2 to 0L
                )
            )
        }
        val mockProducer = mockk<Producer<String, String>>()
        val recordSlots = mutableListOf<ProducerRecord<String, String>>()

        coEvery { mockProducer.send(capture(recordSlots)) } returns mockk<Future<RecordMetadata>>()

        JoarkAivenMottak(
            mockConsumer,
            mockProducer,
            Configuration()
        ).start()

        mockConsumer.addRecord(ConsumerRecord(journalpostMottattTopic, 1, 0, "key", "enverdi"))
        mockConsumer.addRecord(ConsumerRecord(søknadsdataTopic, 1, 0, "key", "søknadsverdi"))

        verify { mockProducer.send(any()) }
        recordSlots.let {
            it.first().topic() shouldBe "teamdagpenger.journalforing.v1"
            it.first().value() shouldBe "enverdi"
            it.last().topic() shouldBe "teamdagpenger.soknadsdata.v1"
            it.last().value() shouldBe "søknadsverdi"
        }
    }
}
