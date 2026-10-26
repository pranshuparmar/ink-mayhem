pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ink-mayhem"

include(":core", ":lwjgl3")

// The Android module needs the Android SDK and Google Maven. Include it whenever an SDK is
// configured (always true on CI); -Pinkmayhem.android=true|false overrides the detection.
val localSdk = file("local.properties").takeIf { it.exists() }?.let { f ->
    java.util.Properties().apply { f.inputStream().use { load(it) } }.getProperty("sdk.dir")
}
val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: localSdk
val includeAndroid = providers.gradleProperty("inkmayhem.android").orNull?.toBoolean() ?: (sdkDir != null)
if (includeAndroid) include(":android")
gradle.extensions.extraProperties["inkmayhem.includeAndroid"] = includeAndroid
