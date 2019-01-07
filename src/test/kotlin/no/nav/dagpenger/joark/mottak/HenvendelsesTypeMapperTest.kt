package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Søknad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HenvendelsesTypeMapperTest {
    @Test
    fun ` known skjemaId gets mapped to correct type`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType("NAV 04-01.03")

        assertEquals(type, Søknad())
    }

    @Test
    fun ` unknown skjemaId gets mapped to Annet`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType("xxx")

        assertEquals(type, Annet())
    }

    @Test
    fun ` no skjemaId gets mapped to Annet`() {
        val type = HenvendelsesTypeMapper.getHenvendelsesType(null)

        assertEquals(type, Annet())
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