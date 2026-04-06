pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.lagradost.cloudstream3")) {
                useModule("com.github.recloudstream:gradle:master-SNAPSHOT")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
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
