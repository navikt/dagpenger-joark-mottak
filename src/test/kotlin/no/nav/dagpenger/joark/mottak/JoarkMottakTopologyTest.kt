package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

    val søknadsdata = Søknadsdata(
        """{"søknadsId": "id"}""",
        "123",
        "2020-06-19"
    )

    val journalpostarkiv = mockk<JournalpostArkivJoark>(relaxed = true).also {
        every { it.hentSøknadsdata(any()) } returns søknadsdata
    }

    val packetCreator = InnløpPacketCreator(personOppslagMock)
    val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator, FakeUnleash())

    @Test
    fun `skal både produsere innløpbehov og søknadsdata`() {
        val journalpostId: Long = 123

        val joarkMottak = JoarkMottak(configuration, journalpostarkiv, packetCreator, FakeUnleash().apply { this.enableAll() })

        every { journalpostarkiv.hentInngåendeJournalpost(journalpostId.toString()) } returns dummyJournalpost(
            journalstatus = Journalstatus.MOTTATT,
            kanal = "NAV_NO",
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAV 04-01.04", tittel = "søknad"))
        )

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val utInnløp = readOutputInnløp(topologyTestDriver)
            val utSøkndadsdata = readOutputSøknadsdata(topologyTestDriver)

            withClue("Publiserte ikke innløpsbehov:") {
                utInnløp shouldNotBe null
            }
            withClue("Publiserte ikke søknadsdata:") {
                utSøkndadsdata shouldNotBe null
                utSøkndadsdata?.value() shouldBe """{"søknadsId":"id","journalpostId":"123","journalRegistrertDato":"2020-06-19"}"""
            }

            withClue("Feil format på søknadsdata") {
                utSøkndadsdata!!.value() shouldBe søknadsdata.serialize()
            }
        }
    }

    @Test
    fun `skal prosessere brevkoder for ny søknad`() {
        val journalpostId: Long = 123

        every { journalpostarkiv.hentInngåendeJournalpost(journalpostId.toString()) } returns dummyJournalpost(
            journalstatus = Journalstatus.MOTTATT,
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAV 04-01.04", tittel = "søknad"))
        )

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldNotBe null
        }
    }

    @Test
    fun `Skal prosessere innkommende journalposter med tema DAG og hendelses type MidlertidigJournalført `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator, FakeUnleash())

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getLongValue("journalpostId") shouldBe journalpostId
        }
    }

    @Test
    fun `skal prosessere innkommende journalposter som har brevkoder gjenopptak, utdanning, etablering og klage-anke`() {
        val idTilBrevkode = mapOf(
            "1" to Pair("NAV 04-16.03", Henvendelsestype.GJENOPPTAK),
            "2" to Pair("NAV 04-06.05", Henvendelsestype.UTDANNING),
            "3" to Pair("NAV 04-06.08", Henvendelsestype.ETABLERING),
            "4" to Pair("NAV 90-00.08", Henvendelsestype.KLAGE_ANKE)
        )

        idTilBrevkode.forEach {
            every { journalpostarkiv.hentInngåendeJournalpost(it.key) } returns dummyJournalpost(
                journalstatus = Journalstatus.MOTTATT,
                dokumenter = listOf(
                    DokumentInfo(
                        dokumentInfoId = "9",
                        brevkode = it.value.first,
                        tittel = "tittel"
                    )
                )
            )
        }

        val joarkhendelser = idTilBrevkode.map {
            KeyValue(it.key, lagJoarkHendelse(it.key.toLong(), "DAG", "MidlertidigJournalført") as GenericRecord)
        }.toMutableList()

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(joarkhendelser)

            topologyTestDriver.pipeInput(inputRecord)

            idTilBrevkode.forEach {
                val ut = readOutputInnløp(topologyTestDriver)

                withClue("Brevkode for henvendelse ${it.value.second.name} skal prosesseres og skal dermed ikke være null ") {
                    ut shouldNotBe null
                    ut?.value()?.getStringValue(PacketKeys.HENVENDELSESTYPE) shouldBe it.value.second.name
                }
            }
        }
    }

    @Test
    fun `Skal ikke gå videre med journalposter som har annen status enn Mottatt`() {
        every { journalpostarkiv.hentInngåendeJournalpost(any()) } returns dummyJournalpost(journalstatus = Journalstatus.JOURNALFOERT)

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord =
                factory.create(lagJoarkHendelse(123, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal telle antall mottatte journalposter som kan behandles`() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

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
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(123, "ANNET", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal ikke prosessere inkomne journalposter med tema DAG og hendelses type Ferdigstilt `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator, FakeUnleash())
        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "Ferdigstilt"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `skal ikke ta vare på packets som er ettersendinger`() {
        val journalpostId: Long = 123

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

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldBe null
        }
    }

    @Test
    fun `Skal legge på søknadsdata `() {
        val joarkMottak = JoarkMottak(configuration, DummyJournalpostArkiv(), packetCreator, FakeUnleash())

        TopologyTestDriver(joarkMottak.buildTopology(), streamProperties).use { topologyTestDriver ->
            val journalpostId: Long = 123
            val inputRecord = factory.create(lagJoarkHendelse(journalpostId, "DAG", "MidlertidigJournalført"))
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutputInnløp(topologyTestDriver)

            ut shouldNotBe null
            val packet = ut!!.value()

            packet.hasField("søknadsdata") shouldBe true
            packet.getMapValue("søknadsdata") shouldBe mapOf(
                "søknadsId" to "id",
                "journalpostId" to "123",
                "journalRegistrertDato" to "2020-06-19"

            )
        }
    }

    private fun readOutputInnløp(topologyTestDriver: TopologyTestDriver): ProducerRecord<String, Packet>? {
        return topologyTestDriver.readOutput(
            configuration.kafka.dagpengerJournalpostTopic.name,
            configuration.kafka.dagpengerJournalpostTopic.keySerde.deserializer(),
            configuration.kafka.dagpengerJournalpostTopic.valueSerde.deserializer()
        )
    }

    private fun readOutputSøknadsdata(topologyTestDriver: TopologyTestDriver): ProducerRecord<String, String>? {
        return topologyTestDriver.readOutput(
            configuration.kafka.søknadsdataTopic.name,
            configuration.kafka.søknadsdataTopic.keySerde.deserializer(),
            configuration.kafka.søknadsdataTopic.valueSerde.deserializer()
        )
    }
}
