package no.nav.dagpenger.joark.mottak

data class GraphQlJournalpostResponse(val data: Data, val errors: List<String>?)

data class Data(val journalpost: Journalpost)

data class Journalpost(
    val journalstatus: Journalstatus?,
    val journalpostId: String,
    val bruker: Bruker?,
    val tittel: String?,
    val datoOpprettet: String,
    val kanalnavn: String?,
    val journalforendeEnhet: String?,
    val dokumenter: List<DokumentInfo>
) {
    fun mapToHenvendelsesType(): Henvendelsestype {
        val brevkode = NavSkjemaIdMapper.getNavSkjemaId(this.dokumenter.first().brevkode)
        return HenvendelsesTypeMapper.getHenvendelsesType(brevkode)
    }
}

data class DokumentInfo(
    val dokumentInfoId: String,
    val brevkode: String
)

data class Bruker(
    val type: BrukerType,
    val id: String
)

enum class BrukerType {
    ORGNR, AKTOERID, FNR
}

enum class Journalstatus {
    MOTTATT, JOURNALFOERT, FERDIGSTILT, EKSPEDERT,
    UNDER_ARBEID, FEILREGISTRERT, UTGAAR, AVBRUTT,
    UKJENT_BRUKER, RESERVERT, OPPLASTING_DOKUMENT,
    UKJENT
}
