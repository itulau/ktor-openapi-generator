import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import pl.allegro.tech.build.axion.release.domain.properties.TagProperties
import pl.allegro.tech.build.axion.release.domain.scm.ScmPosition
import java.net.URL
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.kotlin.tooling.core.closureSequence

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.dokka)
    alias(libs.plugins.axion)
    alias(libs.plugins.nexusPublish)
    `maven-publish`
    signing
}

scmVersion {
    tag {
        prefix = ""
        versionSeparator = ""
    }

    val regex = Regex("""\d+\.\d+\.\d+""")
    tag.deserializer { _: TagProperties, _: ScmPosition, tagName: String ->
        if (tagName.matches(regex)) tagName else "0.0.0"
    }

    useHighestVersion = true

    branchVersionIncrementer = mapOf(
        "feature/.*" to "incrementMinor"
    )
}
val groupName: String by project
val groupPath = groupName.replace(".","/")
val archivesName: String by project
val ver: String by project

group = groupName
base.archivesName.set(archivesName)
version = ver

val buildPath = "$rootDir/build/$groupPath/$archivesName/$ver"

tasks.withType<Jar> {
    destinationDirectory = File(buildPath)
}
tasks.withType<GenerateMavenPom> {
    destination = file("$buildPath/$archivesName-$version.pom")
}

repositories {
    mavenCentral()
}

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath(libs.commonsCodec)
    }
}

dependencies {
    // Ktor server dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.slf4j)
    implementation(libs.swaggerUi)
    implementation(libs.reflections) // only used while initializing

    // testing
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.ktor.server.auth.jwt)
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.jackson.kotlin)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.ktor.serialization.jackson)

    testImplementation(libs.logback) // logging framework for the tests

    testImplementation(libs.junit.jupiter.api) // junit testing framework
    testImplementation(libs.junit.jupiter.params) // generated parameters for tests
    testRuntimeOnly(libs.junit.jupiter.engine) // testing runtime
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    test {
        useJUnitPlatform()
    }

    dokkaHtml {
        outputDirectory.set(File("${layout.buildDirectory.asFile.get()}/docs"))

        dokkaSourceSets {
            configureEach {
                displayName.set("Ktor OpenAPI/Swagger 3 Generator")

                sourceLink {
                    localDirectory.set(file("src/main/kotlin"))
                    remoteUrl.set(URL("https://github.com/itulau/ktor-openapi-generator/tree/master/src/main/kotlin"))
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }
}

tasks.register("generateChecksums") {
    group = "verification" // Optional: Assign a task group
    description = "Generates MD5 and SHA1 checksums"

    val destinationDir = File(buildPath)

    doLast {
        if (!destinationDir.exists()) {
            logger.error("Directory $destinationDir does not exist.")
            return@doLast
        }

        // Process all .jar and .pom files in the directory
        fileTree(destinationDir).matching {
            include("*.jar", "*.pom")
        }.forEach { file ->
            if (file.length() == 0L) {
                logger.warn("Skipping checksum generation for ${file.name} because it is empty.")
                return@forEach
            }

            val md5Checksum = file.inputStream().use { DigestUtils.md5Hex(it) }
            val checksumFileMD5 = File("${file.absolutePath}.md5")
            checksumFileMD5.writeText(md5Checksum)
            logger.lifecycle("Generated MD5 checksum for ${file.name}: ${checksumFileMD5.absolutePath}")

            val sha1Checksum = file.inputStream().use { DigestUtils.sha1Hex(it) }
            val checksumFileSHA1 = File("${file.absolutePath}.sha1")
            checksumFileSHA1.writeText(sha1Checksum)
            logger.lifecycle("Generated SHA1 checksum for ${file.name}: ${checksumFileSHA1.absolutePath}")
        }
    }
}


// ------------------------------------ Deployment Configuration  ------------------------------------
// deployment configuration - deploy with sources and documentation
val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
}

// name the publication as it is referenced
val publication = "mavenJava"
publishing {
    // create jar with sources and with javadoc
    publications {
        create<MavenPublication>(publication) {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("Ktor OpenAPI/Swagger 3 Generator")
                description.set("The Ktor OpenAPI Generator is a library to automatically generate the descriptor as you route your ktor application.")
                url.set("https://github.com/itulau/ktor-openapi-generator")
                packaging = "jar"
                licenses {
                    license {
                        name.set("Apache-2.0 License")
                        url.set("https://github.com/itulau/ktor-openapi-generator/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("wicpar")
                        name.set("Frédéric Nieto")
                    }
                    developer {
                        id.set("lukasforst")
                        name.set("Lukas Forst")
                        email.set("lukas@forst.dev")
                    }
                    developer {
                        id.set("darkxanter")
                        name.set("Sergey Shumov")
                    }
                    developer {
                        id.set("itulau")
                        name.set("Lautaro Iturregui")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/itulau/ktor-openapi-generator.git")
                    url.set("https://github.com/itulau/ktor-openapi-generator")
                }
            }
        }
    }
}

signing {
    val signingKeyId = project.findProperty("gpg.keyId") as String? ?: System.getenv("GPG_KEY_ID")
    val signingKey = project.findProperty("gpg.key") as String? ?: System.getenv("GPG_KEY")
    val signingPassword = project.findProperty("gpg.keyPassword") as String? ?: System.getenv("GPG_KEY_PASSWORD")
    if (signingKeyId != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    } else {
        useGpgCmd()
    }
    sign(publishing.publications[publication])
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(project.findProperty("ossrh.username") as String? ?: System.getenv("OSSRH_USERNAME"))
            password.set(project.findProperty("ossrh.password") as String? ?: System.getenv("OSSRH_PASSWORD"))
        }
    }
}
