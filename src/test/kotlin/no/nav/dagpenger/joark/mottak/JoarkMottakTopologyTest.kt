package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.test.ConsumerRecordFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Properties

class JoarkMottakTopologyTest {
    companion object {
        val personOppslagMock = mockk<PersonOppslag>()

        @BeforeAll
        @JvmStatic
        fun setUp() {
            every { personOppslagMock.hentPerson(any(), any()) } returns Person(
                aktoerId = "1111",
                naturligIdent = "1234",
                behandlendeEnheter = listOf(BehandlendeEnhet(enhetId = "abc", enhetNavn = "NAV Enhet"))
            )
        }
    }

    @Test
    fun `Skal prosessere inkomne journalposter med tema DAG og hendelses type MidlertidigJournalført `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getLongValue("journalpostId") shouldBe journalpostId
        }
    }

    @Test
    fun `Skal ikke prosessere journalposter med andre temaer en DAG`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "ANNET", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal ikke prosessere inkomne journalposter med tema DAG og hendelses type Ferdigstilt `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "Ferdigstilt"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `skal mappe aktørid riktig`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getStringValue("aktørId") shouldBe "1111"
        }
    }

    @Test
    fun `skal gi hovedskjema`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getStringValue("hovedskjemaId") shouldBe "NAV 04-01.04"
            ut?.value()?.getBoolean("nySøknad") shouldBe true
        }
    }

    @Test
    fun `skal ikke ta vare på packets som ikke er NY_SØKNAD`() {
        val journalpostId: Long = 123

        val journalpostarkiv = mockk<JournalpostArkivJoark>()
        every { journalpostarkiv.hentInngåendeJournalpost(journalpostId.toString()) } returns Journalpost(
            journalstatus = Journalstatus.MOTTATT,
            journalpostId = "123",
            bruker = Bruker(BrukerType.AKTOERID, "123"),
            tittel = "Kul tittel",
            kanal = "NAV.no",
            datoOpprettet = "2019-05-05",
            kanalnavn = "DAG",
            journalforendeEnhet = "Uvisst",
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAVe 04-01.04"))
        )

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, personOppslagMock)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

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

    private val streamProperties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
    }

    private fun readOutput(topologyTestDriver: TopologyTestDriver): ProducerRecord<String, Packet>? {
        return topologyTestDriver.readOutput(
            configuration.kafka.dagpengerJournalpostTopic.name,
            configuration.kafka.dagpengerJournalpostTopic.keySerde.deserializer(),
            configuration.kafka.dagpengerJournalpostTopic.valueSerde.deserializer()
        )
    }
}