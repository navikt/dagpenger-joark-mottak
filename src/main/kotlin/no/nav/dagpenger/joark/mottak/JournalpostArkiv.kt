package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.HealthCheck

interface JournalpostArkiv : HealthCheck {
    fun hentInngåendeJournalpost(journalpostId: String): Journalpost
}
