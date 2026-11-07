// The research server (M10.6): a separate build, so the Android app's build stays as it is.
// Run it with the app's Gradle wrapper: ../gradlew -p server test
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "calore-research-server"
