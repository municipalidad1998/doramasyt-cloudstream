pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "doramasyt-cloudstream"

include(
    ":DoramasYTProvider",
    ":DoramasFlixProvider",
    ":DoramasiaProvider",
    ":PeliCineHDProvider",
    ":PelisJuanitaProvider"
)
