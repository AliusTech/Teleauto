import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "tech.alius.rc.ui"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 25; consumerProguardFiles("consumer-rules.pro") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(project(":sdk"))
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("ui") {
                from(components["release"])
                groupId = "tech.alius"
                artifactId = "teleauto-ui"
                pom { name.set("Teleauto SDK UI"); description.set("Shared controller UI components, layout and themes.") }
            }
        }
    }
}
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/AliusTech/dji_rc")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
tasks.register("verifyUiBoundary") {
    group = "verification"
    doLast {
        val forbidden = listOf("TeleautoSdk.", "TeleautoJoystickManager.", "TeleautoTransmitModuleManager.", "TeleautoVideoTransmissionManager.", "android.hardware.usb.UsbManager")
        val violations = fileTree("src/main/java") { include("**/*.kt", "**/*.java") }.files.flatMap { source ->
            forbidden.filter { source.readText().contains(it) }.map { "${source.relativeTo(projectDir)}: $it" }
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }
}
