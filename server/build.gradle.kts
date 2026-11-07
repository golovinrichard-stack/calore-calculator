import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "com.calore"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.calore.research.MainKt")
}

val ktor = "3.6.1"

dependencies {
    // M10.6: Kotlin + Ktor + PostgreSQL, as named in the milestone.
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-rate-limit:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("ch.qos.logback:logback-classic:1.6.6")

    // Tests: Ktor's test host and H2 in PostgreSQL mode as the test database.
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("com.h2database:h2:2.5.252")
}

tasks.test {
    useJUnitPlatform()
}
