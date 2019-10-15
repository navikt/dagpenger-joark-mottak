package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
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

class JoarkMottak(private val config: Configuration) : Service() {
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
                Packet().apply {
                    this.putValue("journalpostId", record.get("journalpostId").toString())
                }
            }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    override fun getConfig(): Properties {
        val credential = config.kafka.credential()

        credential?.let {
            LOGGER.info { "Using kafka credential ${it.username} and password starting with ${it.password.substring(3)}" }
        }

        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = config.kafka.brokers,
            credential = credential
        )
    }
}

fun main(args: Array<String>) {
    val config = Configuration()
    val service = JoarkMottak(config)
    service.start()
}
