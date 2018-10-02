package no.nav.dagpenger.joark.mottak

class JournalpostArkivDummy : JournalpostArkiv {
    override fun hentInngåendeJournalpost(journalpostId: String): JournalPost? {
        return JournalPost(
                tittel = "tittel",
                journalTilstand = JournalTilstand.MIDLERTIDIG,
                journalfEnhet = "nav",
                avsender = Avsender("Navn Navnesen", AvsenderType.PERSON, identifikator = "1234556777"),
                forsendelseMottatt = "2018-09-25T11:21:11.387Z",
                kanalReferanseId = "ref",
                arkivSak = ArkivSak(arkivSakId = "1234", arkivSakSystem = "fag"),
                mottaksKanal = "Nav",
                tema = "DAG",
                dokumentListe = listOf(
                Dokument(dokumentId = "string", dokumentTypeId = "string", navSkjemaId = "string", tittel = "string", dokumentKategori = "string",
                        variant = listOf(Variant(arkivFilType = "string", variantFormat = "string")),
                        logiskVedleggListe = listOf(LogiskVedlegg(logiskVedleggId = "string", logiskVedleggTittel = "string")))),
                brukerListe = listOf(Bruker(brukerType = BrukerType.PERSON, identifikator = "string"))
        )
    }
}