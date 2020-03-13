plugins {
    java
    groovy
    application
    jacoco

    kotlin("jvm") version "1.5.21"

    // infers 'project.version' from last annotated Git tag
    id("nebula.release") version "15.3.1"

    // create uber-jar
    id("com.github.johnrengelman.shadow") version "7.0.0"

    // generate terse test report on console
    id("com.adarshr.test-logger") version "3.0.0"

    // report current code coverage
    id("com.github.ksoichiro.console.reporter") version "0.6.3"

    // generate Docker image *without* using docker-engine
    id("com.google.cloud.tools.jib") version "3.1.3"
}

group = "io.eliez.banking"

val exposedVersion: String by project
val groovyVersion: String by project
val h2Version: String by project
val hikariVersion: String by project
val httpBuilderVersion: String by project
val kotlinLoggingVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project
val slf4jVersion: String by project
val spockVersion: String by project

val dockerGroup = "eliezio"
val dockerRegistry: String? by project

repositories {
    mavenCentral()
}

application {
    mainClass.set("io.eliez.banking.MainKt")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.ktor", "ktor-server-core", ktorVersion)
    implementation("io.ktor", "ktor-server-netty", ktorVersion)
    implementation("io.ktor", "ktor-jackson", ktorVersion)

    implementation("com.h2database", "h2", h2Version)
    implementation("org.jetbrains.exposed", "exposed-core", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion)
    implementation("com.zaxxer", "HikariCP", hikariVersion)
    implementation("io.github.microutils", "kotlin-logging", kotlinLoggingVersion)

    runtimeOnly("ch.qos.logback", "logback-classic", logbackVersion)

    testImplementation("org.spockframework", "spock-core", spockVersion)
    testImplementation("io.ktor", "ktor-server-test-host", ktorVersion)
    testImplementation("org.codehaus.groovy.modules.http-builder", "http-builder", httpBuilderVersion) {
        exclude("commons-logging", "commons-logging")
    }
    testRuntime("org.slf4j", "jcl-over-slf4j", slf4jVersion)
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
}

/*
 * Test
 */
tasks {
    jacocoTestReport {
        reports {
            html.required.set(true)
        }
    }

    reportCoverage {
        dependsOn(jacocoTestReport)

        doLast {
            val htmlDir = jacocoTestReport.get().reports.html.outputLocation
            println("\nThe JaCoCo HTML report is available at file://$htmlDir/index.html")
        }
    }
}

/*
 * All Archives
 */
tasks.withType<AbstractArchiveTask> {
    //** reproducible build
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/*
 * Docker Image
 */
jib {
    from {
        // Smaller than the default gcr/distroless/java
        image = "openjdk:8-jre-alpine"
    }
    to {
        val tagVersion = version.toString().substringBefore('-')
        image = listOfNotNull(dockerRegistry, dockerGroup, "${project.name}:$tagVersion")
            .joinToString("/")
    }
    container {
        jvmFlags = listOf("-noverify")
        mainClass = application.mainClass.get()
        ports = listOf("8080")
    }
}

tasks.jibDockerBuild {
    dependsOn(tasks.build)
}
