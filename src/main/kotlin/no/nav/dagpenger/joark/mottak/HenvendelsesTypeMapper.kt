package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad

class HenvendelsesTypeMapper {

    object mapper {

        private val typeMap = mapOf(
            "NAV 04-01.03" to Søknad(),
            "NAV 04-01.04" to Søknad(),
            "NAV 04-16.03" to Søknad(),
            "NAV 04-16.04" to Søknad(),
            "NAVe 04-01.03" to Ettersending(),
            "NAVe 04-01.04" to Ettersending(),
            "NAVe 04-16.03" to Ettersending(),
            "NAVe 04-16.04" to Ettersending()
        )

        fun getHenvendelsesType(navSkjemaId: String): Any {
        return typeMap.getOrDefault(navSkjemaId, Annet())
    } }
}