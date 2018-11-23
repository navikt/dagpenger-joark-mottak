package no.nav.dagpenger.joark.mottak

import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad
import org.junit.Test
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalpostTest {

    val uuidPattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    @Test
    fun ` map to Behov from journal post `() {
        val journalpost = Journalpost(
            journalTilstand = JournalTilstand.MIDLERTIDIG,
            avsender = Avsender(navn = "string", avsenderType = AvsenderType.PERSON, identifikator = "string"),
            brukerListe = listOf(Bruker(brukerType = BrukerType.PERSON, identifikator = "string")),
            arkivSak = ArkivSak(arkivSakSystem = "string", arkivSakId = "string"),
            tema = "DAG",
            kanalReferanseId = "ref",
            tittel = "string",
            forsendelseMottatt = "2018-09-25T11:21:11.387Z",
            mottaksKanal = "string",
            journalfEnhet = "string",
            dokumentListe = listOf(
                Dokument(
                    dokumentId = "dokumentId",
                    dokumentTypeId = "string",
                    navSkjemaId = "navSkjemaId",
                    tittel = "string",
                    dokumentKategori = "string",
                    variant = listOf(Variant(arkivFilType = "string", variantFormat = "string")),
                    logiskVedleggListe = listOf(
                        LogiskVedlegg(
                            logiskVedleggId = "string",
                            logiskVedleggTittel = "string"
                        )
                    )
                )
            )
        )

        val behov = journalpost.toBehov("12345")
        assertTrue { uuidPattern.matcher(behov.getBehovId()).matches() }
        assertEquals("12345", behov.getJournalpost().getJournalpostId())
        assertEquals("dokumentId", behov.getJournalpost().getDokumentListe().first().getDokumentId())
        assertEquals("navSkjemaId", behov.getJournalpost().getDokumentListe().first().getNavSkjemaId())
        assertEquals("string", behov.getMottaker().getIdentifikator())
        assertEquals(Annet(), behov.getHenvendelsesType().getAnnet())
    }

    @Test
    fun ` map to Behov from journal post for henvendelsestype 'Søknad' `() {

        assertEquals(Søknad(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAV 04-01.03"))
        assertEquals(Søknad(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAV 04-01.04"))
        assertEquals(Søknad(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAV 04-16.03"))
        assertEquals(Søknad(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAV 04-16.04"))
    }

    @Test
    fun ` map to Behov from journal post for henvendelsestype 'Ettersending' `() {
        assertEquals(Ettersending(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-01.03"))
        assertEquals(Ettersending(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-01.04"))
        assertEquals(Ettersending(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-16.03"))
        assertEquals(Ettersending(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-16.04"))
    }

    @Test
    fun ` map to Behov from journal post for henvendelsestype 'Annet' `() {
        assertEquals(Annet(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-01.05"))
        assertEquals(Annet(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NAVe 04-06.08"))
        assertEquals(Annet(), HenvendelsesTypeMapper.mapper.getHenvendelsesType(""))
        assertEquals(Annet(), HenvendelsesTypeMapper.mapper.getHenvendelsesType("NULL"))
    }
}
