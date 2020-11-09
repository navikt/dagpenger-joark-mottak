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
                    this.putValue(
                        PacketKeys.BEHANDLENDE_ENHET,
                        behandlendeEnhetFrom(it.diskresjonskode, journalpost.dokumenter.first().brevkode ?: "ukjent")
                    )
                }
            } catch (e: Exception) {
                logger.error { "Feil i oppslag av person" }
                logger.error { "Feilen var ${e.message}" }
                throw e
            }
        } else {
            logger.warn { "Journalpost: ${journalpost.journalpostId} er ikke tilknyttet bruker" }
            this.putValue(
                PacketKeys.BEHANDLENDE_ENHET,
                behandlendeEnhetFrom(
                    diskresjonskode = null,
                    brevkode = journalpost.dokumenter.first().brevkode ?: "ukjent"
                )
            )
        }
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
            else -> throw UnsupportedBehandlendeEnhetException("Cannot find behandlende enhet for brevkode $brevkode")
        }
    }
}
