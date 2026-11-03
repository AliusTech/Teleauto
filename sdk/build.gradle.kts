import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "tech.alius.rc"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 25          // 通用遥控器库(RC Plus=29,兼容更低)
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++20") } }
        buildConfigField("String", "VERSION", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        named("main") {
            java.srcDir("../modules/video_transmission/src/main/java")
            java.srcDir("../license-common/src/main/java")
            java.srcDir(layout.buildDirectory.dir("generated/license/java").get().asFile)
            assets.srcDir("../modules/video_transmission/src/main/assets")
            jniLibs.srcDir("../modules/video_transmission/src/main/cpp/libs")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../modules/video_transmission/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("djirc") {
                from(components["release"])
                groupId = "tech.alius"
                artifactId = "teleauto-core"
                pom {
                    name.set("Teleauto SDK Core")
                    description.set("UI-free device, input, USB hot-plug, video and MAVLink core for screen controllers.")
                    url.set("https://github.com/AliusTech/dji_rc")
                }
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
        // 可选:Gitea 内置 Maven Registry,提供 -Pgitea.url 时生效,例如
        // ./gradlew :djirc:publishDjircPublicationToGiteaRepository -PsdkVersion=0.1.0 \
        //   -Pgitea.url=https://git.example.com/api/packages/AliusTech/maven \
        //   -Pgitea.user=xxx -Pgitea.token=xxx
        // (token 在 Gitea 设置 → 应用 → 生成令牌,勾选 package 读写权限)
        val giteaUrl = providers.gradleProperty("gitea.url")
        if (giteaUrl.isPresent) {
            maven {
                name = "Gitea"
                url = uri(giteaUrl.get())
                credentials {
                    username = providers.gradleProperty("gitea.user")
                        .getOrElse(System.getenv("GITEA_ACTOR") ?: "")
                    password = providers.gradleProperty("gitea.token")
                        .getOrElse(System.getenv("GITEA_TOKEN") ?: "")
                }
            }
        }
    }
}

// Trust anchors are compiled into the AAR. Host APK assets cannot replace this registry.
val licenseKeysFile = providers.gradleProperty("licensePublicKeysFile")
    .map { rootProject.file(it) }.orElse(provider { file("license-public-keys.properties") })
val generateLicenseKeys by tasks.registering {
    inputs.file(licenseKeysFile)
    inputs.property("sdkVersion", project.version.toString())
    val output = layout.buildDirectory.dir("generated/license/java")
    outputs.dir(output)
    doLast {
        val keys = Properties().apply { licenseKeysFile.get().inputStream().use { load(it) } }
        fun parseVersion(value: String): List<Int> {
            require(value.matches(Regex("(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)"))) { "SDK version must be X.Y.Z" }
            return value.split('.').map { it.toInt() }
        }
        fun compare(a: List<Int>, b: List<Int>): Int =
            a.zip(b).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0
        val sdkVersion = parseVersion(project.version.toString())
        val statements = keys.stringPropertyNames().sorted().mapNotNull { id ->
            val value = keys.getProperty(id).trim()
            require(id.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "Invalid license key ID" }
            require(value.matches(Regex("[0-9]+[.][0-9]+[.][0-9]+[|]([0-9]+[.][0-9]+[.][0-9]+|[*])[|][A-Za-z0-9_-]+"))) { "Invalid license key policy" }
            val parts = value.split('|')
            val from = parseVersion(parts[0])
            val until = parts[1].takeUnless { it == "*" }?.let(::parseVersion)
            require(until == null || compare(from, until) < 0) { "Invalid SDK key window" }
            if (compare(sdkVersion, from) < 0 || (until != null && compare(sdkVersion, until) >= 0)) null
            else "keys.put(\"$id\", \"$value\");"
        }.joinToString("\n")
        val target = output.get().file("tech/alius/rc/license/internal/TeleautoTrustedLicenseKeys.java").asFile
        target.parentFile.mkdirs()
        target.writeText("""package tech.alius.rc.license.internal;
public final class TeleautoTrustedLicenseKeys {
 public static java.util.Map<String,String> records() {
  java.util.Map<String,String> keys = new java.util.HashMap<>();
  $statements
  return java.util.Collections.unmodifiableMap(keys);
 }
}
""")
    }
}
tasks.named("preBuild") { dependsOn(generateLicenseKeys) }
