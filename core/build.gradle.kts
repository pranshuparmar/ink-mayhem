import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

val gdxVersion = providers.gradleProperty("gdxVersion").get()
val junitVersion = providers.gradleProperty("junitVersion").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-box2d:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Headless physics: the desktop Box2D natives run on the build JVM.
    testRuntimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    testRuntimeOnly("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging {
        events("failed", "skipped")
        showStandardStreams = providers.gradleProperty("inkmayhem.testOutput").isPresent
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Regenerates the synthesized sound effects committed under android/src/main/assets/sfx.
tasks.register<JavaExec>("generateSfx") {
    group = "assets"
    description = "Synthesizes the WAV sound effects into the shared assets folder."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.pranshuparmar.inkmayhem.audio.SfxGenerator")
    args(rootProject.file("android/src/main/assets/sfx").absolutePath)
}
