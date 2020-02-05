package no.nav.dagpenger.joark.mottak

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
import no.nav.dagpenger.streams.Topics
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.KeyValue
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
    fun `skal prosessere brevkoder for ny søknad`() {
        val journalpostId: Long = 123

        val journalpostarkiv = mockk<JournalpostArkivJoark>()
        every { journalpostarkiv.hentInngåendeJournalpost(journalpostId.toString()) } returns dummyJournalpost(
            journalstatus = Journalstatus.MOTTATT,
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAV 04-01.04", tittel = "søknad"))
        )

        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
        }
    }

    @Test
    fun `feature toggle skal ikke ødelegge eksisterende funksjonalitet`() {
        val journalpostId: Long = 123
        val unleash = FakeUnleash()

        val journalpostarkiv = mockk<JournalpostArkivJoark>()
        every { journalpostarkiv.hentInngåendeJournalpost(journalpostId.toString()) } returns dummyJournalpost(
            journalstatus = Journalstatus.MOTTATT,
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAV 04-01.04", tittel = "ny søknad"))
        )

        val packetCreator = PacketCreator(personOppslagMock, unleash)

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            unleash.disable("dp.innlop.behandleNyBrevkode")
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null

            unleash.enable("dp.innlop.behandleNyBrevkode")

            topologyTestDriver.pipeInput(inputRecord)

            val ut2 = readOutput(topologyTestDriver)

            ut2 shouldNotBe null
        }
    }

    @Test
    fun `Skal prosessere innkommende journalposter med tema DAG og hendelses type MidlertidigJournalført `() {
        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator)
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
    fun `skal prosessere innkommende journalposter som har brevkoder gjenopptak, utdanning, etablering og klage-anke når feature toggle er på`() {
        val unleash = FakeUnleash().apply { enable("dp.innlop.behandleNyBrevkode") }
        val idTilBrevkode = mapOf(
            "1" to Pair("NAV 04-16.03", Henvendelsestype.GJENOPPTAK),
            "2" to Pair("NAV 04-06.05", Henvendelsestype.UTDANNING),
            "3" to Pair("NAV 04-06.08", Henvendelsestype.ETABLERING)
        )

        val journalpostarkiv = mockk<JournalpostArkivJoark>()

        idTilBrevkode.forEach {
            every { journalpostarkiv.hentInngåendeJournalpost(it.key) } returns dummyJournalpost(
                journalstatus = Journalstatus.MOTTATT,
                dokumenter = listOf(
                    DokumentInfo(
                        dokumentInfoId = "9",
                        brevkode = it.value.first,
                        tittel = "gjenopptak"
                    )
                )
            )
        }

        val packetCreator = PacketCreator(personOppslagMock, unleash)

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)

        val joarkhendelser = idTilBrevkode.map {
            KeyValue(it.key, lagJoarkHendelse(it.key.toLong(), "DAG", "MidlertidigJournalført") as GenericRecord)
        }.toMutableList()

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(joarkhendelser)

            topologyTestDriver.pipeInput(inputRecord)

            idTilBrevkode.forEach {
                val ut = readOutput(topologyTestDriver)

                ut shouldNotBe null
                ut?.value()?.getStringValue(PacketKeys.HENVENDELSESTYPE) shouldBe it.value.second.name
            }
        }
    }

    @Test
    fun `skal ikke prosessere innkommende journalposter som har brevkoder gjenopptak, utdanning, etablering og klage-anke når feature toggle er av`() {
        val unleash = FakeUnleash()
        val idTilBrevkode = mapOf(
            "1" to "NAV 04-16.03",
            "2" to "NAV 04-06.05",
            "3" to "NAV 04-06.08"
        )

        val journalpostarkiv = mockk<JournalpostArkivJoark>()

        idTilBrevkode.forEach {
            every { journalpostarkiv.hentInngåendeJournalpost(it.key) } returns dummyJournalpost(
                journalstatus = Journalstatus.MOTTATT,
                dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = it.value, tittel = "gjenopptak"))
            )
        }

        val packetCreator = PacketCreator(personOppslagMock, unleash)

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)

        val joarkhendelser = idTilBrevkode.map {
            KeyValue(it.key, lagJoarkHendelse(it.key.toLong(), "DAG", "MidlertidigJournalført") as GenericRecord)
        }.toMutableList()

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(joarkhendelser)

            topologyTestDriver.pipeInput(inputRecord)

            idTilBrevkode.forEach {
                val ut = readOutput(topologyTestDriver)

                ut shouldBe null
            }
        }
    }

    @Test
    fun `Skal ikke gå videre med journalposter som har annen status enn Mottatt`() {
        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val journalpostarkiv = mockk<JournalpostArkiv>()

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)

        every { journalpostarkiv.hentInngåendeJournalpost(any()) } returns dummyJournalpost(journalstatus = Journalstatus.JOURNALFOERT)

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord =
                factory.create(lagJoarkHendelse(123, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal telle antall mottatte journalposter som kan behandles`() {
        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            CollectorRegistry.defaultRegistry.getSampleValue(
                "dagpenger_journalpost_mottatt",
                arrayOf(),
                arrayOf()
            ) shouldBeGreaterThan 1.0
        }
    }

    @Test
    fun `Skal ikke prosessere journalposter med andre temaer en DAG`() {
        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "ANNET", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal ikke prosessere inkomne journalposter med tema DAG og hendelses type Ferdigstilt `() {
        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator)
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "Ferdigstilt"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `skal ikke ta vare på packets som er ettersendinger`() {
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

        val packetCreator = PacketCreator(personOppslagMock, FakeUnleash())
        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator)
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
