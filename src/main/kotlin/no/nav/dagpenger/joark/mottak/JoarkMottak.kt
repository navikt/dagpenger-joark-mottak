package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.HealthCheck
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.util.Properties

private val logger = KotlinLogging.logger {}
const val DAGPENGER_NAMESPACE = "dagpenger"
private val labelNames = listOf(
    "skjemaId",
    "brukerType",
    "henvendelsestype",
    "skjemaIdIsKnown",
    "numberOfDocuments",
    "kanal",
    "kanalnavn",
    "journalTilstand"

)
private val jpCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .name("journalpost_received")
    .help("Number of Journalposts received on tema DAG")
    .labelNames(*labelNames.toTypedArray())
    .register()

private val jpMottatCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .help("Antall journalposter dp joark mottak kan behandle (etter filtrering)")
    .name("journalpost_mottatt")
    .register()

internal object PacketKeys {
    const val DOKUMENTER: String = "dokumenter"
    const val AVSENDER_NAVN: String = "avsenderNavn"
    const val DATO_REGISTRERT: String = "datoRegistrert"
    const val HOVEDSKJEMA_ID: String = "hovedskjemaId"
    const val JOURNALPOST_ID: String = "journalpostId"
    const val AKTØR_ID: String = "aktørId"
    const val BEHANDLENDE_ENHET: String = "behandlendeEnhet"
    const val NATURLIG_IDENT: String = "naturligIdent"
    const val TOGGLE_BEHANDLE_NY_SØKNAD: String = "toggleBehandleNySøknad"
    const val HENVENDELSESTYPE: String = "henvendelsestype"
}

class JoarkMottak(
    val config: Configuration,
    val journalpostArkiv: JournalpostArkiv,
    val packetCreator: PacketCreator
) : Service() {
    override val healthChecks: List<HealthCheck> =
        listOf(journalpostArkiv as HealthCheck, packetCreator.personOppslag as HealthCheck)

    override val SERVICE_APP_ID =
        "dagpenger-joark-mottak-opprydder1" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = config.application.httpPort

    override fun buildTopology(): Topology {

        val builder = StreamsBuilder()

        logger.info { "Consuming topic ${config.kafka.joarkTopic.name}" }

        val inngåendeJournalposter = builder.consumeGenericTopic(
            config.kafka.joarkTopic, config.kafka.schemaRegisterUrl
        )

        inngåendeJournalposter
            .filter { _, jp -> journalpostIdsFrom21OfJanuary.contains(jp[PacketKeys.JOURNALPOST_ID]) }
            .filter { _, journalpostHendelse -> "DAG" == journalpostHendelse.get("temaNytt").toString() }
            .peek { _, record ->
                logger.info(
                    "Received journalpost with journalpost id: ${record[PacketKeys.JOURNALPOST_ID]} and tema: ${record["temaNytt"]}, hendelsesType: ${record["hendelsesType"]}, mottakskanal, ${record["mottaksKanal"]} "
                )
            }
            .filter { _, journalpostHendelse -> "MidlertidigJournalført" == journalpostHendelse.get("hendelsesType").toString() }
            .mapValues { _, record ->
                val journalpostId = record.get(PacketKeys.JOURNALPOST_ID).toString()

                journalpostArkiv.hentInngåendeJournalpost(journalpostId)
                    .also { logger.info { "Journalpost --> $it" } }
                    .also { registerMetrics(it) }
            }
            .filter { _, journalpost -> journalpost.journalstatus == Journalstatus.MOTTATT }
            .filter { _, journalpost -> journalpost.henvendelsestype.erStøttet() }
            .filter { _, journalpost -> toggleStøtte(journalpost.henvendelsestype) }
            .mapValues { _, journalpost -> packetCreator.createPacket(journalpost) }
            .peek { _, _ -> jpMottatCounter.inc() }
            .selectKey { _, value -> value.getStringValue(PacketKeys.JOURNALPOST_ID) }
            .peek { _, packet ->
                logger.info {
                    "Producing packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)} and henvendelsestype: ${packet.getStringValue(
                        PacketKeys.HENVENDELSESTYPE
                    )}"
                }
            }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    private fun Henvendelsestype.erStøttet() = this in listOf(
        Henvendelsestype.NY_SØKNAD,
        Henvendelsestype.UTDANNING,
        Henvendelsestype.GJENOPPTAK,
        Henvendelsestype.ETABLERING,
        Henvendelsestype.KLAGE_ANKE
    )

    private fun toggleStøtte(henvendelsestype: Henvendelsestype): Boolean {
        return henvendelsestype == Henvendelsestype.NY_SØKNAD || packetCreator.unleash.isEnabled("dp.innlop.behandleNyBrevkode")
    }

    private fun registerMetrics(journalpost: Journalpost) {
        val skjemaId = journalpost.dokumenter.firstOrNull()?.brevkode ?: "ukjent"
        val brukerType = journalpost.bruker?.type?.toString() ?: "ukjent"
        val henvendelsestype = journalpost.henvendelsestype.toString()
        val skjemaIdKjent = HenvendelsesTypeMapper.isKnownSkjemaId(skjemaId).toString()
        val numberOfDocuments = journalpost.dokumenter.size.toString()
        val kanal = journalpost.kanal?.let { it } ?: "ukjent"
        val kanalnavn = journalpost.kanalnavn?.let { it } ?: "ukjent"
        val journalTilstand = journalpost.journalstatus?.name ?: "ukjent"

        jpCounter
            .labels(
                skjemaId,
                brukerType,
                henvendelsestype,
                skjemaIdKjent,
                numberOfDocuments,
                kanal,
                kanalnavn,
                journalTilstand
            )
            .inc()
    }

    override fun getConfig(): Properties {
        val properties = streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = config.kafka.brokers,
            credential = config.kafka.credential()
        )
        properties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = config.kafka.processingGuarantee
        return properties
    }
}

