import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version("1.6.10")
    id("com.github.gmazzo.buildconfig") version ("3.1.0")
}

group = "dev.autovoicefunnels"
sourceSets.main {
    java.srcDirs("src/main/kotlin")
}
repositories {
    mavenCentral()
    maven {
        name = "KotlinDiscord"
        url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
    }
}

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("dev.kord:kord-core:0.8.0-M17")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.5.2-RC1")
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.autovoicefunnels.MainKt")
}

// https://docs.gradle.org/current/userguide/working_with_files.html#sec:creating_uber_jar_example
tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = application.mainClass
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

buildConfig {
    buildConfigField("String", "DISCORD_TOKEN", "\"${project.properties["discord.bot.token"]}\"")
}