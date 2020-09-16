package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.streams.HealthCheck

interface JournalpostArkiv : HealthCheck {
    fun hentInngåendeJournalpost(journalpostId: String): Journalpost
    fun hentSøknadsdata(journalpost: Journalpost): Søknadsdata
    fun hentSøknadsdataV2(journalpost: Journalpost): Pair<Journalpost, Søknadsdata?>
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

    fun toMap(): Map<String, Any?>? {
        return jsonMapAdapter.fromJson(data)?.toMap()?.let {
            it + mapOf("journalpostId" to journalpostId, "journalRegistrertDato" to registrertDato)
        }
    }
}

val emptySøknadsdata = Søknadsdata("", "", null)
