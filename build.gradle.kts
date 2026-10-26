buildscript {
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    val agpVersion = providers.gradleProperty("agpVersion").get()
    val includeAndroid = gradle.extensions.extraProperties["inkmayhem.includeAndroid"] as Boolean
    repositories {
        mavenCentral()
        if (includeAndroid) google()
    }
    dependencies {
        // AGP and KGP share the root classloader. AGP 9 compiles Kotlin itself (built-in Kotlin)
        // and picks up this newer KGP from the root classpath.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        if (includeAndroid) classpath("com.android.tools.build:gradle:$agpVersion")
    }
}
