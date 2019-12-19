package no.nav.dagpenger.joark.mottak

import com.squareup.moshi.Types
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.finn.unleash.FakeUnleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
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
                navn = "Proffen",
                aktoerId = "1111",
                naturligIdent = "1234",
                diskresjonskode = null
            )
        }
    }

    @Test
    fun `Skal prosessere inkomne journalposter med tema DAG og hendelses type MidlertidigJournalført `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
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
    fun `Skal telle antall mottatte journalposter som kan behandles`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_mottatt", arrayOf(), arrayOf()) shouldBeGreaterThan 1.0
        }
    }

    @Test
    fun `Skal ikke prosessere journalposter med andre temaer en DAG`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "ANNET", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal ikke prosessere inkomne journalposter med tema DAG og hendelses type Ferdigstilt `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
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
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
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
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
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
    fun `skal legge dokumentliste på pakken i JSON-format`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val adapter = moshiInstance.adapter<List<DokumentInfo>>(
                Types.newParameterizedType(
                    List::class.java,
                    DokumentInfo::class.java
                )
            )

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            val dokumentListe = ut?.value()?.getObjectValue("dokumenter") { adapter.fromJsonValue(it)!! }!!
            dokumentListe.size shouldBe 1
            dokumentListe.first().tittel shouldBe "Søknad"
        }
    }

    @Test
    fun `skal legge navn på pakken`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getStringValue("avsenderNavn") shouldBe "Proffen"
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
            relevanteDatoer = listOf(RelevantDato(dato = "2018-01-01T12:00:00", datotype = Datotype.DATO_REGISTRERT)),
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAVe 04-01.04", tittel = "søknad"))
        )

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, personOppslagMock, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `skal legge behandlings-toggle med verdi true på packet når toggle er på `() {
        val unleash = FakeUnleash()
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMock, unleash)

        unleash.disable("dp.innlop.behandleNySoknad")

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getBoolean("toggleBehandleNySøknad") shouldBe false
        }

        unleash.enable("dp.innlop.behandleNySoknad")

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(124, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getBoolean("toggleBehandleNySøknad") shouldBe true
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

    @Test
    fun `skal få riktig behandlende enhet ved kode 6`() {
        val personOppslagMedDiskresjonskode = mockk<PersonOppslag>()

        every { personOppslagMedDiskresjonskode.hentPerson(any(), any()) } returns Person(
            navn = "Proffen",
            aktoerId = "1111",
            naturligIdent = "1234",
            diskresjonskode = "SPSF"
        )

        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), personOppslagMedDiskresjonskode, FakeUnleash())

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getStringValue("behandlendeEnhet") shouldBe "2103"
        }
    }
}