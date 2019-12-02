package no.nav.dagpenger.joark.mottak

import io.kotlintest.shouldBe
import org.junit.jupiter.api.Test

internal class DokumentInfoTest {
    @Test
    fun `gir tittel fra mapping når tittel er null og brevkode er kjent`() {
        val dokument = DokumentInfo(null, "abc", "NAV 04-01.03")

        dokument.tittel shouldBe "Søknad om dagpenger (ikke permittert)"
    }

    @Test
    fun `gir tittel ukjent når tittel er null og brevkode er null eller ukjent`() {
        val dokumentMedUkjentBrevkode = DokumentInfo(null, "abc", "Aetat 04-01.03")
        val dokumentUtenBrevkode = DokumentInfo(null, "abc", null)

        dokumentMedUkjentBrevkode.tittel shouldBe "Ukjent dokumenttittel"
        dokumentUtenBrevkode.tittel shouldBe "Ukjent dokumenttittel"
    }

    @Test
    fun `bruker tittel når den er spesifisert`() {
        val dokument = DokumentInfo("Søknad", "a", "NAV 04-01.03")

        dokument.tittel shouldBe "Søknad"
    }
}