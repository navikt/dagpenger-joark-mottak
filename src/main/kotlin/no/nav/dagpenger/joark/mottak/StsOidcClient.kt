package no.nav.dagpenger.joark.mottak

import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import java.time.Duration
import java.time.LocalDateTime.now

class StsOidcClient(private val stsUrl: String, private val username: String, private val password: String) {
    private val EXPIRE_TIME_TO_REFRESH: Long = 60

    @Volatile private var tokenExpiryTime = now().minus(Duration.ofSeconds(EXPIRE_TIME_TO_REFRESH))

    @Volatile private lateinit var oidcToken: OidcToken

    fun oidcToken(): OidcToken {
        return if (now().isBefore(tokenExpiryTime)) {
            oidcToken
        } else {
            oidcToken = newOidcToken()
            tokenExpiryTime = now().plus(Duration.ofSeconds(oidcToken.expires_in - EXPIRE_TIME_TO_REFRESH))
            oidcToken
        }
    }

    private fun newOidcToken(): OidcToken {
        val parameters = listOf(
                "grant_type" to "client_credentials",
                "scope" to "openid"
        )
        val (_, response, result) = with(stsUrl.httpGet(parameters)) {
            authenticate(username, password)
            responseObject<OidcToken>()
        }
        when (result) {
            is Result.Failure -> throw StsOidcClientException(response.responseMessage, result.getException())
            is Result.Success -> return result.get()
        }
    }
}

class StsOidcClientException(override val message: String, override val cause: Throwable) : RuntimeException(message, cause)

data class OidcToken(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)
