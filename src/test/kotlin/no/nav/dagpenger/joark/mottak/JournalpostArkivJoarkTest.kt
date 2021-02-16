package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.dagpenger.streams.HealthStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
    fun `henter søknadsdata for journalpost`() {
        val journalpostId = "123"
        val dokumentId = "666"
        val url = "/rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL"

        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-søknadsdata-payload.json")
            .readText()
        stubFor(
            get(urlEqualTo(url))
                .withHeader("Authorization", EqualToPattern("Bearer hunter2"))
                .withHeader("Content-type", EqualToPattern("application/json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        joarkClient.hentSøknadsdata(
            dummyJournalpost(
                journalpostId = journalpostId,
                kanal = "NAV_NO",
                dokumenter = listOf(DokumentInfo("Søknad", dokumentId, "NAV 04-01.03"))
            )
        )

        verify(getRequestedFor(urlEqualTo(url)))
    }

    @Test
    fun `henter Journalpost med riktig spørring`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Authorization", EqualToPattern("Bearer hunter2"))
                .withHeader("Content-type", EqualToPattern("application/json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        val journalPost = joarkClient.hentInngåendeJournalpost("1")

        journalPost.tittel shouldBe "MASKERT_FELT"
        verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten Journalpost`() {
        val body =
            JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-error-payload.json")
                .readText()
        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Authorization", EqualToPattern("Bearer hunter2"))
                .withHeader("Content-type", EqualToPattern("application/json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        val result = runCatching { joarkClient.hentInngåendeJournalpost("2") }

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<JournalpostArkivException>()
        verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `håndterer 4xx-feil`() {

        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Authorization", EqualToPattern("Bearer hunter2"))
                .willReturn(
                    notFound()
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())

        val result = runCatching { joarkClient.hentInngåendeJournalpost("-1") }
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<JournalpostArkivException>()
        verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `helsestatus settes korrekt om joark er oppe`() {
        stubFor(
            get(urlEqualTo("/isAlive"))
                .willReturn(
                    ok()
                )
        )
        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        joarkClient.status() shouldBe HealthStatus.UP
        verify(getRequestedFor(urlEqualTo("/isAlive")))
    }

    @Test
    fun `helsestatus settes korrekt om joark er nede`() {
        stubFor(
            get(urlEqualTo("/isAlive"))
                .willReturn(
                    serverError()
                )
        )
        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        joarkClient.status() shouldBe HealthStatus.DOWN
        verify(getRequestedFor(urlEqualTo("/isAlive")))
    }
}
