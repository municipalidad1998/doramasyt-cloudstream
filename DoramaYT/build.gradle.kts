plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.municipalidad1998.doramasyt"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        targetSdk = 35
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
}
