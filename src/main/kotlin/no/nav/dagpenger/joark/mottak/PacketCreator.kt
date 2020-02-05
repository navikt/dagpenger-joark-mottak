package no.nav.dagpenger.joark.mottak

import mu.KotlinLogging
import no.finn.unleash.Unleash
import no.nav.dagpenger.events.Packet

private val logger = KotlinLogging.logger {}

class PacketCreator(
    val personOppslag: PersonOppslag,
    val unleash: Unleash
) {
    fun createPacket(journalpost: Journalpost) = Packet().apply {
        this.putValue(PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD, true)

        this.putValue(PacketKeys.JOURNALPOST_ID, journalpost.journalpostId)
        this.putValue(PacketKeys.HOVEDSKJEMA_ID, journalpost.dokumenter.first().brevkode ?: "ukjent")
        this.putValue(
            PacketKeys.DOKUMENTER, journalpost.dokumenter
        )

        this.putValue(PacketKeys.HENVENDELSESTYPE, journalpost.henvendelsestype)

        journalpost.relevanteDatoer.find { it.datotype == Datotype.DATO_REGISTRERT }?.let {
            this.putValue(PacketKeys.DATO_REGISTRERT, it.dato)
        }

        if (null != journalpost.bruker) {
            personOppslag.hentPerson(journalpost.bruker.id, journalpost.bruker.type).let {
                this.putValue(PacketKeys.AKTØR_ID, it.aktoerId)
                this.putValue(PacketKeys.NATURLIG_IDENT, it.naturligIdent)
                this.putValue(PacketKeys.AVSENDER_NAVN, it.navn)
                this.putValue(
                    PacketKeys.BEHANDLENDE_ENHET,
                    behandlendeEnhetFrom(it.diskresjonskode, journalpost.dokumenter.first().brevkode ?: "ukjent")
                )
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
