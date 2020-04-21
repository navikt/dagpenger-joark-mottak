package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.HealthStatus

class DummyJournalpostArkiv() : JournalpostArkiv {
    override fun status(): HealthStatus {
        return HealthStatus.UP
    }
    override fun hentInngåendeJournalpost(journalpostId: String): Journalpost {
        return Journalpost(
            journalstatus = Journalstatus.MOTTATT,
            journalpostId = "123",
            bruker = Bruker(BrukerType.AKTOERID, "123"),
            tittel = "Kul tittel",
            kanal = "NAV.no",
            datoOpprettet = "2019-05-05",
            kanalnavn = "DAG",
            journalforendeEnhet = "Uvisst",
            relevanteDatoer = listOf(RelevantDato(dato = "2018-01-01T12:00:00", datotype = Datotype.DATO_REGISTRERT)),
            dokumenter = listOf(DokumentInfo(tittel = "Søknad", dokumentInfoId = "9", brevkode = "NAV 04-01.04"))
        )
    }

    override fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata {
        return Søknadsdata("data")
    }
}
