plugins {
    java
    groovy
    application
    jacoco

    kotlin("jvm") version "1.3.60"

    // infers 'project.version' from last annotated Git tag
    id("nebula.release") version "11.1.0"

    // create uber-jar
    id("com.github.johnrengelman.shadow") version "5.1.0"

    // generate terse test report on console
    id("com.adarshr.test-logger") version "2.0.0"

    // report current code coverage
    id("com.github.ksoichiro.console.reporter") version "0.6.2"

    // generate API documentation
    id("org.asciidoctor.convert").version("1.5.9")

    // generate Docker image *without* using docker-engine
    id("com.google.cloud.tools.jib").version("2.0.0")
}

group = "io.eliez.banking"

val dockerGroup = "eliezio"
val dockerRegistry: String? by project

repositories {
    mavenCentral()
}

val ktorVersion = "1.3.1"
val exposedVersion = "0.21.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor", "ktor-server-netty", ktorVersion)
    implementation("io.ktor", "ktor-jackson", ktorVersion)

    implementation("com.h2database", "h2", "1.4.199")
    implementation("org.jetbrains.exposed", "exposed-core", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion)
    implementation("com.zaxxer", "HikariCP", "3.4.1")
    implementation("io.github.microutils", "kotlin-logging", "1.7.6")

    runtimeOnly("ch.qos.logback", "logback-classic", "1.2.3")

    testImplementation("org.spockframework", "spock-core", "1.3-groovy-2.5")
    testImplementation("org.codehaus.groovy.modules.http-builder", "http-builder", "0.7.2") {
        exclude("commons-logging", "commons-logging")
    }
    testRuntime("org.slf4j", "jcl-over-slf4j", "1.7.26")
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
 * Online API documentation
 */
/*
val snippetsDir = "$projectDir/src/docs/asciidoc/snippets"

tasks {
    asciidoctor {
        inputs.dir(snippetsDir)
        attributes(mapOf(
            "project-version" to version.toString(),
            "snippets" to snippetsDir,
            //** reproducible build
            "reproducible" to ""
        ))
        doLast {
            copy {
                from("$outputDir/html5")
                into("$buildDir/resources/main/static/docs")
            }
        }
    }

    shadowJar {
        dependsOn(asciidoctor)
    }
}
*/

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
