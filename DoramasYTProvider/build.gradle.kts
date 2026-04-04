// use an integer for version numbers
version = 1

cloudstream {
    language = "es"

    // description = "Ver K-Dramas, C-Dramas, J-Dramas y más gratis en HD"
    authors = listOf("municipalidad1998")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.doramasyt.com/favicon.ico"

    isCrossPlatform = true
}

dependencies {
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
