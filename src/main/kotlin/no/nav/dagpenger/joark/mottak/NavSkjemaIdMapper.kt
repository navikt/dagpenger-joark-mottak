package no.nav.dagpenger.joark.mottak

class NavSkjemaIdMapper {

    object mapper {

        private val dokumentIdtoNavSkjemaIdMapper = mapOf(
            "196002" to "NAV 04-01.03",
            "273034" to "NAV 04-01.04",
            "200899" to "NAV 04-16.03",
            "221659" to "NAV 04-16.04",
            "596002" to "NAVe 04-01.03",
            "673034" to "NAVe 04-01.04",
            "600899" to "NAVe 04-16.03",
            "621659" to "NAVe 04-16.04",
            "196008" to "NAV 04-01.05",
            "196076" to "NAV 04-02.01",
            "196216" to "NAV 04-02.03",
            "196256" to "NAV 04-02.05",
            "196862" to "NAV 04-03.07",
            "197391" to "NAV 04-06.05",
            "197897" to "NAV 04-06.08",
            "198442" to "NAV 04-06.09",
            "200000" to "NAV 04-06.10",
            "200365" to "NAV 04-08.03",
            "219312" to "NAV 04-05.03",
            "219521" to "NAV 04-05.05",
            "219569" to "NAV 04-05.07",
            "233862" to "NAV 04-02.10",
            "235955" to "NAV 04-05.09",
            "596008" to "NAVe 04-01.05",
            "596076" to "NAVe 04-02.01",
            "596256" to "NAVe 04-02.05",
            "597897" to "NAVe 04-06.08",
            "598442" to "NAVe 04-06.09",
            "270440" to "NAV 04-03.03",
            "670440" to "NAVe 04-03.03",
            "276058" to "NAV 04-08.04",
            "596862" to "NAVe 04-03.07",
            "597391" to "NAVe 04-06.05",
            "600365" to "NAVe 04-08.03",
            "676058" to "NAVe 04-08.04",
            "287483" to "NAV 04-03.08",
            "687483" to "NAVe 04-03.08",
            "600000" to "NAVe 04-06.10",
            "263651" to "NAV 04-13.01",
            "298170" to "NAV 04-02.02",
            "698170" to "NAVe 04-02.02",
            "231823" to "NAV 90-00.08"

        )

        fun getNavSkjemaId(navSkjemaId: String?): String {
            return dokumentIdtoNavSkjemaIdMapper.getOrDefault(navSkjemaId.orEmpty(), "Unknown")
        }
    }
}
