plugins {
    id('java')
    id('com.gradleup.shadow') version '9.0.0-beta10'
}

group = 'net.qzgeek'
version = '1.0.0'

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven('https://repo.papermc.io/repository/maven-public/')
}

dependencies {
    compileOnly('io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT')
}

shadowJar {
    archiveBaseName.set('TPArea')
    archiveClassifier.set('')
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
