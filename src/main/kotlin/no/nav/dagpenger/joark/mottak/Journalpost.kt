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
    val dokumenter: List<DokumentInfo>
) {
    fun mapToHenvendelsesType(): Henvendelsestype {
        return HenvendelsesTypeMapper.getHenvendelsesType(this.dokumenter.first().brevkode)
    }
}

data class DokumentInfo(
    val tittel: String,
    val dokumentInfoId: String,
    val brevkode: String?
)

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
