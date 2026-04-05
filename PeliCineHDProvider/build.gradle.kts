version = 1

android {
    namespace = "com.cncverse.pelicinehd"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "es"
    description = "Peliculas y series en HD 1080P Latino"
    authors = listOf("municipalidad1998")
    status = 1
    apiVersion = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    iconUrl = "https://github.com/municipalidad1998/doramasyt-cloudstream/raw/refs/heads/master/PeliCineHDProvider/icon.png"
}
