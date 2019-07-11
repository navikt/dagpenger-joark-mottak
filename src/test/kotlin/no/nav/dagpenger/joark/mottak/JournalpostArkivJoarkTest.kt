package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.RegexPattern
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.oidc.OidcToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
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

    class DummyOidcClient : OidcClient {
        override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
    }

    @Test
    fun `fetch JournalPost on 200 ok`() {

        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-payload.json")
            .readText()
        stubFor(
            get(urlEqualTo("/rest/journalfoerinngaaende/v1/journalposter/1"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalpostArkivJoark(server.url(""), DummyOidcClient())
        val journalPost = joarkClient.hentInngåendeJournalpost("1")

        assertEquals(journalPost.journalTilstand, JournalTilstand.ENDELIG)
        assertEquals(
            journalPost.avsender,
            Avsender(navn = "string", avsenderType = AvsenderType.PERSON, identifikator = "string")
        )
        assertEquals(journalPost.brukerListe, listOf(Bruker(brukerType = BrukerType.PERSON, identifikator = "string")))
        assertEquals(journalPost.arkivSak, ArkivSak(arkivSakSystem = "string", arkivSakId = "string"))
        assertEquals(journalPost.tema, "string")
        assertEquals(journalPost.tittel, "string")
        assertEquals(journalPost.kanalReferanseId, "string")
        assertEquals(journalPost.forsendelseMottatt, "2018-09-25T11:21:11.387Z")
        assertEquals(journalPost.mottaksKanal, "string")
        assertEquals(journalPost.journalfEnhet, "string")
        assertEquals(
            journalPost.dokumentListe, listOf(
                Dokument(
                    dokumentId = "string",
                    dokumentTypeId = "string",
                    navSkjemaId = "string",
                    tittel = "string",
                    dokumentKategori = "string",
                    variant = listOf(Variant(arkivFilType = "string", variantFormat = "string")),
                    logiskVedleggListe = listOf(
                        LogiskVedlegg(
                            logiskVedleggId = "string",
                            logiskVedleggTittel = "string"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `fetch JournalPost on 200 ok but no content`() {

        val body = ""
        stubFor(
            get(urlEqualTo("/rest/journalfoerinngaaende/v1/journalposter/2"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
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
    fun `fetch JournalPost on 4xx errors`() {

        stubFor(
            get(urlEqualTo("/rest/journalfoerinngaaende/v1/journalposter/-1"))
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