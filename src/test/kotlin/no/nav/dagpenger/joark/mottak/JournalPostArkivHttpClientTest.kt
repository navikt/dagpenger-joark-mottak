package no.nav.dagpenger.joark.mottak

import au.com.dius.pact.consumer.ConsumerPactBuilder
import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.PactTestRun
import au.com.dius.pact.consumer.PactVerificationResult
import au.com.dius.pact.consumer.runConsumerTest
import au.com.dius.pact.model.MockProviderConfig
import org.jetbrains.annotations.NotNull
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JournalPostArkivHttpClientTest {

    @Test
    fun `fetch JournalPost on 200 ok`() {

        val body = JournalPostArkivHttpClientTest::class.java.getResource("/test-data/example-journalpost-payload.json").readText()

        val pact = ConsumerPactBuilder
                .consumer("joark-v1")
                .hasPactWith("dagpenger-joark-mottak")
                .uponReceiving("GET journalpost basert på jpid")
                .path("/rest/journalfoerinngaaende/v1/journalposter/1")
                .matchHeader("Authorization", "Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(body)
                .toPact()

        val config = MockProviderConfig.createDefault()

        val result = runConsumerTest(pact, config, object : PactTestRun {
            @Throws(IOException::class)
            override fun run(@NotNull mockServer: MockServer) {
                val joarkClient = JournalPostArkivHttpClient(config.url())
                val result = joarkClient.hentInngåendeJournalpost("1")
                assertNotNull(result)
            }
        })

        assertEquals(PactVerificationResult.Ok, result)
    }

    @Test
    fun `fetch JournalPost on 4xx errors`() {

        val pact = ConsumerPactBuilder
                .consumer("joark-v1")
                .hasPactWith("dagpenger-joark-mottak")
                .uponReceiving("GET journalpost basert på jpid")
                .path("/rest/journalfoerinngaaende/v1/journalposter/-1")
                .matchHeader("Authorization", "Bearer\\s[\\d|a-f]{8}-([\\d|a-f]{4}-){3}[\\d|a-f]{12}")
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact()

        val config = MockProviderConfig.createDefault()

        val result = runConsumerTest(pact, config, object : PactTestRun {
            @Throws(IOException::class)
            override fun run(@NotNull mockServer: MockServer) {
                val joarkClient = JournalPostArkivHttpClient(config.url())
                try {
                    joarkClient.hentInngåendeJournalpost("-1")
                } catch (e: JournalPostArkivException) {
                    assertEquals(e.statusCode, 404)
                }
            }
        })

        assertEquals(PactVerificationResult.Ok, result)
    }
}