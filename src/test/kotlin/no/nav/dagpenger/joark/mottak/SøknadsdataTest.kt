package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class SøknadsdataTest {
    @Test
    fun `Serialize to json`() {
        Søknadsdata(
            """{"key": "value"}""",
            "jpid",
            "2020-06-19")
            .serialize() shouldBe """{"key":"value","journalpostId":"jpid","journalRegistrertDato":"2020-06-19"}"""

        emptySøknadsdata.serialize() shouldBe "{}"
    }
}
