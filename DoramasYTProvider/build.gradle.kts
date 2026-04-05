version = 1

android {
    namespace = "com.cncverse.doramasyt"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "es"
    description = "Doramas con subtitulos en Espanol y Latino"
    authors = listOf("municipalidad1998")
    status = 1
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries"
    )
    iconUrl = "https://github.com/municipalidad1998/doramasyt-cloudstream/raw/refs/heads/master/DoramasYTProvider/icon.png"
}
