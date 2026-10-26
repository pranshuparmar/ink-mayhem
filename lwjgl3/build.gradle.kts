import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

val gdxVersion = providers.gradleProperty("gdxVersion").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

// One asset folder for every platform: the Android default location.
val assetsDir = rootProject.file("android/src/main/assets")
sourceSets["main"].resources.srcDir(assetsDir)

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

application {
    mainClass.set("com.pranshuparmar.inkmayhem.lwjgl3.Lwjgl3LauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = assetsDir
    // Pass -Pargs="--portrait" etc. through to the launcher.
    providers.gradleProperty("args").orNull?.let { args(it.split(' ')) }
}
