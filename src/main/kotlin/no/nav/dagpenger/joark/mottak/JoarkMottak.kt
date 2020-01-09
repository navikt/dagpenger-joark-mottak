package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.nav.dagpenger.events.Packet
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
    const val NY_SØKNAD: String = "nySøknad"
    const val HOVEDSKJEMA_ID: String = "hovedskjemaId"
    const val JOURNALPOST_ID: String = "journalpostId"
    const val AKTØR_ID: String = "aktørId"
    const val BEHANDLENDE_ENHET: String = "behandlendeEnhet"
    const val NATURLIG_IDENT: String = "naturligIdent"
    const val TOGGLE_BEHANDLE_NY_SØKNAD: String = "toggleBehandleNySøknad"
}

class JoarkMottak(
    val config: Configuration,
    val journalpostArkiv: JournalpostArkiv,
    val personOppslag: PersonOppslag,
    val unleash: Unleash
) : Service() {
    override val healthChecks: List<HealthCheck> =
        listOf(journalpostArkiv as HealthCheck, personOppslag as HealthCheck)

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
            .filter { _, journalpost ->
                journalpost.mapToHenvendelsesType() == Henvendelsestype.NY_SØKNAD
            }
            .mapValues { _, journalpost ->
                Packet().apply {
                    this.putValue(PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD, unleash.isEnabled("dp.innlop.behandleNySoknad"))

                    this.putValue(PacketKeys.JOURNALPOST_ID, journalpost.journalpostId)
                    this.putValue(PacketKeys.HOVEDSKJEMA_ID, journalpost.dokumenter.first().brevkode ?: "ukjent")
                    this.putValue(
                        PacketKeys.DOKUMENTER, journalpost.dokumenter
                    )

                    this.putValue(
                        PacketKeys.NY_SØKNAD,
                        journalpost.mapToHenvendelsesType() == Henvendelsestype.NY_SØKNAD
                    )

                    journalpost.relevanteDatoer.find { it.datotype == Datotype.DATO_REGISTRERT }?.let {
                        this.putValue(PacketKeys.DATO_REGISTRERT, it.dato)
                    }

                    if (null != journalpost.bruker) {

                        personOppslag.hentPerson(journalpost.bruker.id, journalpost.bruker.type).let {
                            this.putValue(PacketKeys.AKTØR_ID, it.aktoerId)
                            this.putValue(PacketKeys.NATURLIG_IDENT, it.naturligIdent)
                            this.putValue(PacketKeys.AVSENDER_NAVN, it.navn)
                            this.putValue(PacketKeys.BEHANDLENDE_ENHET, behandlendeEnhetFrom(it.diskresjonskode, journalpost.dokumenter.first().brevkode ?: "ukjent"))
                        }
                    } else {
                        logger.warn { "Journalpost er ikke tilknyttet bruker?" }
                    }
                }
            }
            .peek { _, _ -> jpMottatCounter.inc() }
            .selectKey { _, value -> value.getStringValue(PacketKeys.JOURNALPOST_ID) }
            .peek { _, packet -> logger.info { "Producing packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}" } }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    private fun behandlendeEnhetFrom(diskresjonskode: String?, brevkode: String): String {
        return when {
            diskresjonskode == "SPSF" -> "2103"
            brevkode == "NAV 04-01.03" -> "4450"
            brevkode == "NAV 04-01.04" -> "4455"
            else -> throw UnsupportedBehandlendeEnhetException("Cannot find behandlende enhet for brevkode $brevkode")
        }
    }

    private fun registerMetrics(journalpost: Journalpost) {
        val skjemaId = journalpost.dokumenter.firstOrNull()?.brevkode ?: "ukjent"
        val brukerType = journalpost.bruker?.type?.toString() ?: "ukjent"
        val henvendelsestype = journalpost.mapToHenvendelsesType().toString()
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

    val service = JoarkMottak(config, journalpostArkiv, personOppslag, unleash)
    service.start()
}
