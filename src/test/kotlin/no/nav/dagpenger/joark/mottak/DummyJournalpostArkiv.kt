package no.nav.dagpenger.joark.mottak

class DummyJournalpostArkiv() : JournalpostArkiv {
    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        return Journalpost(
            journalstatus = Journalstatus.MOTTATT,
            journalpostId = "123",
            bruker = Bruker(BrukerType.AKTOERID, "123"),
            tittel = "Kul tittel",
            datoOpprettet = "2019-05-05",
            kanalnavn = "DAG",
            journalforendeEnhet = "Uvisst",
            dokumenter = listOf(DokumentInfo(dokumentInfoId = "9", brevkode = "NAV 04-01.04"))
        )
    }
}