package no.nav.dagpenger.joark.mottak

class JournalpostArkivDummy : JournalpostArkiv {
    override fun hentInngåendeJournalpost(journalpostId: String): String {
        return "bar"
    }
}