class UnsupportedBehandlendeEnhetException(override val message: String) : RuntimeException(message)

fun main(args: Array<String>) {
    val config = Configuration()
    val oidcClient = StsOidcClient(
        config.application.oidcStsUrl,
        config.kafka.user, config.kafka.password
    )

    val unleash: Unleash = DefaultUnleash(config.unleashConfig)

    val journalpostArkiv = JournalpostArkivJoark(
        config.application.joarkJournalpostArkivBaseUrl,
        oidcClient
    )

    val personOppslag = PersonOppslag(
        config.application.personOppslagBaseUrl,
        oidcClient,
        config.application.graphQlApiKey
    )

    val packetCreator = PacketCreator(personOppslag, unleash)

    val service = JoarkMottak(config, journalpostArkiv, packetCreator)
    service.start()
}

val journalpostIdsFrom21OfJanuary = setOf<String>(
    "468016607",
    "468016568",
    "468016220",
    "468015943",
    "468015860",
    "468015745",
    "468015610",
    "468014912",
    "468014797",
    "468014383",
    "468014208",
    "468014103",
    "468014101",
    "468013987",
    "468013955",
    "468013618",
    "468013590",
    "468013486",
    "468013027",
    "468013020",
    "468012822",
    "468012562",
    "468012281",
    "468011335",
    "468011281",
    "468010951",
    "468010880",
    "468010655",
    "468010489",
    "468010440",
    "468010261",
    "468010258",
    "468010050",
    "468010030",
    "468009993",
    "468009804",
    "468009754",
    "468008991",
    "468008716",
    "468008713",
    "468008553",
    "468008513",
    "468008294",
    "468008322",
    "468008110",
    "468007999",
    "468007796",
    "468007789",
    "468007653",
    "468007344",
    "468007094",
    "468007086",
    "468006986",
    "468006754",
    "468006752",
    "468006643",
    "468006547",
    "468006609",
    "468006594",
    "468006457",
    "468006368",
    "468006216",
    "468006098",
    "468006060",
    "468005862",
    "468005587",
    "468005618",
    "468005546",
    "468005189",
    "468005209",
    "468005141",
    "468005080",
    "468004966",
    "468004897",
    "468004860",
    "468004823",
    "468004751",
    "468004587",
    "468004481",
    "468004432",
    "468004469",
    "468004430",
    "468004356",
    "468004380",
    "468004104",
    "468003970",
    "468003669",
    "468003589",
    "468003534",
    "468003446",
    "468003381",
    "468003176",
    "468003154",
    "468003043",
    "468003041",
    "468002763",
    "468002686",
    "468002657",
    "468002216",
    "468002162",
    "468002111",
    "468002019",
    "468001980",
    "468001958",
    "468001871",
    "468001817",
    "468001486",
    "468001385",
    "468001404",
    "468001319",
    "468001269",
    "468001191",
    "468001156",
    "468001134",
    "468001126",
    "468001054",
    "468001035",
    "468000910",
    "468000743",
    "468000651",
    "468000527",
    "468000480",
    "468000416",
    "468000353",
    "468000220",
    "468000080",
    "468000055",
    "468000032",
    "467999899",
    "467999844",
    "467999837",
    "467999472",
    "467999475",
    "467999178",
    "467998591",
    "467998227",
    "467998140",
    "467998088",
    "467998018",
    "467997858",
    "467997792",
    "467997795",
    "467997480",
    "467997312",
    "467997335",
    "467996847",
    "467996629",
    "467996546",
    "467996346",
    "467996035",
    "467995903",
    "467995623",
    "467995648",
    "467995599",
    "467994793",
    "467994669",
    "467994664",
    "467994665",
    "467994420",
    "467994127",
    "467993915",
    "467993586",
    "467993528",
    "467993288",
    "467993210",
    "467993061",
    "467992881",
    "467992819",
    "467992774",
    "467992449",
    "467992422",
    "467992154",
    "467992142",
    "467992039",
    "467991949",
    "467991815",
    "467991780",
    "467991691",
    "467991464",
    "467991385",
    "467991341",
    "467991303",
    "467991245"

)