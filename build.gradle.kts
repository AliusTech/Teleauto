plugins {
    id("com.android.library") version "9.3.1" apply false
}

// SDK releases have their own version, independent of the Teleauto applications.
// CI may override published SDK builds with -PsdkVersion=x.y.z.
val productVersion = providers.gradleProperty("sdkVersion")
    .orElse(providers.provider { file("VERSION").readText().trim() })
    .get()

allprojects {
    version = productVersion
}
