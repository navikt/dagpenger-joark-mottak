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

class DokumentInfo(tittel: String?, dokumentInfoId: String, brevkode: String?) {
    val tittel = tittel
        get() = field ?: tittelForBrevkode.getOrDefault(brevkode, "Ukjent dokumenttittel")
    val dokumentInfoId = dokumentInfoId
    val brevkode = brevkode
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

val tittelForBrevkode = mapOf(
    "NAV 04-02.03" to "Bekreftelse på ansettelsesforhold",
    "O2" to "Arbeidsavtale",
    "M6" to "Timelister",
    "M7" to " Brev fra bobestyrer/konkursforvalter",
    "NAV 04-02.05" to "Søknad om attest PD U1/N-301 til bruk ved overføring av dagpengerettigheter",
    "NAV 04-08.03" to "Bekreftelse på sluttårsak/nedsatt arbeidstid (ikke permittert)",
    "S7" to "Kopi av arbeidsavtale/sluttårsak",
    "NAV 04-08.04" to "Bekreftelse på arbeidsforhold og permittering",
    "T3" to "Tjenestebevis",
    "S6" to "Dokumentasjon av sluttårsak",
    "NAV 04-16.04" to "Søknad om gjenopptak av dagpenger ved permittering",
    "NAV 04-02.01" to "Søknad om utstedelse av attest PD U2",
    "O9" to "Bekreftelse fra studiested/skole",
    "NAV 04-06.05" to "Søknad om godkjenning av utdanning med rett til dagpenger",
    "N2" to "Kopi av søknad",
    "N5" to "Kopi av undersøkelsesresultat",
    "NAV 04-06.08" to "Søknad om dagpenger under etablering av egen virksomhet (utviklingsfase)",
    "NAV 04-16.03" to "Søknad om gjenopptak av dagpenger",
    "NAV 04-13.01" to "Egenerklæringsskjema for fastsettelse av grensearbeiderstatus",
    "NAV 04-03.07" to "Egenerklæring - overdragelse av lønnskrav",
    "X8" to "Fødselsattest/bostedsbevis for barn under 18 år",
    "T5" to "SED U006 Familieinformasjon",
    "T4" to "Oppholds- og arbeidstillatelse, eller registreringsbevis for EØS-borgere",
    "T2" to "Dokumentasjon av sluttdato",
    "T1" to "Elevdokumentasjon fra lærested",
    "V6" to "Kopi av sluttavtale",
    "U1" to "U1 Perioder av betydning for retten til dagpenger",
    "NAV 04-03.08" to "Oversikt over arbeidstimer",
    "S8" to "Sjøfartsbok/hyreavregning",
    "NAV 04-01.03" to "Søknad om dagpenger (ikke permittert)",
    "NAV 04-01.04" to "Søknad om dagpenger ved permittering"
)
