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

class JournalpostArkivJoarkTest {

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
    fun `henter Journalpost med riktig spørring`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .withHeader("Content-type", RegexPattern("application/json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        val journalPost = joarkClient.hentInngåendeJournalpost("1")
        assertEquals("MASKERT_FELT", journalPost.tittel)
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten Journalpost`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-error-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .withHeader("Content-type", RegexPattern("application/json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        val result = runCatching { joarkClient.hentInngåendeJournalpost("2") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is JournalpostArkivException)
    }

    @Test
    fun `håndterer 4xx-feil`() {

        stubFor(
            post(urlEqualTo("/"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .willReturn(
                    notFound()
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())

        val result = runCatching { joarkClient.hentInngåendeJournalpost("-1") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is JournalpostArkivException)
    }
}
