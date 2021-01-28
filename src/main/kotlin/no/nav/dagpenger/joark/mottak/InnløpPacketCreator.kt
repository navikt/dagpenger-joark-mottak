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
                    logger.info { "norsk tilknytning: $it.norskTilknytning" }
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

    private val PERMITTERING_BREVKODER =
        listOf(
            "NAV 04-01.04",
            "NAVe 04-01.04",
            "NAVe 04-16.03",
            "NAV 04-16.04",
            "NAVe 04-08.04",
            "NAV 04-08.04"
        )

    private val UTLAND_BREVKODER =
        listOf("NAV 04-02.01", "NAVe 04-02.01", "NAV 04-02.03", "NAV 04-02.05", "NAVe 04-02.05")

    private fun behandlendeEnhetFrom(diskresjonskode: String?, brevkode: String): String {
        return when {
            diskresjonskode == "SPSF" -> "2103"
            brevkode in PERMITTERING_BREVKODER -> "4455"
            brevkode in UTLAND_BREVKODER -> "4470"
            else -> "4450"
        }
    }
}
