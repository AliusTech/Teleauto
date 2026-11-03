plugins { id("com.android.application") version "9.3.1" }
android {
 namespace = "tech.alius.rc.inputprobe"
 compileSdk = 37
 buildToolsVersion = "36.0.0"
 defaultConfig { applicationId = "tech.alius.rc.inputprobe"; minSdk = 25; targetSdk = 25; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
