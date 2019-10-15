package no.nav.dagpenger.joark.mottak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HenvendelsesTypeMapperTest {

    @Test
    fun ` known søknad-skjemaId gets mapped to søknad`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType("NAV 04-01.03")
        assertEquals(type, Henvendelsestype.SØKNAD)
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