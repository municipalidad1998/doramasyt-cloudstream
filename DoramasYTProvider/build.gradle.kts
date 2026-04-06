apply(plugin = "com.lagradost.cloudstream3.gradle")

version = 2

cloudstream {
    setRepo("https://github.com/municipalidad1998/doramasyt-cloudstream")
    description = "Doramas con subtitulos en Espanol y Latino"
    authors     = listOf("municipalidad1998")
    language    = "es"
    tvTypes     = listOf("AsianDrama", "TvSeries")
    iconUrl     = "https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/main/DoramasYTProvider/icon.png"
}
