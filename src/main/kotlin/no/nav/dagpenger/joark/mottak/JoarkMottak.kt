package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.metrics.aCounter
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.ValueMapper
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JoarkMottak(val env: Environment, private val journalpostArkiv: JournalpostArkiv) : Service() {
    override val SERVICE_APP_ID =
        "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    private val jpCounter = aCounter(
        name = "journalpost_received",
        labelNames = listOf(
            "skjemaId",
            "henvendelsesType",
            "mottaksKanal",
            "hasJournalfEnhet",
            "numberOfDocuments",
            "numberOfBrukere",
            "brukerType",
            "hasIdentifikator",
            "journalTilstand",
            "hasKanalReferanseId"
        ),
        help = "Number of Journalposts received on tema DAG"
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = Environment()
            val journalpostArkiv: JournalpostArkiv = JournalPostArkivHttpClient(
                env.journalfoerinngaaendeV1Url,
                StsOidcClient(env.oicdStsUrl, env.username, env.password)
            )
            val service = JoarkMottak(env, journalpostArkiv)
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }
        val builder = StreamsBuilder()

        val inngåendeJournalposter = builder.consumeGenericTopic(
            JOARK_EVENTS.copy(
                name = if (env.fasitEnvironmentName.isBlank()) JOARK_EVENTS.name else JOARK_EVENTS.name + "-" + env.fasitEnvironmentName
            ), env.schemaRegistryUrl
        )

        inngåendeJournalposter
            .peek { _, value ->
                LOGGER.info(
                    "Received journalpost with journalpost id: ${value.get("journalpostId")} and tema: ${value.get(
                        "temaNytt"
                    )}"
                )
            }
            .filter { _, journalpostHendelse -> "DAG" == journalpostHendelse.get("temaNytt").toString() }
            .mapValues(ValueMapper<GenericRecord, Behov> {
                hentInngåendeJournalpost(it.get("journalpostId").toString())
            })
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
            .toTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }

    private fun hentInngåendeJournalpost(journalpostId: String): Behov {
        val journalpost = journalpostArkiv.hentInngåendeJournalpost(journalpostId)
        registerMetrics(journalpost)
        return journalpost.toBehov(journalpostId)
    }

    private fun registerMetrics(journalpost: Journalpost) {
        val skjemaId = journalpost.dokumentListe.firstOrNull()?.navSkjemaId ?: "unknown"

        jpCounter
            .labels(
                skjemaId,
                HenvendelsesTypeMapper.mapper.getHenvendelsesType(skjemaId).toString(),
                journalpost.mottaksKanal,
                if (journalpost.journalfEnhet.isBlank()) "true" else "false",
                journalpost.dokumentListe.size.toString(),
                journalpost.brukerListe.size.toString(),
                journalpost.brukerListe.takeIf { it.size == 1 }?.firstOrNull()?.brukerType?.toString() ?: "notSingleBruker",
                journalpost.brukerListe.firstOrNull()?.identifikator?.let { "true" } ?: "false",
                journalpost.journalTilstand.toString(),
                if (journalpost.kanalReferanseId.isBlank()) "true" else "false"
            )
            .inc()
    }
}
