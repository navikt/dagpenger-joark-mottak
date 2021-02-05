package no.nav.dagpenger.joark.mottak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import no.nav.dagpenger.streams.HealthStatus
import org.junit.jupiter.api.Test

internal class PersonOppslagTest {
    @Test
    fun `henter person med riktig spørring og headers`() {
        val body = PersonOppslagTest::class.java.getResource("/test-data/example-person-payload.json")
            .readText()

        val engine = MockEngine {
            this.respond(
                headers = headersOf("Content-Type", "application/json"),
                content = body
            )
        }
        val person = PersonOppslag(
            "http://localhost/",
            DummyOidcClient()
        ) { httpClient(engine = engine) }.hentPerson("780")

        person.aktoerId shouldBe "2797593735308"
        person.naturligIdent shouldBe "13086824072"
        person.navn shouldBe "LITEN DRØVTYGGENDE BRANNHYDRANT"
        person.norskTilknytning shouldBe true
        person.diskresjonskode shouldBe "STRENGT_FORTROLIG_UTLAND"

        engine.requestHistory.size shouldBe 1
        with(engine.requestHistory[0]) {
            method shouldBe HttpMethod.Post
            url.toString() shouldBe "http://localhost/graphql"
            headers["Authorization"] shouldBe "Bearer hunter2"
            headers["Nav-Consumer-Token"] shouldBe "Bearer hunter2"
            headers["TEMA"] shouldBe "DAG"
        }
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten person`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-person-error-payload.json")
            .readText()

        val engine = MockEngine {
            this.respond(
                headers = headersOf("Content-Type", "application/json"),
                content = body
            )
        }

        val personOppslag = PersonOppslag("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }

        val result = runCatching { personOppslag.hentPerson("123") }
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<PersonOppslagException>()
    }

    @Test
    fun `håndterer 400-statuskoder`() {
        val engine = MockEngine { this.respondError(HttpStatusCode.NotFound) }

        val personOppslag = PersonOppslag("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }

        val result = runCatching { personOppslag.hentPerson("123") }
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<PersonOppslagException>()
    }

    @Test
    fun `helsestatus settes korrekt om pdl-api er oppe`() {
        val engine = MockEngine { respondOk() }

        val personOppslag = PersonOppslag("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }
        personOppslag.status() shouldBe HealthStatus.UP
        engine.requestHistory.first().url.toString() shouldBe "http://localhost/internal/health/readiness"
    }

    @Test
    fun `helsestatus settes korrekt om pdl-api  er nede`() {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

        val personOppslag = PersonOppslag("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }
        personOppslag.status() shouldBe HealthStatus.DOWN
        engine.requestHistory.first().url.toString() shouldBe "http://localhost/internal/health/readiness"
    }
}
