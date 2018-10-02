package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RegexPattern
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class StsOidcClientTest {
    companion object {
        val wireMockServer = WireMockServer(
                options().dynamicPort()
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            wireMockServer.start()
        }

        @AfterClass
        @JvmStatic
        fun after() {
            wireMockServer.stop()
        }
    }

    private val expires = 300L

    @Test
    fun `fetch open id token from sts server`() {

        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/stsurl?grant_type=client_credentials&scope=openid"))
                .withHeader("Authorization", RegexPattern("Basic\\s[a-zA-Z0-9]*="))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body())
                )
        )

        val stsOidcClient = StsOidcClient(wireMockServer.url("stsurl"), "username", "password")
        val oidcToken: OidcToken = stsOidcClient.oidcToken()

        assertEquals(oidcToken, OidcToken("token", "openid", expires))
    }

    @Test
    fun `fetch open id token from sts server and token is cached `() {

        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/cached?grant_type=client_credentials&scope=openid"))
                .withHeader("Authorization", RegexPattern("Basic\\s[a-zA-Z0-9]*="))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body())
                )
        )
        val stsOidcClient = StsOidcClient(wireMockServer.url("cached"), "username", "password")

        val firstCall: OidcToken = stsOidcClient.oidcToken()

        assertEquals(firstCall, OidcToken("token", "openid", expires))

        val secondCall: OidcToken = stsOidcClient.oidcToken()

        assertEquals(secondCall, OidcToken("token", "openid", expires))

        verify(exactly(1), getRequestedFor(urlEqualTo("/stsurl?grant_type=client_credentials&scope=openid"))
                .withHeader("Authorization", RegexPattern("Basic\\s[a-zA-Z0-9]*=")))
    }

    fun body() = """
                {
                    "access_token": "token",
                    "token_type" : "openid",
                    "expires_in" : $expires
                }

            """.trimIndent()
}