package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.isAnnet
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isSoknad
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
import org.apache.logging.log4j.ThreadContext
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
        ),
        help = "Number of Journalposts received on tema DAG"
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = Environment()
            val journalpostArkiv: JournalpostArkiv = JournalpostArkivJoark(
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
            .flatMapValues(ValueMapper<GenericRecord, List<Behov>> {
                try {
                    listOf(hentInngåendeJournalpost(it.get("journalpostId").toString()))
                } catch (e: JournalpostArkivException) {
                    if (e.statusCode == 403) {
                        LOGGER.warn("Could not fetch journalpost", e)
                        emptyList<Behov>()
                    } else {
                        throw e
                    }
                }
            })
            .selectKey { _, behov -> behov.getJournalpost().getJournalpostId() }
            .peek { key, value -> LOGGER.info("Producing ${value.javaClass.simpleName} with key '$key' ") }
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
        ThreadContext.put("journalpostId", journalpostId)
        val journalpost = journalpostArkiv.hentInngåendeJournalpost(journalpostId)
        val behov = journalpost.toBehov(journalpostId)
        registerMetrics(journalpost, behov)
        return behov
    }

    private fun registerMetrics(journalpost: Journalpost, behov: Behov) {
        val skjemaId = journalpost.dokumentListe.firstOrNull()?.navSkjemaId ?: "unknown"
        val skjemaIdIsKnown = HenvendelsesTypeMapper.mapper.isKnownSkjemaId(skjemaId).toString()
        val henvendelsesType = when {
            behov.isSoknad() -> "Soknad"
            behov.isEttersending() -> "Ettersending"
            behov.isAnnet() -> "Annet"
            else -> "unknown"
        }
        val hasJournalfEnhet = if (journalpost.journalfEnhet.isNotBlank()) "true" else "false"
        val brukerType =
            journalpost.brukerListe.takeIf { it.size == 1 }?.firstOrNull()?.brukerType?.toString() ?: "notSingleBruker"
        val hasIdentifikator = journalpost.brukerListe.firstOrNull()?.identifikator?.let { "true" } ?: "false"
        val hasKanalreferanseId = journalpost.kanalReferanseId?.let { "true" } ?: "false"

        jpCounter
            .labels(
                skjemaId,
                skjemaIdIsKnown,
                henvendelsesType,
                journalpost.mottaksKanal?.let { it } ?: "ingen",
                hasJournalfEnhet,
                journalpost.dokumentListe.size.toString(),
                journalpost.brukerListe.size.toString(),
                brukerType,
                hasIdentifikator,
                journalpost.journalTilstand.toString(),
                hasKanalreferanseId
            )
            .inc()
    }
}
