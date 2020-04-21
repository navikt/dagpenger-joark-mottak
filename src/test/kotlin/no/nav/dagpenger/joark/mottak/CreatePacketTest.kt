package no.nav.dagpenger.joark.mottak

import com.squareup.moshi.Types
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CreatePacketTest {

    companion object {
        val personOppslagMock = mockk<PersonOppslag>()

        @BeforeAll
        @JvmStatic
        fun setUp() {
            every { personOppslagMock.hentPerson(any(), any()) } returns Person(
                navn = "Proffen",
                aktoerId = "1111",
                naturligIdent = "1234",
                diskresjonskode = null
            )
        }
    }

    @Test
    fun `skal hente og legge brukerinfo på packet`() {
        val journalpost = dummyJournalpost(
            bruker = Bruker(BrukerType.AKTOERID, "1111"),
            dokumenter = listOf(DokumentInfo("tittel", "infoId", "NAV 04-01.03"))
        )

        val packetCreator = InnløpPacketCreator(personOppslagMock)
        val packet = packetCreator.createPacket(journalpost)

        verify { personOppslagMock.hentPerson("1111", BrukerType.AKTOERID) }

        packet.getStringValue("aktørId") shouldBe "1111"
        packet.getStringValue("naturligIdent") shouldBe "1234"
        packet.getStringValue("avsenderNavn") shouldBe "Proffen"
        packet.getStringValue("behandlendeEnhet") shouldBe "4450"
    }

    @Test
    fun `skal gi hovedskjema`() {
        val packetCreator = InnløpPacketCreator(personOppslagMock)

        val journalpost = dummyJournalpost(
            dokumenter = listOf(DokumentInfo("tittel", "infoId", "NAV 04-01.04"))
        )
        val packet = packetCreator.createPacket(journalpost)

        packet.getStringValue("hovedskjemaId") shouldBe "NAV 04-01.04"
        packet.getStringValue("henvendelsestype") shouldBe "NY_SØKNAD"
    }

    @Test
    fun `skal legge dokumentliste på pakken i JSON-format`() {
        val packetCreator = InnløpPacketCreator(personOppslagMock)

        val journalpost = dummyJournalpost(
            dokumenter = listOf(DokumentInfo("Søknad", "infoId", "NAV 04-01.04"))
        )
        val packet = packetCreator.createPacket(journalpost)

        val adapter = moshiInstance.adapter<List<DokumentInfo>>(
            Types.newParameterizedType(
                List::class.java,
                DokumentInfo::class.java
            )
        )

        val dokumentListe = Packet(packet.toJson()!!).getObjectValue("dokumenter") { adapter.fromJsonValue(it)!! }
        dokumentListe.size shouldBe 1
        dokumentListe.first().tittel shouldBe "Søknad"
    }

    @Test
    fun `skal få riktig behandlende enhet ved kode 6`() {
        val personOppslagMedDiskresjonskode = mockk<PersonOppslag>()

        every { personOppslagMedDiskresjonskode.hentPerson(any(), any()) } returns Person(
            navn = "Proffen",
            aktoerId = "1111",
            naturligIdent = "1234",
            diskresjonskode = "SPSF"
        )

        val packetCreator = InnløpPacketCreator(personOppslagMedDiskresjonskode)
        val packet = packetCreator.createPacket(dummyJournalpost())

        packet.getStringValue("behandlendeEnhet") shouldBe "2103"
    }

    @Test
    fun `nye søknader (ikke permitering) skal havne på benk 4450`() {
        val packetCreator = InnløpPacketCreator(personOppslagMock)

        val journalpost = dummyJournalpost(
            dokumenter = listOf(DokumentInfo(tittel = "Søknad", dokumentInfoId = "9", brevkode = "NAV 04-01.03"))
        )
        val packet = packetCreator.createPacket(journalpost)

        packet.getStringValue("behandlendeEnhet") shouldBe "4450"
    }

    @Test
    fun `nye søknader ved permitering skal havne på benk 4455`() {
        val packetCreator = InnløpPacketCreator(personOppslagMock)

        val journalpost = dummyJournalpost(
            dokumenter = listOf(DokumentInfo(tittel = "Søknad", dokumentInfoId = "9", brevkode = "NAV 04-01.04"))
        )
        val packet = packetCreator.createPacket(journalpost)

        packet.getStringValue("behandlendeEnhet") shouldBe "4455"
    }
}
