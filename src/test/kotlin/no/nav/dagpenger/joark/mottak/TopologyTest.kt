package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.test.ConsumerRecordFactory
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertTrue

class TopologyTest {
    companion object {
        val factory = ConsumerRecordFactory<String, GenericRecord>(
            Topics.JOARK_EVENTS.name,
            Topics.JOARK_EVENTS.keySerde.serializer(),
            Topics.JOARK_EVENTS.valueSerde.serializer()
        )

        val config = Properties().apply {
            this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
            this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
        }
    }

    @Test
    fun `sjekke at inkomne journalposter blir packet`() {
        val joarkMottak = JoarkMottak(Configuration())
        TopologyTestDriver(joarkMottak.buildTopology(), config).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = topologyTestDriver.readOutput(
                DAGPENGER_INNGÅENDE_JOURNALFØRING.name,
                DAGPENGER_INNGÅENDE_JOURNALFØRING.keySerde.deserializer(),
                DAGPENGER_INNGÅENDE_JOURNALFØRING.valueSerde.deserializer()
            )

            assertTrue { null == ut }
        }
    }

}