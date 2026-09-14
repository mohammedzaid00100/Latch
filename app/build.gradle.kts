import java.security.MessageDigest
import java.net.URI
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zaid.latch"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zaid.latch"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "EXTRACTOR_VERSION", "\"2026.08.19\"")
        buildConfigField("String", "EXTRACTOR_SHA256", "\"1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6\"")
        ndk { abiFilters += (project.findProperty("latchAbis") as? String)?.split(",") ?: listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
    testOptions { animationsDisabled = true }
    lint { abortOnError = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/*
 * Pin the extractor independently from the Android runtime wrapper. The wrapper's
 * original resource is older, and its initializer never refreshes an existing copy.
 * Gradle verifies the official release before packaging it. No runtime updater.
 */
val extractorAssets = layout.buildDirectory.dir("generated/extractorAssets")
val prepareExtractor by tasks.registering {
    val destination = extractorAssets.map { it.file("engine/yt-dlp") }
    outputs.file(destination)
    // Verify cached bytes too, including on offline/source-archive builds.
    outputs.upToDateWhen { false }
    doLast {
        val target = destination.get().asFile
        val expected = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6"
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var count = stream.read(buffer)
                while (count != -1) {
                    hash.update(buffer, 0, count)
                    count = stream.read(buffer)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        if (!target.isFile || digest(target) != expected) {
            target.parentFile.mkdirs()
            val temporary = File(target.parentFile, "yt-dlp.part")
            try {
                val connection = URI("https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp").toURL().openConnection().apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        var total = 0L
                        var count = input.read(buffer)
                        while (count != -1) {
                            total += count
                            check(total <= 25L * 1024 * 1024) { "Unexpected extractor download size." }
                            output.write(buffer, 0, count)
                            count = input.read(buffer)
                        }
                    }
                }
                check(digest(temporary) == expected) { "Extractor SHA-256 verification failed." }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        logger.lifecycle("Verified bundled yt-dlp 2026.08.19")
    }
}
android.sourceSets.getByName("main").assets.srcDir(extractorAssets)
tasks.named("preBuild").configure { dependsOn(prepareExtractor) }
