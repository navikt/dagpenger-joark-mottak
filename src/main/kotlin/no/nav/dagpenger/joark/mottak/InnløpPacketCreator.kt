package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet

private val logger = KotlinLogging.logger {}

class InnløpPacketCreator(
    val personOppslag: PersonOppslag
) {
    fun createPacket(journalpostOgSøknadsdata: Pair<Journalpost, Søknadsdata?>) = Packet().apply {

        val (journalpost, søknadsdata) = journalpostOgSøknadsdata

        this.putValue(PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD, true)

        søknadsdata?.toMap()?.let { this.putValue(PacketKeys.SØKNADSDATA, it) }

        this.putValue(PacketKeys.JOURNALPOST_ID, journalpost.journalpostId)
        this.putValue(PacketKeys.HOVEDSKJEMA_ID, journalpost.dokumenter.first().brevkode ?: "ukjent")
        this.putValue(
            PacketKeys.DOKUMENTER,
            journalpost.dokumenter
        )

        this.putValue(PacketKeys.HENVENDELSESTYPE, journalpost.henvendelsestype)

        journalpost.registrertDato()?.let {
            this.putValue(PacketKeys.DATO_REGISTRERT, it)
        }

        if (null != journalpost.bruker) {
            try {
                personOppslag.hentPerson(journalpost.bruker.id, journalpost.bruker.type).let {
                    this.putValue(PacketKeys.AKTØR_ID, it.aktoerId)
                    this.putValue(PacketKeys.NATURLIG_IDENT, it.naturligIdent)
                    this.putValue(PacketKeys.AVSENDER_NAVN, it.navn)
                    leggPåBehandlendeEnhet(journalpost = journalpost, it.diskresjonskode)
                }
            } catch (e: PersonOppslagException) {
                logger.error { "Kunne ikke slå opp personen. Feilen fra PDL var ${e.message}" }
                leggPåBehandlendeEnhet(journalpost = journalpost)
            }
        } else {
            logger.warn { "Journalpost: ${journalpost.journalpostId} er ikke tilknyttet bruker" }
            leggPåBehandlendeEnhet(journalpost = journalpost)
        }
    }

    private fun Packet.leggPåBehandlendeEnhet(journalpost: Journalpost, diskresjonskode: String? = null) {
        this.putValue(
            PacketKeys.BEHANDLENDE_ENHET,
            behandlendeEnhetFrom(
                diskresjonskode = diskresjonskode,
                brevkode = journalpost.dokumenter.first().brevkode ?: "ukjent"
            )
        )
    }

    private fun behandlendeEnhetFrom(diskresjonskode: String?, brevkode: String): String {
        return when {
            diskresjonskode == "SPSF" -> "2103"
            brevkode == "NAV 04-01.03" -> "4450"
            brevkode == "NAV 04-01.04" -> "4455"
            brevkode == "NAV 04-16.03" -> "4450"
            brevkode == "NAV 04-16.04" -> "4455"
            brevkode == "NAV 04-06.05" -> "4450"
            brevkode == "NAV 04-06.08" -> "4450"
            brevkode == "NAV 90-00.08" -> "4450"
            brevkode == "NAVe 04-16.03" -> "4455"
            brevkode == "NAVe 04-01.04" -> "4455"
            brevkode == "NAVe 04-02.05" -> "4450"
            brevkode == "NAVe 04-08.03" -> "4450"
            brevkode == "NAVe 04-08.04" -> "4450"
            brevkode == "NAVe 04-16.04" -> "4450"
            brevkode == "NAVe 04-02.01" -> "4450"
            brevkode == "NAVe 04-06.05" -> "4450"
            brevkode == "NAVe 04-06.08" -> "4450"
            brevkode == "NAVe 04-03.07" -> "4450"
            brevkode == "NAVe 04-03.08" -> "4450"
            brevkode == "NAVe 04-01.03" -> "4450"
            else -> throw UnsupportedBehandlendeEnhetException("Cannot find behandlende enhet for brevkode $brevkode")
        }
    }
}
