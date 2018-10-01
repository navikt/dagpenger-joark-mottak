package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.streams.Service
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder

private val LOGGER = KotlinLogging.logger {}

class JoarkMottak(
    private val journalpostArkiv: JournalpostArkiv
) : Service() {
    override val SERVICE_APP_ID = "joark-mottak"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val service = JoarkMottak(JournalpostArkivDummy())
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        println(SERVICE_APP_ID)
        val builder = StreamsBuilder()

        return KafkaStreams(builder.build(), this.getConfig())
    }

//    private fun hentInngåendeJournalpost(inngåendeJournalpost: TynnInngåendeJournalpost): InngåendeJournalpost {
//        var journalpost = journalpostArkiv.hentInngåendeJournalpost(inngåendeJournalpost.getId())
//
//        return mapToInngåendeJournalpost(journalpost)
//    }

//    private fun mapToInngåendeJournalpost(journalpost: JournalPost?): InngåendeJournalpost =
//        InngåendeJournalpost.newBuilder().apply {
//            id = "123"
//            avsenderId = "abba-dabba"
//            forsendelseMottatt = LocalDate.now().toString()
//            tema = "DAG"
//            journalTilstand = JournalTilstand.MIDLERTIDIG
//            journalførendeEnhet = journalpost?.journalfEnhet
//        }.build()
}
