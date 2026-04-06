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

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

subprojects {
    afterEvaluate {
        tasks.findByName("make")?.let { makeTask ->
            rootProject.tasks.findByName("makeAll")?.dependsOn(makeTask)
        }
    }
}

tasks.register("makeAll") {
    group = "build"
}
