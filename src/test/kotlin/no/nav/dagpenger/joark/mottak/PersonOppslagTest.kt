package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.RegexPattern
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class PersonOppslagTest {
    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun `henter person med riktig spørring`() {
        val body = PersonOppslagTest::class.java.getResource("/test-data/example-person-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Content-type", RegexPattern("application/json"))
                    .withHeader("X-API-KEY", RegexPattern("hunter2"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient(), "hunter2")
        val person = personOppslag.hentPerson("789", BrukerType.AKTOERID)
        assertEquals("789", person.aktoerId)
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten person`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-person-error-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Content-type", RegexPattern("application/json"))
                    .withHeader("X-API-KEY", RegexPattern("hunter2"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient(), "hunter2")
        val result = runCatching { personOppslag.hentPerson("123", BrukerType.FNR) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersonOppslagException)
    }

    @Test
    fun `håndterer 400-statuskoder`() {
        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Content-type", RegexPattern("application/json"))
                    .withHeader("X-API-KEY", RegexPattern("hunter2"))
                .willReturn(
                    notFound()
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient(), "hunter2")
        val result = runCatching { personOppslag.hentPerson("123", BrukerType.FNR) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersonOppslagException)
    }
}
