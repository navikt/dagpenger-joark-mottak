package no.nav.dagpenger.joark.mottak

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NavSkjemaIdMapperTest {
    @Test
    fun ` known NETS id gets mapped to correct skjemaId`() {
        val type = NavSkjemaIdMapper.getNavSkjemaId("196002")

        assertEquals(type, "NAV 04-01.03")
    }

    @Test
    fun ` unknown NETS id gets mapped to Unknown`() {
        val type = NavSkjemaIdMapper.getNavSkjemaId("xxx")

        assertEquals(type, "Unknown")
    }

    @Test
    fun ` no NETS id gets mapped to Unknown`() {
        val type = NavSkjemaIdMapper.getNavSkjemaId("xxx")

        assertEquals(type, "Unknown")
    }
}