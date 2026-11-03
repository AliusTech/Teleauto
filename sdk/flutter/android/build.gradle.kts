group = "tech.alius.rc.flutter"
version = "0.1.0"
plugins { id("com.android.library") }
android {
    namespace = "tech.alius.rc.flutter"
    compileSdk = 37
    defaultConfig { minSdk = 25 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api("tech.alius:teleauto-core:${project.findProperty("teleautoSdkVersion") ?: "0.1.0"}") }
kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}
