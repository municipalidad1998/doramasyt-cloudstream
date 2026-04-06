plugins {
    id("com.lagradost.cloudstream3.gradle")
}

version = 2

cloudstream {
    setRepo("https://github.com/municipalidad1998/doramasyt-cloudstream")
    description = "Doramas y series asiaticas en espanol"
    authors     = listOf("municipalidad1998")
    language    = "es"
    tvTypes     = listOf("AsianDrama", "TvSeries")
    iconUrl     = "https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/main/DoramasiaProvider/icon.png"
}
