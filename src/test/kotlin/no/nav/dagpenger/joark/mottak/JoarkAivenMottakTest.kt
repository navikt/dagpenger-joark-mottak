package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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
        val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).also {
            val topicPartition = TopicPartition("privat-dagpenger-journalpost-mottatt-v1", 1)
            it.assign(listOf(topicPartition))
            it.updateBeginningOffsets(
                mapOf(
                    topicPartition to 0L
                )
            )
        }
        val mockProducer = mockk<Producer<String, String>>()
        val recordSlot = slot<ProducerRecord<String, String>>()

        coEvery { mockProducer.send(capture(recordSlot)) } returns mockk<Future<RecordMetadata>>()

        JoarkAivenMottak(
            mockConsumer,
            mockProducer
        ).start()

        mockConsumer.addRecord(ConsumerRecord("privat-dagpenger-journalpost-mottatt-v1", 1, 0, "key", "enverdi"))
        verify { mockProducer.send(any()) }
        recordSlot.isCaptured shouldBe true
        recordSlot.captured.let {
            it.topic() shouldBe "topic"
            it.value() shouldBe "enverdi"
        }
    }
}
