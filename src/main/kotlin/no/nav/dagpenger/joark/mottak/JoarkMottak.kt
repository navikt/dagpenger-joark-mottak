package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}
const val DAGPENGER_NAMESPACE = "dagpenger"
private val labelNames = listOf(
    "skjemaId",
    "skjemaIdIsKnown",
    "henvendelsesType",
    "mottaksKanal",
    "hasJournalfEnhet",
    "numberOfDocuments",
    "numberOfBrukere",
    "brukerType",
    "hasIdentifikator",
    "journalTilstand",
    "hasKanalReferanseId"
)
private val jpCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .name("journalpost_received")
    .help("Number of Journalposts received on tema DAG")
    .labelNames(*labelNames.toTypedArray())
    .register()

class JoarkMottak(val config: Configuration, val journalpostArkiv: JournalpostArkiv) : Service() {

    override val SERVICE_APP_ID =
        "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = config.application.httpPort

    override fun buildTopology(): Topology {

        val builder = StreamsBuilder()

        LOGGER.info { "Consuming topic ${config.kafka.joarkTopic.name}" }

        val inngåendeJournalposter = builder.consumeGenericTopic(
            config.kafka.joarkTopic, config.kafka.schemaRegisterUrl
        )

        inngåendeJournalposter
            .filter { _, journalpostHendelse -> "DAG" == journalpostHendelse.get("temaNytt").toString() }
            .peek { _, value ->
                LOGGER.info(
                    "Received journalpost with journalpost id: ${value.get("journalpostId")} and tema: ${value.get(
                        "temaNytt"
                    )}, hendelsesType: ${value.get("hendelsesType")}"
                )
            }
            .filter { _, journalpostHendelse -> "MidlertidigJournalført" == journalpostHendelse.get("hendelsesType").toString() }
            .mapValues { _, record ->
                val journalpostId = record.get("journalpostId").toString()
                Packet().apply {
                    this.putValue("journalpostId", journalpostId)
                }
            }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = config.kafka.brokers,
            credential = config.kafka.credential()
        )
    }
}

fun main(args: Array<String>) {
    val config = Configuration()
    val journalpostArkiv = JournalpostArkivJoark(config.application.joarkJournalpostArkivUrl,
        StsOidcClient(config.application.oidcStsUrl,
            config.kafka.user!!, config.kafka.password!!)
    )

    val service = JoarkMottak(config, journalpostArkiv)
    service.start()
}
