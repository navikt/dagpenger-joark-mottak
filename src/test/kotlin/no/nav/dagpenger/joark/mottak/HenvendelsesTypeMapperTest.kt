package no.nav.dagpenger.joark.mottak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HenvendelsesTypeMapperTest {

    @Test
    fun `kun nav 04-0103 og nav 04-0104 er henvendelsestype NY_SØKNAD`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType("NAV 04-01.03")
        val type2 = HenvendelsesTypeMapper.getHenvendelsesType("NAV 04-01.04")
        assertEquals(type, Henvendelsestype.NY_SØKNAD)
        assertEquals(type2, Henvendelsestype.NY_SØKNAD)
    }

    @Test
    fun ` unknown skjemaId gets mapped to Annet`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType("xxx")
        assertEquals(type, Henvendelsestype.ANNET)
    }

    @Test
    fun ` no skjemaId gets mapped to Annet`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType(null)
        assertEquals(type, Henvendelsestype.ANNET)
    }

    @Test
    fun ` known skjemaId returns true`() {
        assertTrue(HenvendelsesTypeMapper.isKnownSkjemaId("NAV 04-01.03"))
    }

    @Test
    fun ` unknown skjemaId returns false`() {
        assertFalse(HenvendelsesTypeMapper.isKnownSkjemaId("xxx"))
    }
}