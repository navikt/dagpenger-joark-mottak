package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.RegexPattern
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JournalPostArkivHttpClientTest {

    @Rule
    @JvmField
    var wireMockRule = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    class DummyOidcClient : OidcClient {
        override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
    }

    @Test
    fun `fetch JournalPost on 200 ok`() {

        val body = JournalPostArkivHttpClientTest::class.java.getResource("/test-data/example-journalpost-payload.json").readText()
        stubFor(get(urlEqualTo("/rest/journalfoerinngaaende/v1/journalposter/1"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val joarkClient = JournalPostArkivHttpClient(wireMockRule.url(""), DummyOidcClient())
        val journalPost = joarkClient.hentInngåendeJournalpost("1")

        assertNotNull(journalPost!!)
        assertEquals(journalPost.journalTilstand, JournalTilstand.ENDELIG)
        assertEquals(journalPost.avsender, Avsender(navn = "string", avsenderType = AvsenderType.PERSON, identifikator = "string"))
        assertEquals(journalPost.brukerListe, listOf(Bruker(brukerType = BrukerType.PERSON, identifikator = "string")))
        assertEquals(journalPost.arkivSak, ArkivSak(arkivSakSystem = "string", arkivSakId = "string"))
        assertEquals(journalPost.tema, "string")
        assertEquals(journalPost.tittel, "string")
        assertEquals(journalPost.kanalReferanseId, "string")
        assertEquals(journalPost.forsendelseMottatt, "2018-09-25T11:21:11.387Z")
        assertEquals(journalPost.mottaksKanal, "string")
        assertEquals(journalPost.journalfEnhet, "string")
        assertEquals(journalPost.dokumentListe, listOf(
                Dokument(dokumentId = "string", dokumentTypeId = "string", navSkjemaId = "string", tittel = "string", dokumentKategori = "string",
                        variant = listOf(Variant(arkivFilType = "string", variantFormat = "string")),
                        logiskVedleggListe = listOf(LogiskVedlegg(logiskVedleggId = "string", logiskVedleggTittel = "string")))))
    }

    @Test(expected = JournalPostArkivException::class)
    fun `fetch JournalPost on 4xx errors`() {

        stubFor(get(urlEqualTo("/rest/journalfoerinngaaende/v1/journalposter/-1"))
                .withHeader("Authorization", RegexPattern("Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}"))
                .willReturn(notFound()
                )
        )

        val joarkClient = JournalPostArkivHttpClient(wireMockRule.url(""), DummyOidcClient())
        joarkClient.hentInngåendeJournalpost("-1")
    }
}