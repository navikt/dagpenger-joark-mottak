package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.joark.mottak.PersonDeserializer.aktoerId
import no.nav.dagpenger.joark.mottak.PersonDeserializer.diskresjonsKode
import no.nav.dagpenger.joark.mottak.PersonDeserializer.naturligIdent
import no.nav.dagpenger.joark.mottak.PersonDeserializer.norskTilknyting
import no.nav.dagpenger.joark.mottak.PersonDeserializer.personNavn
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class PersonDeserialiseringTest {
    @Test
    fun `riktig navn`() {
        jacksonJsonAdapter.readTree(
            """{"data" :{"navn": [ { "fornavn": "LITEN", "mellomnavn": "hubba",  "etternavn": "BRANNHYDRANT" } ] }} """.trimIndent()
        ).personNavn() shouldBe "LITEN hubba BRANNHYDRANT"
    }

    @Test
    fun `Takler manglende mellom navn`() {
        jacksonJsonAdapter.readTree(
            """{ "data": {"navn": [ { "fornavn": "LITEN", "etternavn": "BRANNHYDRANT" } ] } }""".trimIndent()
        ).personNavn() shouldBe "LITEN BRANNHYDRANT"

        jacksonJsonAdapter.readTree(
            """{ "data": {"navn": [ { "fornavn": "LITEN", "mellomnavn": null,  "etternavn": "BRANNHYDRANT" } ] } }""".trimIndent()
        ).personNavn() shouldBe "LITEN BRANNHYDRANT"
    }

    @Test
    fun `riktig identer`() {
        @Language("JSON") val json = jacksonJsonAdapter.readTree(
            """{ "data": {"identer": [ { "ident": "13086824072", "gruppe": "FOLKEREGISTERIDENT" }, { "ident": "2797593735308", "gruppe": "AKTORID" } ] }} """.trimIndent()
        )

        json.aktoerId() shouldBe "2797593735308"
        json.naturligIdent() shouldBe "13086824072"
    }

    @Test
    fun `riktig norsk tilknytning`() {
        //language=JSON
        val jsonTrue =
            jacksonJsonAdapter.readTree("""{ "data": { "hentGeografiskTilknytning": { "gtLand": null } } } """.trimIndent())
        jsonTrue.norskTilknyting() shouldBe true

        val jsonFalse =
            jacksonJsonAdapter.readTree("""{ "data": { "hentGeografiskTilknytning": { "gtLand": "sdfsafd" } } } """.trimIndent())
        jsonFalse.norskTilknyting() shouldBe false
    }

    @Test
    fun `riktig diskresjonskode`() {
        //language=JSON
        val strengtFortroligJson =
            jacksonJsonAdapter.readTree("""{ "data": { "hentPerson": { "adressebeskyttelse": [ { "gradering": "STRENGT_FORTROLIG_UTLAND" } ] } } } """.trimIndent())
        strengtFortroligJson.diskresjonsKode() shouldBe "STRENGT_FORTROLIG_UTLAND"

        //language=JSON
        val ukjentGraderingJsone =
            jacksonJsonAdapter.readTree("""{ "data": { "hentPerson": { "adressebeskyttelse": [ { "gradering": null } ] } } } """.trimIndent())
        ukjentGraderingJsone.diskresjonsKode() shouldBe null

        @Language("JSON") val ingenBeskyttelseJson =
            jacksonJsonAdapter.readTree("""{ "data": { "hentPerson": { "adressebeskyttelse":[] } } } """.trimIndent())
        ingenBeskyttelseJson.diskresjonsKode() shouldBe null
    }
}
