package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging

import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.Topics.JOARK_EVENTS
import no.nav.dagpenger.streams.consumeGenericTopic
import no.nav.dagpenger.streams.toTopic
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.streams.kstream.ValueMapper

private val LOGGER = KotlinLogging.logger {}

class JoarkMottak(private val journalpostArkiv: JournalpostArkiv) : Service() {
    override val SERVICE_APP_ID = "dagpenger-joark-mottak" // NB: also used as group.id for the consumer group - do not change!
    override val HTTP_PORT: Int = 8080

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JoarkMottak(JournalpostArkivDummy())
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeGenericTopic(JOARK_EVENTS)

        inngåendeJournalposter
                .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
                .mapValues(ValueMapper<GenericRecord, Behov> {
                    hentInngåendeJournalpost(it.get("journalpostId").toString())
                })
                .peek { key, value -> LOGGER.info("Producing ${value.javaClass} with key $key") }
                .toTopic(INNGÅENDE_JOURNALPOST)

        return KafkaStreams(builder.build(), this.getConfig())
    }

    private fun hentInngåendeJournalpost(inngåendeJournalpostId: String): Behov {
        val journalpost = journalpostArkiv.hentInngåendeJournalpost(inngåendeJournalpostId)
        return mapToInngåendeJournalpost(journalpost)
    }

    private fun mapToInngåendeJournalpost(journalpost: JournalPost?): Behov =
            Behov.newBuilder().apply {
                journalPost = no.nav.dagpenger.events.avro.JournalPost.newBuilder().apply {
                    tema = journalpost?.tema
                    dokumentListe = mapToDokumentList(journalpost)
                }.build()
            }.build()

    private fun mapToDokumentList(journalpost: JournalPost?): List<no.nav.dagpenger.events.avro.Dokument>? {
        return journalpost?.dokumentListe?.map {
            no.nav.dagpenger.events.avro.Dokument.newBuilder().apply {
                dokumentId = it.dokumentId
                navSkjemaId = it.navSkjemaId
            }.build()
        }?.toList()
    }
}
