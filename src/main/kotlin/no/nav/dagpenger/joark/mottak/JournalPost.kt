package no.nav.dagpenger.joark.mottak

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class JournalPost(
    val journalTilstand: JournalTilstand,
    val avsender: Avsender,
    val brukerListe: List<Bruker>,
    val arkivSak: ArkivSak,
    val tema: String,
    val tittel: String,
    val kanalReferanseId: String,
    val forsendelseMottatt: ZonedDateTime,
    val mottaksKanal: String,
    val journalfEnhet: String,
    val dokumentListe: List<Dokument>
)

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

object JournalPostParser {

    private val zonedDateTimeConverter = object : Converter {
        override fun canConvert(cls: Class<*>) = cls == ZonedDateTime::class.java

        override fun fromJson(jv: JsonValue) =
                if (jv.string != null) {
                    LocalDateTime.parse(jv.string, DateTimeFormatter.ISO_DATE_TIME).atZone(ZoneId.of("UTC"))
                } else {
                    throw KlaxonException("Couldn't parse date: ${jv.string}")
                }

        override fun toJson(value: Any) = TODO("not implemented")
    }

    fun parse(stream: InputStream): JournalPost? =
            Klaxon().converter(zonedDateTimeConverter).parse<JournalPost>(stream)
}
