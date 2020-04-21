package no.nav.dagpenger.joark.mottak

fun dummyJournalpost(
    journalstatus: Journalstatus? = null,
    journalpostId: String = "12345",
    bruker: Bruker? = Bruker(BrukerType.AKTOERID, "123456789"),
    tittel: String? = null,
    datoOpprettet: String? = null,
    kanal: String? = null,
    kanalnavn: String? = null,
    journalforendeEnhet: String? = null,
    relevanteDatoer: List<RelevantDato> = emptyList(),
    dokumenter: List<DokumentInfo> = listOf(DokumentInfo("tittel", "infoId", "NAV 04-01.03"))
) = Journalpost(
    journalstatus = journalstatus,
    journalpostId = journalpostId,
    bruker = bruker,
    tittel = tittel,
    datoOpprettet = datoOpprettet,
    kanal = kanal,
    kanalnavn = kanalnavn,
    journalforendeEnhet = journalforendeEnhet,
    relevanteDatoer = relevanteDatoer,
    dokumenter = dokumenter
)
