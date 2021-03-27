package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.streams.HealthStatus
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class JournalfoeringReplicatorTest {
    private val journalfoeringTopic = "kafka.aapen.dok.journalfoering.topic"
    val journalfoeringPartition = TopicPartition(journalfoeringTopic, 0)

    val mockConsumer = MockConsumer<String, GenericRecord>(OffsetResetStrategy.EARLIEST).also {
        it.assign(listOf(journalfoeringPartition))
        it.updateBeginningOffsets(
            mapOf(
                journalfoeringPartition to 0L,
            )
        )
    }

    @BeforeEach
    fun reset() {
        mockConsumer.updateBeginningOffsets(
            mapOf(
                journalfoeringPartition to 0L,
            )
        )
    }

    @Test
    fun `videresender journalpost med tema DAG fra onprem til aiventopic`() = runBlocking {

        val mockProducer = MockProducer(true, StringSerializer(), StringSerializer())

        val journalfoeringReplicator = JournalfoeringReplicator(
            mockConsumer,
            mockProducer,
        ).also {
            it.start()
        }

        mockConsumer.addRecord(
            ConsumerRecord(journalfoeringTopic, 0, 0L, "jpid", lagJoarkHendelse(1L, "DAG", "sadba"))
        )
        mockConsumer.addRecord(
            ConsumerRecord(journalfoeringTopic, 0, 1L, "jpid", lagJoarkHendelse(2L, "IKKEDAG", "sadba"))
        )
        mockConsumer.addRecord(
            ConsumerRecord(journalfoeringTopic, 0, 2L, "jpid", lagJoarkHendelse(3L, "IKKEDAG", "sadba"))
        )

        delay(500)

        mockProducer.history().size shouldBe 1

        mockProducer.history().first().let {
            it.topic() shouldBe AIVEN_JOURNALFOERING_TOPIC_NAME
            it.key() shouldBe "jpid"
            //language=JSON
            it.value() shouldBe """{"hendelsesId":"1","versjon":1,"hendelsesType":"sadba","journalpostId":1,"journalpostStatus":"journalpostStatus","temaGammelt":"DAG","temaNytt":"DAG","mottaksKanal":"mottakskanal","kanalReferanseId":"kanalReferanseId","behandlingstema":"DAG"}"""
        }

        val offsetData = mockConsumer.committed(setOf(journalfoeringPartition))
        offsetData[journalfoeringPartition]?.offset() shouldBe 3L
        journalfoeringReplicator.status() shouldBe HealthStatus.UP
    }

    @Test
    fun `committer ikke når det skjer feil i konsumering`() = runBlocking {

        val mockProducer = MockProducer(false, StringSerializer(), StringSerializer())

        val journalfoeringReplicator = JournalfoeringReplicator(
            mockConsumer,
            mockProducer,
        ).also {
            it.start()
        }

        mockConsumer.addRecord(
            ConsumerRecord(journalfoeringTopic, 0, 0L, "jpid", lagJoarkHendelse(1L, "DAG", "sadba"))
        )

        delay(500)
        mockProducer.errorNext(TopicAuthorizationException("Simulere feil")) shouldBe true
        delay(500)

        mockConsumer.closed() shouldBe true
        mockProducer.closed() shouldBe true
        journalfoeringReplicator.status() shouldBe HealthStatus.DOWN
    }
}
