buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")
    }
}

tasks.register("makeAll") {
    group = "build"
    description = "Build all CS3 plugins"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("make") })
}
