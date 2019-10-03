package no.nav.dagpenger.joark.mottak

import io.prometheus.client.Counter
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.PacketDeserializer
import no.nav.dagpenger.streams.PacketSerializer
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topic
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

const val DAGPENGER_NAMESPACE = "dagpenger"
class JoarkMottak(val config: Configuration) : Service() {
    override val SERVICE_APP_ID =
        "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = config.application.httpPort

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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val config = Configuration()
            val service = JoarkMottak(config)
            service.start()
        }
    }

    override fun buildTopology(): Topology {

        val builder = StreamsBuilder()

        val inngåendeJournalposter = builder.consumeGenericTopic(
            config.kafka.joarkTopic, config.kafka.schemaRegisterUrl
        )

        inngåendeJournalposter
            .peek { _, value ->
                LOGGER.info(
                    "Received journalpost with journalpost id: ${value.get("journalpostId")} and tema: ${value.get(
                        "temaNytt"
                    )}, hendelsesType: ${value.get("hendelsesType")}"
                )
            }
            .filter { _, journalpostHendelse -> "DAG" == journalpostHendelse.get("temaNytt").toString() }
            .filter { _, journalpostHendelse -> "MidlertidigJournalført" == journalpostHendelse.get("hendelsesType").toString() }
            .mapValues { _, record ->  Packet().apply {
                this.putValue("journalpostId", record.get("journalpostId").toString())
            }}
            .toTopic(DAGPENGER_INNGÅENDE_JOURNALFØRING)

        return builder.build()
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = config.kafka.brokers,
            credential = KafkaCredential(config.kafka.user!!, config.kafka.password!!)
        )
    }
}

private val strings = Serdes.String()

private val packetSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())

val DAGPENGER_INNGÅENDE_JOURNALFØRING = Topic(
    "privat-dagpenger-journalpost-mottatt-v1",
    keySerde = strings,
    valueSerde = packetSerde
)