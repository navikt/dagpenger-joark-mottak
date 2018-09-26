package no.nav.dagpenger.joark.mottak

import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class JournalPostParserTest {

    @Test
    fun parse() {

        val journalPost = JournalPostParser.parse(ByteArrayInputStream(exampleJournalPostData().toByteArray(Charsets.UTF_8)))

        assertEquals(journalPost!!.journalTilstand, JournalTilstand.ENDELIG)
        assertEquals(journalPost.avsender, Avsender(navn = "string",avsenderType = AvsenderType.PERSON, identifikator = "string"))
        assertEquals(journalPost.brukerListe, listOf(Bruker(brukerType = BrukerType.PERSON, identifikator = "string")))
        assertEquals(journalPost.arkivSak, ArkivSak(arkivSakSystem = "string", arkivSakId = "string"))
        assertEquals(journalPost.tema, "string")
        assertEquals(journalPost.tittel, "string")
        assertEquals(journalPost.kanalReferanseId, "string")
        assertEquals(journalPost.forsendelseMottatt, ZonedDateTime.of(2018, 9, 25, 11, 21, 11, 387_000_000, ZoneId.of("UTC")))
        assertEquals(journalPost.mottaksKanal, "string")
        assertEquals(journalPost.journalfEnhet, "string")
        assertEquals(journalPost.dokumentListe, listOf(
                Dokument(dokumentId = "string", dokumentTypeId = "string", navSkjemaId = "string", tittel = "string", dokumentKategori = "string",
                        variant = listOf(Variant(arkivFilType = "string", variantFormat = "string")),
                        logiskVedleggListe = listOf(LogiskVedlegg(logiskVedleggId = "string", logiskVedleggTittel = "string")))
                )
        )

    }

    private fun exampleJournalPostData() = """

        {
  "journalTilstand": "ENDELIG",
  "avsender": {
    "avsenderType": "PERSON",
    "identifikator": "string",
    "navn": "string"
  },
  "brukerListe": [
    {
      "brukerType": "PERSON",
      "identifikator": "string"
    }
  ],
  "arkivSak": {
    "arkivSakSystem": "string",
    "arkivSakId": "string"
  },
  "tema": "string",
  "tittel": "string",
  "kanalReferanseId": "string",
  "forsendelseMottatt": "2018-09-25T11:21:11.387Z",
  "mottaksKanal": "string",
  "journalfEnhet": "string",
  "dokumentListe": [
    {
      "dokumentId": "string",
      "dokumentTypeId": "string",
      "navSkjemaId": "string",
      "tittel": "string",
      "dokumentKategori": "string",
      "variant": [
        {
          "arkivFilType": "string",
          "variantFormat": "string"
        }
      ],
      "logiskVedleggListe": [
        {
          "logiskVedleggId": "string",
          "logiskVedleggTittel": "string"
        }
      ]
    }
  ]
}
    """.trimIndent()
}