package no.nav.dagpenger.joark.mottak

object HenvendelsesTypeMapper {
    val allKnownTypes = mapOf(
        "NAV 04-02.03" to "Bekreftelse på ansettelsesforhold",
        "O2" to "Arbeidsavtale",
        "M6" to "Timelister",
        "M7" to " Brev fra bobestyrer/konkursforvalter",
        "NAV 04-02.05" to "Søknad om attest PD U1/N-301 til bruk ved overføring av dagpengerettigheter",
        "NAVe 04-02.05" to "Ettersendelse til søknad om attest PD U1/N-301 til bruk ved overføring av dagpengerettigheter",
        "NAV 04-08.03" to "Bekreftelse på sluttårsak/nedsatt arbeidstid (ikke permittert)",
        "NAVe 04-08.03" to "Ettersendelse til bekreftelse på sluttårsak/nedsatt arbeidstid (ikke permittert)",
        "S7" to "Kopi av arbeidsavtale/sluttårsak",
        "NAV 04-08.04" to "Bekreftelse på arbeidsforhold og permittering",
        "NAVe 04-08.04" to "Ettersendelse til bekreftelse på arbeidsforhold og permittering",
        "T3" to "Tjenestebevis",
        "S6" to "Dokumentasjon av sluttårsak",
        "NAV 04-16.04" to "Søknad om gjenopptak av dagpenger ved permittering",
        "NAVe 04-16.04" to "Ettersendelse til søknad om gjenopptak av dagpenger ved permittering",
        "NAV 04-02.01" to "Søknad om utstedelse av attest PD U2",
        "NAVe 04-02.01" to "Ettersendelse til søknad om attest PD U2",
        "O9" to "Bekreftelse fra studiested/skole",
        "NAV 04-06.05" to "Søknad om godkjenning av utdanning med rett til dagpenger",
        "NAVe 04-06.05" to "Ettersendelse til søknad om godkjenning av utdanning med rett til dagpenger",
        "N2" to "Kopi av søknad",
        "N5" to "Kopi av undersøkelsesresultat",
        "NAV 04-06.08" to "Søknad om dagpenger under etablering av egen virksomhet (utviklingsfase)",
        "NAVe 04-06.08" to "Ettersendelse til søknad om dagpenger under etablering av egen virksomhet (utviklingsfase)",
        "NAV 04-16.03" to "Søknad om gjenopptak av dagpenger",
        "NAVe 04-16.03" to "Ettersendelse til søknad om gjenopptak av dagpenger (ikke permittert)",
        "NAV 04-13.01" to "Egenerklæringsskjema for fastsettelse av grensearbeiderstatus",
        "NAV 04-03.07" to "Egenerklæring - overdragelse av lønnskrav",
        "NAVe 04-03.07" to "Ettersendelse til egenerklæring - overdragelse av lønnskrav ved konkurs mv",
        "X8" to "Fødselsattest/bostedsbevis for barn under 18 år",
        "T5" to "SED U006 Familieinformasjon",
        "T4" to "Oppholds- og arbeidstillatelse, eller registreringsbevis for EØS-borgere",
        "T2" to "Dokumentasjon av sluttdato",
        "T1" to "Elevdokumentasjon fra lærested",
        "V6" to "Kopi av sluttavtale",
        "U1" to "U1 Perioder av betydning for retten til dagpenger",
        "NAV 04-03.08" to "Oversikt over arbeidstimer",
        "NAVe 04-03.08" to "Ettersendelse til oversikt over arbeidstimer",
        "S8" to "Sjøfartsbok/hyreavregning",
        "NAV 04-01.03" to "Søknad om dagpenger (ikke permittert)",
        "NAVe 04-01.03" to "Ettersendelse til søknad om dagpenger ved arbeidsledighet (ikke permittert)",
        "NAV 04-01.04" to "Søknad om dagpenger ved permittering",
        "NAVe 04-01.04" to "Ettersendelse til søknad om dagpenger ved permittering",
        "NAV 90-00.08" to "Klage og anke"
    )

    private val brevkodeTilHenvendelsestype = mapOf(
        "NAV 04-01.03" to Henvendelsestype.NY_SØKNAD,
        "NAV 04-01.04" to Henvendelsestype.NY_SØKNAD,
        "NAV 04-16.03" to Henvendelsestype.GJENOPPTAK,
        "NAV 04-16.04" to Henvendelsestype.GJENOPPTAK,
        "NAV 04-06.05" to Henvendelsestype.UTDANNING,
        "NAV 04-06.08" to Henvendelsestype.ETABLERING,
        "NAV 90-00.08" to Henvendelsestype.KLAGE_ANKE
    )

    fun getHenvendelsesType(navSkjemaId: String?): Henvendelsestype {
        return brevkodeTilHenvendelsestype.getOrDefault(navSkjemaId.orEmpty(), Henvendelsestype.ANNET)
    }

    fun isKnownSkjemaId(navSkjemaId: String): Boolean {
        return allKnownTypes.contains(navSkjemaId)
    }
}

enum class Henvendelsestype {
    NY_SØKNAD,
    GJENOPPTAK,
    ANNET,
    UTDANNING,
    ETABLERING,
    KLAGE_ANKE
}
