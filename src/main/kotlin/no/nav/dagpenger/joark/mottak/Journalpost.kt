package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.HenvendelsesType
import no.nav.dagpenger.events.avro.Mottaker
import java.util.UUID

data class Journalpost(
    val journalTilstand: JournalTilstand,
    val avsender: Avsender,
    val brukerListe: List<Bruker>,
    val arkivSak: ArkivSak,
    val tema: String,
    val tittel: String,
    val kanalReferanseId: String,
    val forsendelseMottatt: String,
    val mottaksKanal: String,
    val journalfEnhet: String,
    val dokumentListe: List<Dokument>
) {

    fun toBehov(id: String): Behov {
        return Behov.newBuilder().apply {
            behovId = UUID.randomUUID().toString()
            trengerManuellBehandling = false
            henvendelsesType = HenvendelsesType.newBuilder().apply {
                annet = Annet()
            }.build()
            journalpost = no.nav.dagpenger.events.avro.Journalpost.newBuilder().apply {
                journalpostId = id
                dokumentListe = this@Journalpost.dokumentListe.asSequence().map {
                    no.nav.dagpenger.events.avro.Dokument.newBuilder().apply {
                        dokumentId = it.dokumentId
                        navSkjemaId = it.navSkjemaId
                    }.build()
                }.toList()
                mottaker = mapToMottaker(this@Journalpost.brukerListe)
            }.build()
        }.build()
    }

    private fun mapToMottaker(brukerListe: List<Bruker>): Mottaker? {
        if (brukerListe.size > 1) {
            throw IllegalArgumentException("BrukerListe has more than one element")
        }
        return Mottaker(brukerListe.firstOrNull().takeIf { it?.brukerType == BrukerType.PERSON }?.identifikator)
    }
}

data class Dokument(
    val dokumentId: String,
    val dokumentTypeId: String,
    val navSkjemaId: String,
    val tittel: String,
    val dokumentKategori: String,
    val variant: List<Variant>,
    val logiskVedleggListe: List<LogiskVedlegg>
)

data class LogiskVedlegg(
    val logiskVedleggId: String,
    val logiskVedleggTittel: String
)

data class Variant(
    val arkivFilType: String,
    val variantFormat: String
)

data class ArkivSak(
    val arkivSakSystem: String,
    val arkivSakId: String
)

data class Bruker(
    val brukerType: BrukerType,
    val identifikator: String
)

enum class BrukerType {
    PERSON, ORGANISASJON
}

enum class JournalTilstand {
    ENDELIG, MIDLERTIDIG, UTGAAR
}

data class Avsender(
    val navn: String,
    val avsenderType: AvsenderType,
    val identifikator: String
)

enum class AvsenderType {
    PERSON, ORGANISASJON
}
