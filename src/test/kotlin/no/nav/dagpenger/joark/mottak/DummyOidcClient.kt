package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.oidc.OidcToken

class DummyOidcClient : OidcClient {
    override suspend fun oidcToken(): OidcToken = OidcToken("hunter2", "openid", 3000)
}
