package no.nav.dagpenger.joark.mottak

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.prometheus.client.Counter
import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.BrukerType
import no.nav.dagpenger.events.avro.Søker
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import no.nav.dagpenger.streams.configureAvroSerde
import no.nav.dagpenger.streams.configureGenericAvroSerde
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.streamConfig
import no.nav.dagpenger.streams.toTopic
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.ValueMapper
import java.io.IOException

import java.lang.IllegalArgumentException
import java.net.ServerSocket

import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JoarkMottak(val env: Environment, private val journalpostArkiv: JournalpostArkiv) : Service() {
    override val SERVICE_APP_ID = "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    private val jpCounter: Counter = Counter.build()
            .name("journalpost_mottatt")
            .labelNames("tema")
            .help("Antall journalposter mottatt - med tema").register()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = Environment()
            val journalpostArkiv: JournalpostArkiv = JournalPostArkivHttpClient(env.journalfoerinngaaendeV1Url, StsOidcClient(env.oicdStsUrl, env.username, env.password))
            val service = JoarkMottak(env, journalpostArkiv)
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }
        val builder = StreamsBuilder()

        val inngåendeJournalposter = builder.consumeGenericTopic(
                JOARK_EVENTS.copy(
                        name = if (env.fasitEnvironmentName == "p") JOARK_EVENTS.name else JOARK_EVENTS.name + env.fasitEnvironmentName,
                        valueSerde = configureGenericAvroSerde(
                                mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl)
                        )
                ))

        inngåendeJournalposter
                .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
                .mapValues(ValueMapper<GenericRecord, Behov> {
                    hentInngåendeJournalpost(it.get("journalpostId").toString())
                })
                .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
                .toTopic(INNGÅENDE_JOURNALPOST.copy(
                        valueSerde = configureAvroSerde<Behov>(
                                mapOf(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl,
                                        KafkaAvroDeserializerConfig.AUTO_REGISTER_SCHEMAS to true,
                                        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true
                                        )
                                )
                        )
                )

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(appId = SERVICE_APP_ID, bootStapServerUrl = env.bootstrapServersUrl, credential = KafkaCredential(env.username, env.password))
    }

    private fun hentInngåendeJournalpost(inngåendeJournalpostId: String): Behov {
        val journalpost = journalpostArkiv.hentInngåendeJournalpost(inngåendeJournalpostId)
        jpCounter.labels(journalpost.tema).inc()
        return mapToInngåendeJournalpost(journalpost)
    }

    private fun mapToInngåendeJournalpost(inngåendeJournalpost: Journalpost): Behov =
            Behov.newBuilder().apply {
                journalpost = no.nav.dagpenger.events.avro.Journalpost.newBuilder().apply {
                    tema = inngåendeJournalpost.tema
                    dokumentListe = mapToDokumentList(inngåendeJournalpost)
                    søker = mapToSøker(inngåendeJournalpost.brukerListe)
                }.build()
            }.build()

    private fun mapToDokumentList(inngåendeJournalpost: Journalpost): List<no.nav.dagpenger.events.avro.Dokument>? {
        return inngåendeJournalpost.dokumentListe.asSequence().map {
            no.nav.dagpenger.events.avro.Dokument.newBuilder().apply {
                dokumentId = it.dokumentId
                navSkjemaId = it.navSkjemaId
            }.build()
        }.toList()
    }

    private fun mapToSøker(brukerListe: List<Bruker>): Søker? {
        return when {
            brukerListe.size > 1 -> throw IllegalArgumentException("BrukerListe has more than one element")
            brukerListe[0].brukerType == BrukerType.PERSON -> Søker(brukerListe[0].identifikator)
            else -> null
        }
    }
}
