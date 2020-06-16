package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.HealthCheck

interface JournalpostArkiv : HealthCheck {
    fun hentInngåendeJournalpost(journalpostId: String): Journalpost
    fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata
}

data class Søknadsdata(val data: String, val journalpostId: String) {
    fun serialize() = when (this == emptySøknadsdata) {
        true -> "{}"
        false -> merge(mapOf("journalpostId" to journalpostId), data)
    }
}

val emptySøknadsdata = Søknadsdata("", "")
