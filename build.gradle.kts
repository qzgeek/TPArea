plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta10" apply true
}

group = "net.qzgeek"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("/tmp/ProtocolLib-server.jar"))
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("TPArea")
        archiveClassifier.set("")
    }
    build {
        dependsOn("shadowJar")
    }
}
