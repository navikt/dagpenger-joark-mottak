package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.oidc.OidcToken
import java.util.UUID

class DummyOidcClient : OidcClient {
    override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
}
