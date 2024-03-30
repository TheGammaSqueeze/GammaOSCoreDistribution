val kotlinVersion = "1.4.20"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.20"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.android.ndkports"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    google()
}

dependencies {
    implementation(kotlin("stdlib", kotlinVersion))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")

    implementation("com.google.prefab:api:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation("org.redundent:kotlin-xml-builder:1.6.1")

    testImplementation(kotlin("test", kotlinVersion))
    testImplementation(kotlin("test-junit", kotlinVersion))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks {
    compileJava {
        @Suppress("UnstableApiUsage")
        options.release.set(8)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

gradlePlugin {
    plugins {
        create("ndkports") {
            id = "com.android.ndkports.NdkPorts"
            implementationClass = "com.android.ndkports.NdkPortsPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("${rootProject.buildDir}/repository")
        }
    }
}
