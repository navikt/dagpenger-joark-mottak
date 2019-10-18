package no.nav.dagpenger.joark.mottak

object HenvendelsesTypeMapper {
    private val allKnownTypes = listOf(
        "NAV 04-02.03",
        "O2",
        "M6",
        "M7",
        "NAV 04-02.05",
        "NAV 04-08.03",
        "S7",
        "NAV 04-08.04",
        "T3",
        "S6",
        "NAV 04-16.04",
        "NAV 04-02.01",
        "O9",
        "NAV 04-06.05",
        "N2",
        "N5",
        "NAV 04-06.08",
        "NAV 04-16.03",
        "NAV 04-13.01",
        "NAV 04-03.07",
        "X8",
        "T5",
        "T4",
        "T2",
        "T1",
        "V6",
        "U1",
        "NAV 04-03.08",
        "S8",
        "NAV 04-01.03", // NAVe 04-01.03
        "NAV 04-01.04" // NAVe 04-01.04
    )

    private val supportedTypes = mapOf(
        "NAV 04-01.03" to Henvendelsestype.NY_SØKNAD,
        "NAV 04-01.04" to Henvendelsestype.NY_SØKNAD
    )

    fun getHenvendelsesType(navSkjemaId: String?): Henvendelsestype {
        return supportedTypes.getOrDefault(navSkjemaId.orEmpty(), Henvendelsestype.ANNET)
    }

    fun isKnownSkjemaId(navSkjemaId: String): Boolean {
        return allKnownTypes.contains(navSkjemaId)
    }
}

enum class Henvendelsestype {
    NY_SØKNAD, ANNET
}