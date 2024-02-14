import java.net.URI

plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.henrycourse"
version = "0.0.1"

publishing {
    publications {
        create<MavenPublication>("ktor-jetty-12-poc") {
            from(components["kotlin"])
        }
    }

    repositories {
        maven {
            name = "GithubPackages"
            url = URI("https://maven.pkg.github.com/henryjcee/ktor-jetty-12-poc")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(platform("io.ktor:ktor-bom:3.0.0-beta-1"))
    implementation(platform("org.eclipse.jetty:jetty-bom:12.0.6"))

    implementation("io.ktor:ktor-server-core-jvm")

    implementation("org.eclipse.jetty:jetty-server")
    implementation("org.eclipse.jetty:jetty-alpn-java-server")
    implementation("org.eclipse.jetty.http3:http3-server:11.0.20")
    implementation("org.eclipse.jetty.http2:http2-server:11.0.20")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
