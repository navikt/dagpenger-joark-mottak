package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad

class HenvendelsesTypeMapper {

    object mapper {

        private val allKnownTypes = listOf(
            "196002",
            "273034",
            "200899",
            "221659",
            "596002",
            "673034",
            "600899",
            "621659",
            "196008",
            "196076",
            "196216",
            "196256",
            "196862",
            "197391",
            "197897",
            "198442",
            "200000",
            "200365",
            "219312",
            "219521",
            "219569",
            "233862",
            "235955",
            "596008",
            "596076",
            "596256",
            "597897",
            "598442",
            "270440",
            "670440",
            "276058",
            "596862",
            "597391",
            "600365",
            "676058",
            "287483",
            "687483",
            "600000",
            "263651",
            "298170",
            "698170",
            "231823"
        )

        private val supportedTypes = mapOf(
            "196002" to Søknad(),
            "273034" to Søknad(),
            "200899" to Søknad(),
            "221659" to Søknad(),
            "596002" to Ettersending(),
            "673034" to Ettersending(),
            "600899" to Ettersending(),
            "621659" to Ettersending()
        )

        fun getHenvendelsesType(navSkjemaId: String?): Any {
            return supportedTypes.getOrDefault(navSkjemaId.orEmpty(), Annet())
        }

        fun isKnownSkjemaId(navSkjemaId: String): Boolean {
            return allKnownTypes.contains(navSkjemaId)
        }
    }
}
