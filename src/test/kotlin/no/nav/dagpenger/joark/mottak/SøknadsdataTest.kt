package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class SøknadsdataTest {
    @Test
    fun `Serialize to json`() {
        Søknadsdata("""{"key": "value"}""", "jpid")
            .serialize() shouldBe """{"key":"value","journalpostId":"jpid"}"""

        emptySøknadsdata.serialize() shouldBe "{}"
    }
}
