plugins {
    id("com.lagradost.cloudstream3.gradle")
}

version = 2

cloudstream {
    setRepo("https://github.com/municipalidad1998/doramasyt-cloudstream")
    description = "Peliculas y series en HD 1080P Latino"
    authors     = listOf("municipalidad1998")
    language    = "es"
    tvTypes     = listOf("Movie", "TvSeries")
    iconUrl     = "https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/main/PeliCineHDProvider/icon.png"
}
