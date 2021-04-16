package no.nav.dagpenger.joark.mottak

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.dagpenger.streams.HealthCheck

interface JournalpostArkiv : HealthCheck {
    fun hentInngåendeJournalpost(journalpostId: String): Journalpost
    fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata?
}

data class Søknadsdata(
    val data: String,
    val journalpostId: String,
    val registrertDato: String?
) {
    fun serialize() = when (this == emptySøknadsdata) {
        true -> "{}"
        false -> merge(mapOf("journalpostId" to journalpostId, "journalRegistrertDato" to registrertDato), data)
    }

    fun toMap(): Map<String, Any?> =
        when (this == emptySøknadsdata) {
            true -> emptyMap()
            false ->
                jacksonJsonAdapter.readValue(data, object : TypeReference<Map<String, Any?>>() {})
                    .toMap() + mapOf("journalpostId" to journalpostId, "journalRegistrertDato" to registrertDato)
        }
}

val emptySøknadsdata = Søknadsdata("", "", null)
