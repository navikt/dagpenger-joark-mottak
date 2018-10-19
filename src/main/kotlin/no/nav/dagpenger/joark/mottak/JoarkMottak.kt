package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.oidc.StsOidcClient
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
import java.lang.System.getenv
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

private val username: String? = getenv("SRVDAGPENGER_JOARK_MOTTAK_USERNAME")
private val password: String? = getenv("SRVDAGPENGER_JOARK_MOTTAK_PASSWORD")
private val oicdStsUrl: String? = getenv("OIDC_STS_ISSUERURL")
private val journalfoerinngaaendeV1Url: String? = getenv("JOURNALFOERINNGAAENDE_V1_URL")

class JoarkMottak(private val journalpostArkiv: JournalpostArkiv) : Service() {
    override val SERVICE_APP_ID = "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val journalpostArkiv: JournalpostArkiv = oicdStsUrl?.let { stsUrl -> journalfoerinngaaendeV1Url?.let { journalPostUrl -> JournalPostArkivHttpClient(journalPostUrl, StsOidcClient(stsUrl, username!!, password!!)) } } ?: JournalpostArkivDummy()
            val service = JoarkMottak(journalpostArkiv)
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }
        if (password != null) {
            LOGGER.info { "Using username $username and password ${password.substring(0, password.length - 5) + "*****"}" }
        }
        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeGenericTopic(JOARK_EVENTS.copy(name = "aapen-dok-journalfoering-v1-t6"))

        inngåendeJournalposter
                .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
                .mapValues(ValueMapper<GenericRecord, Behov> {
                    hentInngåendeJournalpost(it.get("journalpostId").toString())
                })
                .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
                .toTopic(INNGÅENDE_JOURNALPOST)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    override fun getConfig(): Properties {
        return streamConfig(appId = SERVICE_APP_ID, username = username, password = password)
    }

    private fun hentInngåendeJournalpost(inngåendeJournalpostId: String): Behov {
        val journalpost = journalpostArkiv.hentInngåendeJournalpost(inngåendeJournalpostId)
        return mapToInngåendeJournalpost(journalpost)
    }

    private fun mapToInngåendeJournalpost(inngåendeJournalpost: Journalpost): Behov =
            Behov.newBuilder().apply {
                journalpost = no.nav.dagpenger.events.avro.Journalpost.newBuilder().apply {
                    tema = inngåendeJournalpost.tema
                    dokumentListe = mapToDokumentList(inngåendeJournalpost)
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
}
