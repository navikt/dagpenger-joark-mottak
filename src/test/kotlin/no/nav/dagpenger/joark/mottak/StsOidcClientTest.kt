package no.nav.dagpenger.joark.mottak

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.RegexPattern
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class StsOidcClientTest {

    @Rule
    @JvmField
    var wireMockRule = WireMockRule(wireMockConfig().dynamicPort())

    private val expires = 300L

    @Test
    fun `fetch open id token from sts server`() {

        stubFor(WireMock.get(WireMock.urlEqualTo("/stsurl?grant_type=client_credentials&scope=openid"))
                .withHeader("Authorization", RegexPattern("Basic\\s[a-zA-Z0-9]*="))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body())
                )
        )

        val stsOidcClient = StsOidcClient(wireMockRule.url("stsurl"), "username", "password")
        val oidcToken: OidcToken = stsOidcClient.oidcToken()

        assertEquals(oidcToken, OidcToken("token", "openid", expires))
    }

    @Test
    fun `fetch open id token from sts server and token is cached `() {

        stubFor(WireMock.get(WireMock.urlEqualTo("/cached?grant_type=client_credentials&scope=openid"))
                .withHeader("Authorization", RegexPattern("Basic\\s[a-zA-Z0-9]*="))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body())
                )
        )
        val stsOidcClient = StsOidcClient(wireMockRule.url("cached"), "username", "password")

        val firstCall: OidcToken = stsOidcClient.oidcToken()

        assertEquals(firstCall, OidcToken("token", "openid", expires))

        val secondCall: OidcToken = stsOidcClient.oidcToken()

        assertEquals(secondCall, OidcToken("token", "openid", expires))

        verify(exactly(1), getRequestedFor(urlEqualTo("/cached?grant_type=client_credentials&scope=openid"))
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