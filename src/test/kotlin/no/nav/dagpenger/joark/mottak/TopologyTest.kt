package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.test.ConsumerRecordFactory
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertTrue

class TopologyTest {

    private val schemaRegistryClient = mockk<SchemaRegistryClient>().apply {
        every {
            this@apply.getId(any(), any())
        } returns 1

        every {
            this@apply.register(any(), any())
        } returns 1

        every {
            this@apply.getById(1)
        } returns joarkjournalfoeringhendelserAvroSchema
    }

    private val avroSerde = GenericAvroSerde(schemaRegistryClient)
    private val configuration: Configuration = Configuration()
        .copy(
            kafka = Configuration.Kafka().copy(
                joarkTopic = Topics.JOARK_EVENTS.copy(
                    valueSerde = avroSerde
                )

            )
        )

    private val factory = ConsumerRecordFactory<String, GenericRecord>(
        Topics.JOARK_EVENTS.name,
        Topics.JOARK_EVENTS.keySerde.serializer(),
        avroSerde.serializer()

    )

    private val config = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
    }

    @Test
    fun `Sjekke at inkomne journalposter med tema DAG og hendelses type MidlertidigJournalført blir prosessert`() {

        val joarkMottak = JoarkMottak(configuration)
        TopologyTestDriver(joarkMottak.buildTopology(), config).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = topologyTestDriver.readOutput(
                DAGPENGER_INNGÅENDE_JOURNALFØRING.name,
                DAGPENGER_INNGÅENDE_JOURNALFØRING.keySerde.deserializer(),
                DAGPENGER_INNGÅENDE_JOURNALFØRING.valueSerde.deserializer()
            )


            ut shouldNotBe null
            ut.value().getLongValue("journalpostId") shouldBe 123
        }
    }
}