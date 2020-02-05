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
        "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = config.application.httpPort

    override fun buildTopology(): Topology {

        val builder = StreamsBuilder()

        logger.info { "Consuming topic ${config.kafka.joarkTopic.name}" }

        val inngåendeJournalposter = builder.consumeGenericTopic(
            config.kafka.joarkTopic, config.kafka.schemaRegisterUrl
        )

        inngåendeJournalposter
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
            .mapValues { _, journalpost ->
                packetCreator.createPacket(journalpost)
            }
            .peek { _, _ -> jpMottatCounter.inc() }
            .selectKey { _, value -> value.getStringValue(PacketKeys.JOURNALPOST_ID) }
            .peek { _, packet -> logger.info { "Producing packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}" } }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

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

private fun Henvendelsestype.erStøttet() = this in listOf(
    Henvendelsestype.NY_SØKNAD,
    Henvendelsestype.UTDANNING,
    Henvendelsestype.GJENOPPTAK,
    Henvendelsestype.ETABLERING
)

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
