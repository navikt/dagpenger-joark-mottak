package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.dagpenger.streams.HealthStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
                .withHeader("Content-type", EqualToPattern("application/json"))
                .withHeader("TEMA", EqualToPattern("DAG"))
                .withHeader("Nav-Consumer-Token", EqualToPattern("Bearer hunter2"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val person = personOppslag.hentPerson("789")

        WireMock.verify(WireMock.postRequestedFor(urlEqualTo("/graphql")))
        person.aktoerId shouldBe "2797593735308"
        person.naturligIdent shouldBe "13086824072"
        person.navn shouldBe "LITEN DRØVTYGGENDE BRANNHYDRANT"
        person.norskTilknytning shouldBe true
        person.diskresjonskode shouldBe "STRENGT_FORTROLIG_UTLAND"
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten person`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-person-error-payload.json")
            .readText()
        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Content-type", EqualToPattern("application/json"))
                .withHeader("TEMA", EqualToPattern("DAG"))
                .withHeader("Nav-Consumer-Token", EqualToPattern("Bearer hunter2"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val result = runCatching { personOppslag.hentPerson("123") }

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<PersonOppslagException>()
        WireMock.verify(WireMock.postRequestedFor(urlEqualTo("/graphql")))
    }

    @Test
    fun `håndterer 400-statuskoder`() {
        stubFor(
            post(urlEqualTo("/graphql"))
                .withHeader("Content-type", EqualToPattern("application/json"))
                .withHeader("TEMA", EqualToPattern("DAG"))
                .withHeader("Nav-Consumer-Token", EqualToPattern("Bearer hunter2"))
                .willReturn(
                    notFound()
                )
        )

        val personOppslag = PersonOppslag(server.url(""), DummyOidcClient())
        val result = runCatching { personOppslag.hentPerson("123") }

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<PersonOppslagException>()
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
    fun `helsestatus settes korrekt om pdl-api er nede`() {
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
