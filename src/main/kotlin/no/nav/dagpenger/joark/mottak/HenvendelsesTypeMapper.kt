package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad

object HenvendelsesTypeMapper {
    private val allKnownTypes = listOf(
        "NAV 04-01.03",
        "NAV 04-01.04",
        "NAV 04-16.03",
        "NAV 04-16.04",
        "NAVe 04-01.03",
        "NAVe 04-01.04",
        "NAVe 04-16.03",
        "NAVe 04-16.04",
        "NAV 04-01.05",
        "NAV 04-02.01",
        "NAV 04-02.03",
        "NAV 04-02.05",
        "NAV 04-03.07",
        "NAV 04-06.05",
        "NAV 04-06.08",
        "NAV 04-06.09",
        "NAV 04-06.10",
        "NAV 04-08.03",
        "NAV 04-05.03",
        "NAV 04-05.05",
        "NAV 04-05.07",
        "NAV 04-02.10",
        "NAV 04-05.09",
        "NAVe 04-01.05",
        "NAVe 04-02.01",
        "NAVe 04-02.05",
        "NAVe 04-06.08",
        "NAVe 04-06.09",
        "NAV 04-03.03",
        "NAVe 04-03.03",
        "NAV 04-08.04",
        "NAVe 04-03.07",
        "NAVe 04-06.05",
        "NAVe 04-08.03",
        "NAVe 04-08.04",
        "NAV 04-03.08",
        "NAVe 04-03.08",
        "NAVe 04-06.10",
        "NAV 04-13.01",
        "NAV 04-02.02",
        "NAVe 04-02.02",
        "NAV 90-00.08")

    private val supportedTypes = mapOf(
        "NAV 04-01.03" to Søknad(),
        "NAV 04-01.04" to Søknad(),
        "NAV 04-16.03" to Søknad(),
        "NAV 04-16.04" to Søknad(),
        "NAVe 04-01.03" to Ettersending(),
        "NAVe 04-01.04" to Ettersending(),
        "NAVe 04-16.03" to Ettersending(),
        "NAVe 04-16.04" to Ettersending()
    )

    fun getHenvendelsesType(navSkjemaId: String?): Any {
        return supportedTypes.getOrDefault(navSkjemaId.orEmpty(), Annet())
    }

    fun isKnownSkjemaId(navSkjemaId: String): Boolean {
        return allKnownTypes.contains(navSkjemaId)
    }
}

data class Henvendelse(val netsId: String, val skjemaId: String, val navn: String, val type: Søknad)