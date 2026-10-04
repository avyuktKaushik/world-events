plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "dev.arc2"
version = "1.0.0"
description = "Arc 2 World Events"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    jar {
        archiveBaseName.set("WorldEvents")
        archiveClassifier.set("")
    }
    runServer {
        // ./gradlew runServer  -> starts a local Paper 1.21.11 test server with the plugin
        minecraftVersion("1.21.11")
    }
}
