package no.nav.dagpenger.joark.mottak

import java.util.UUID
import no.nav.dagpenger.oidc.OidcClient
import no.nav.dagpenger.oidc.OidcToken

class DummyOidcClient : OidcClient {
    override fun oidcToken(): OidcToken = OidcToken(UUID.randomUUID().toString(), "openid", 3000)
}
