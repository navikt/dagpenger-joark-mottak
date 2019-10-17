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

private val logger = KotlinLogging.logger {}
const val DAGPENGER_NAMESPACE = "dagpenger"
private val labelNames = listOf(
    "skjemaId",
    "brukerType",
    "henvendelsestype"

)
private val jpCounter = Counter
    .build()
    .namespace(DAGPENGER_NAMESPACE)
    .name("journalpost_received")
    .help("Number of Journalposts received on tema DAG")
    .labelNames(*labelNames.toTypedArray())
    .register()

internal object PacketKeys {
    const val NY_SØKNAD: String = "nySøknad"
    const val HOVEDSKJEMA_ID: String = "hovedskjemaId"
    const val AKTØR_ID: String = "aktørId"
    const val JOURNALPOST_ID: String = "journalpostId"
}

class JoarkMottak(val config: Configuration, val journalpostArkiv: JournalpostArkiv) : Service() {

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
            .peek { _, value ->
                logger.info(
                    "Received journalpost with journalpost id: ${value.get(PacketKeys.JOURNALPOST_ID)} and tema: ${value.get(
                        "temaNytt"
                    )}, hendelsesType: ${value.get("hendelsesType")}"
                )
            }
            .filter { _, journalpostHendelse -> "MidlertidigJournalført" == journalpostHendelse.get("hendelsesType").toString() }
            .mapValues { _, record ->
                val journalpostId = record.get(PacketKeys.JOURNALPOST_ID).toString()

                mapAktørId(journalpostArkiv.hentInngåendeJournalpost(journalpostId))
                    .also { logger.info { "Journalpost: $it}" } }
                    .also { registerMetrics(it) }
            }
            .mapValues { _, journalpost ->
                Packet().apply {
                    this.putValue(PacketKeys.JOURNALPOST_ID, journalpost.journalpostId)
                    this.putValue(PacketKeys.AKTØR_ID, journalpost.bruker?.id ?: "")
                    this.putValue(PacketKeys.HOVEDSKJEMA_ID, journalpost.dokumenter.first().brevkode ?: "ukjent")
                    this.putValue(PacketKeys.NY_SØKNAD, journalpost.mapToHenvendelsesType() == Henvendelsestype.NY_SØKNAD)
                }
            }
            .selectKey { _, value -> value.getStringValue(PacketKeys.JOURNALPOST_ID) }
            .toTopic(config.kafka.dagpengerJournalpostTopic)

        return builder.build()
    }

    private fun registerMetrics(journalpost: Journalpost) {
        val skjemaId = journalpost.dokumenter.firstOrNull()?.brevkode ?: "ukjent"
        val brukerType = journalpost.bruker?.type.toString()
        val henvendelsestype = journalpost.mapToHenvendelsesType().toString()

        jpCounter
            .labels(
                skjemaId,
                brukerType,
                henvendelsestype
            )
            .inc()
    }

    private fun mapAktørId(it: Journalpost) = it.copy(bruker = it.bruker?.copy(id = "1111"))

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
    val journalpostArkiv = JournalpostArkivJoark(
        config.application.joarkJournalpostArkivUrl,
        StsOidcClient(
            config.application.oidcStsUrl,
            config.kafka.user!!, config.kafka.password!!
        )
    )

    val service = JoarkMottak(config, journalpostArkiv)
    service.start()
}
