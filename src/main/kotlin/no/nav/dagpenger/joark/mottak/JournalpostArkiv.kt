package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.streams.HealthCheck

private val logger = KotlinLogging.logger { }
private val sikkerLogger = KotlinLogging.logger("tjenestekall")

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

    fun toMap(): Map<String, Any?>? {
        return try {
            jsonMapAdapter.fromJson(data)?.toMap()?.let {
                it + mapOf("journalpostId" to journalpostId, "journalRegistrertDato" to registrertDato)
            }
        } catch (e: Exception) {
            logger.info { "Klarte ikke å lese søknad med journalpost id $journalpostId" }
            sikkerLogger.info { "Klarte ikke å lese søknad med journalpost id $journalpostId, data $data" }
            null
        }
    }
}

val emptySøknadsdata = Søknadsdata("", "", null)
