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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class JournalpostArkivJoarkTest {

    @Test
    fun `henter søknadsdata for journalpost`() {
        val journalpostId = "123"
        val dokumentId = "666"
        val url = "/rest/hentdokument/$journalpostId/$dokumentId/ORIGINAL"

        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-søknadsdata-payload.json")
            .readText()

        val engine = MockEngine {
            respond(
                content = body,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }
        joarkClient.hentSøknadsdata(
            dummyJournalpost(
                journalpostId = journalpostId,
                kanal = "NAV_NO",
                dokumenter = listOf(DokumentInfo("Søknad", dokumentId, "NAV 04-01.03"))
            )
        )

        engine.requestHistory.size shouldBe 1
        with(engine.requestHistory.first()) {
            this.url.toString() shouldBe "http://localhost$url"
            this.method shouldBe HttpMethod.Get
        }
    }

    @Test
    fun `henter Journalpost med riktig spørring`() {
        val body = JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-payload.json")
            .readText()

        val engine = MockEngine {
            respond(
                content = body,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }
        val journalPost = joarkClient.hentInngåendeJournalpost("1")

        engine.requestHistory.size shouldBe 1
        with(engine.requestHistory[0]) {
            method shouldBe HttpMethod.Post
            url.toString() shouldBe "http://localhost/graphql"
            headers["Authorization"] shouldBe "Bearer hunter2"
        }

        journalPost.tittel shouldBe "MASKERT_FELT"
    }

    @Test
    fun `håndterer statuskode 200 med errors og uten Journalpost`() {
        val body =
            JournalpostArkivJoarkTest::class.java.getResource("/test-data/example-journalpost-error-payload.json")
                .readText()
        val engine = MockEngine {

            respond(
                content = body,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }
        val result = runCatching { joarkClient.hentInngåendeJournalpost("2") }

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<JournalpostArkivException>()
    }

    @Test
    fun `håndterer 4xx-feil`() {

        val engine = MockEngine {
            respondError(HttpStatusCode.NotFound)
        }

        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }

        val result = runCatching { joarkClient.hentInngåendeJournalpost("-1") }
        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<JournalpostArkivException>()
    }

    @Test
    @Disabled
    fun `helsestatus settes korrekt om joark er oppe`() {
        val engine = MockEngine {
            respondOk()
        }
        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }

        joarkClient.status() shouldBe HealthStatus.UP
        engine.requestHistory.first().url.toString() shouldBe "http://localhost/isAlive"
    }

    @Test
    @Disabled
    fun `helsestatus settes korrekt om joark er nede`() {
        val engine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val joarkClient =
            JournalpostArkivJoark("http://localhost/", DummyOidcClient()) { httpClient(engine = engine) }

        joarkClient.status() shouldBe HealthStatus.DOWN
        engine.requestHistory.first().url.toString() shouldBe "http://localhost/isAlive"
    }
}
