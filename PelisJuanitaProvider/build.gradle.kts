version = 1

android {
    namespace = "com.cncverse.pelisjuanita"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "es"
    description = "Peliculas y series en espanol latino HD"
    authors = listOf("municipalidad1998")
    status = 1
    apiVersion = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    iconUrl = "https://github.com/municipalidad1998/doramasyt-cloudstream/raw/refs/heads/master/PelisJuanitaProvider/icon.png"
}
