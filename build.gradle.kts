import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

val customerVersion = file("app/pubspec.yaml")
    .readLines()
    .first { it.trimStart().startsWith("version:") }
    .substringAfter("version:")
    .trim()

tasks.register<Exec>("generateCustomerApk") {
    group = "build"
    description = "Builds the VJoyKart Flutter customer release APK."

    val localProperties = Properties().apply {
        file("app/android/local.properties").inputStream().use(::load)
    }
    val flutterSdk = requireNotNull(localProperties.getProperty("flutter.sdk")) {
        "flutter.sdk is not configured in app/android/local.properties"
    }
    val flutterExecutable = if (System.getProperty("os.name").startsWith("Windows")) {
        "$flutterSdk\\bin\\flutter.bat"
    } else {
        "$flutterSdk/bin/flutter"
    }

    workingDir("app")
    commandLine(flutterExecutable, "build", "apk", "--release")

    doLast {
        val source = file("app/build/app/outputs/flutter-apk/app-release.apk")
        val destination = file("app/build/outputs/VJoyKart-Customer-$customerVersion.apk")
        check(source.exists()) { "Flutter did not produce ${source.absolutePath}" }
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        logger.lifecycle("VJoyKart customer APK: ${destination.absolutePath}")
    }
}