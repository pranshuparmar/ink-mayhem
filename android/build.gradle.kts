import javax.inject.Inject

plugins {
    id("com.android.application")
}

val gdxVersion = providers.gradleProperty("gdxVersion").get()
val appVersionName = providers.gradleProperty("appVersionName").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()

// Release signing comes from the environment (CI decodes the keystore from GitHub Secrets).
val releaseKeystore: String? = System.getenv("ANDROID_KEYSTORE_PATH")
// Optional fixed debug key so successive debug APKs install over each other.
val debugKeystore: String? = System.getenv("ANDROID_DEBUG_KEYSTORE_PATH")

android {
    namespace = "com.pranshuparmar.inkmayhem.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pranshuparmar.inkmayhem"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
        if (debugKeystore != null) {
            getByName("debug") {
                storeFile = file(debugKeystore)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf("META-INF/robovm/ios/robovm.xml", "**/*.gwt.xml")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
}

// libGDX ships its .so files inside "natives" jars; extract them into a generated jniLibs folder.
val natives: Configuration by configurations.creating

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

abstract class ExtractGdxNatives : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val nativeJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val archives: ArchiveOperations

    @TaskAction
    fun extract() {
        fs.delete { delete(outputDir) }
        for (jar in nativeJars.files) {
            // e.g. gdx-box2d-platform-1.14.2-natives-arm64-v8a.jar -> arm64-v8a
            val abi = jar.name.substringAfter("-natives-").removeSuffix(".jar")
            fs.copy {
                from(archives.zipTree(jar))
                include("*.so")
                into(outputDir.dir(abi))
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<ExtractGdxNatives>("extract${variant.name.replaceFirstChar { it.uppercase() }}GdxNatives") {
            nativeJars.from(natives)
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(task, ExtractGdxNatives::outputDir)
    }
}
