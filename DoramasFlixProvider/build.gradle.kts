version = 1

android {
    namespace = "com.cncverse.doramasflix"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "es"
    description = "Doramas subtitulados en espanol"
    authors = listOf("municipalidad1998")
    status = 1
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries"
    )
    iconUrl = "https://github.com/municipalidad1998/doramasyt-cloudstream/raw/refs/heads/master/DoramasFlixProvider/icon.png"
}
