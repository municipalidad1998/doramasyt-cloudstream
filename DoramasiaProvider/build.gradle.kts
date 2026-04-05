version = 1

android {
    namespace = "com.cncverse.doramasia"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "es"
    description = "Doramas y series asiaticas en espanol"
    authors = listOf("municipalidad1998")
    status = 1
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries"
    )
    iconUrl = "https://github.com/municipalidad1998/doramasyt-cloudstream/raw/refs/heads/master/DoramasiaProvider/icon.png"
}
