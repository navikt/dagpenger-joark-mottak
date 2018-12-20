package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.HenvendelsesType
import no.nav.dagpenger.events.avro.Mottaker
import no.nav.dagpenger.events.avro.Søknad
import java.util.UUID

data class Journalpost(
    val journalTilstand: JournalTilstand,
    val avsender: Avsender,
    val brukerListe: List<Bruker>,
    val arkivSak: ArkivSak,
    val tema: String,
    val tittel: String,
    val kanalReferanseId: String?,
    val forsendelseMottatt: String,
    val mottaksKanal: String?,
    val journalfEnhet: String?,
    val dokumentListe: List<Dokument>
) {

    fun toBehov(id: String): Behov {
        return Behov.newBuilder().apply {
            behovId = UUID.randomUUID().toString()
            trengerManuellBehandling = false
            henvendelsesType = mapJournalpostTilHenvendelsesType().build()
            journalpost = no.nav.dagpenger.events.avro.Journalpost.newBuilder().apply {
                journalpostId = id
                dokumentListe = this@Journalpost.dokumentListe.asSequence().map {
                    no.nav.dagpenger.events.avro.Dokument.newBuilder().apply {
                        dokumentId = it.dokumentId
                        navSkjemaId = it.navSkjemaId
                    }.build()
                }.toList()
                mottaker = mapToMottaker()
            }.build()
        }.build()
    }

    private fun mapToMottaker(): Mottaker? {
        val brukerListe = this@Journalpost.brukerListe
        if (brukerListe.size > 1) {
            throw IllegalArgumentException("BrukerListe has more than one element")
        }
        val identifikator = brukerListe.firstOrNull().takeIf { it?.brukerType == BrukerType.PERSON }?.identifikator
        return identifikator?.let { Mottaker(it) }
    }

    private fun mapToHenvendelsesType(): Any {
        val navSkjemaId = this@Journalpost.dokumentListe.first().navSkjemaId
        return HenvendelsesTypeMapper.getHenvendelsesType(navSkjemaId)
    }

    private fun mapJournalpostTilHenvendelsesType(): HenvendelsesType.Builder {
        val builder = HenvendelsesType.newBuilder()
        val type = mapToHenvendelsesType()
        when (type) {
            Søknad() -> builder.søknad = type as Søknad?
            Ettersending() -> builder.ettersending = type as Ettersending?
            Annet() -> builder.annet = type as Annet?
        }
        return builder
    }
}

data class Dokument(
    val dokumentId: String,
    val dokumentTypeId: String,
    val navSkjemaId: String?,
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
    val identifikator: String?
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
