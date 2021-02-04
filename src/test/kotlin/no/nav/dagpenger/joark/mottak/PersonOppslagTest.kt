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
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.streams.HealthStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
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
    fun `henter person med riktig spørring og headers`() {
        val body = PersonOppslagTest::class.java.getResource("/test-data/example-person-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Content-type", RegexPattern("application/json"))
                .withHeader("Authorization", RegexPattern("Bearer hunter2"))
                .withHeader("Nav-Consumer-Token", RegexPattern("Bearer hunter2"))
                .withHeader("accept", RegexPattern("application/json"))
                .withHeader("TEMA", RegexPattern("DAG"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val person = personOppslag.hentPerson("789")
        assertEquals("2797593735308", person.aktoerId)
        assertEquals("13086824072", person.naturligIdent)
        assertEquals("LITEN DRØVTYGGENDE BRANNHYDRANT", person.navn)
        assertEquals(true, person.norskTilknytning)
        assertEquals("STRENGT_FORTROLIG_UTLAND", person.diskresjonskode)
        WireMock.verify(WireMock.postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten person`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-person-error-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val result = runCatching { personOppslag.hentPerson("123") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersonOppslagException)

        WireMock.verify(WireMock.postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `håndterer 400-statuskoder`() {
        stubFor(
            post(urlEqualTo("/graphql"))
                .willReturn(
                    notFound()
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val result = runCatching { personOppslag.hentPerson("123") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PersonOppslagException)
        WireMock.verify(WireMock.postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `helsestatus settes korrekt om pdl-api er oppe`() {
        stubFor(
            WireMock.get(urlEqualTo("/internal/health/readiness"))
                .willReturn(
                    WireMock.ok()
                )
        )
        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        personOppslag.status() shouldBe HealthStatus.UP
        WireMock.verify(WireMock.getRequestedFor(urlEqualTo("/internal/health/readiness")))
    }
    @Test
    fun `helsestatus settes korrekt om pdl-api  er nede`() {
        stubFor(
            WireMock.get(urlEqualTo("/internal/health/readiness"))
                .willReturn(
                    WireMock.serverError()
                )
        )
        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        personOppslag.status() shouldBe HealthStatus.DOWN
        WireMock.verify(WireMock.getRequestedFor(urlEqualTo("/internal/health/readiness")))
    }
}
