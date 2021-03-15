package no.nav.dagpenger.joark.mottak

data class GraphQlJournalpostResponse(val data: Data, val errors: List<String>?) {
    class Data(val journalpost: Journalpost)
}

data class Journalpost(
    val journalstatus: Journalstatus?,
    val journalpostId: String,
    val bruker: Bruker?,
    val tittel: String?,
    val datoOpprettet: String?,
    val kanal: String?,
    val kanalnavn: String?,
    val journalforendeEnhet: String?,
    val relevanteDatoer: List<RelevantDato>,
    val dokumenter: List<DokumentInfo>,
    val behandlingstema: String? = null
) {
    val henvendelsestype: Henvendelsestype
        get() = HenvendelsesTypeMapper.getHenvendelsesType(this.dokumenter.first().brevkode)
}

internal fun Journalpost.registrertDato(): String? = this.relevanteDatoer.find { dato -> dato.datotype == Datotype.DATO_REGISTRERT }?.dato

class DokumentInfo(tittel: String?, dokumentInfoId: String, brevkode: String?) {
    val tittel = tittel
        get() = field ?: HenvendelsesTypeMapper.allKnownTypes.getOrDefault(brevkode, "Ukjent dokumenttittel")
    val dokumentInfoId = dokumentInfoId
    val brevkode = brevkode

    override fun toString(): String {
        return "DokumentInfo(tittel=$tittel, dokumentInfoId=$dokumentInfoId, brevkode=$brevkode)"
    }
}

data class Bruker(
    val type: BrukerType,
    val id: String
) {
    override fun toString(): String {
        return "Bruker(type=$type, id='<REDACTED>')"
    }
}

enum class BrukerType {
    ORGNR, AKTOERID, FNR
}

data class RelevantDato(
    val dato: String,
    val datotype: Datotype
)

enum class Datotype {
    DATO_SENDT_PRINT, DATO_EKSPEDERT, DATO_JOURNALFOERT,
    DATO_REGISTRERT, DATO_AVS_RETUR, DATO_DOKUMENT
}

enum class Journalstatus {
    MOTTATT, JOURNALFOERT, FERDIGSTILT, EKSPEDERT,
    UNDER_ARBEID, FEILREGISTRERT, UTGAAR, AVBRUTT,
    UKJENT_BRUKER, RESERVERT, OPPLASTING_DOKUMENT,
    UKJENT
}
