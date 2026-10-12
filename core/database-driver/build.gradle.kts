import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("daftar.kmp.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

/*
 * SQLCipher Community Edition for iOS is compiled from the official source tag, never fetched as a binary. The
 * archive is pinned by SHA-256 and verified on every build; a mismatch fails the build.
 *
 * The source is staged under the Gradle user home, not the project build directory: SQLCipher's configure script
 * rejects source paths that contain spaces, and this checkout lives under "Mobile App Projects".
 */
val sqlcipherVersion = "4.19.0"
val sqlcipherArchiveSha256 = "7075f96cbabe45b4ecfc2e6b1745a625f856f695b0827a5506ce9ed85b906aa0"
val sqlcipherIosMinVersion = "16.0"

val sqlcipherStaging: File = gradle.gradleUserHomeDir.resolve("caches/daftar-sqlcipher/$sqlcipherVersion")
val sqlcipherArchiveFile: File = sqlcipherStaging.resolve("sqlcipher-v$sqlcipherVersion.tar.gz")
val sqlcipherSourceDir: File = sqlcipherStaging.resolve("sqlcipher-$sqlcipherVersion")
val sqlcipherIosDir: File = layout.buildDirectory.dir("sqlcipher/ios").get().asFile
val sqlcipherBuildScript: File = file("sqlcipher/build-sqlcipher-ios.sh")

val downloadSqlCipherSource = tasks.register("downloadSqlCipherSource") {
    val archive = sqlcipherArchiveFile
    val expectedSha256 = sqlcipherArchiveSha256
    val url = "https://github.com/sqlcipher/sqlcipher/archive/refs/tags/v$sqlcipherVersion.tar.gz"
    outputs.file(archive)
    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!archive.exists() || sha256(archive) != expectedSha256) {
            archive.parentFile.mkdirs()
            archive.delete()
            URI(url).toURL().openStream().use { input ->
                archive.outputStream().use { input.copyTo(it) }
            }
        }
        val actual = sha256(archive)
        if (actual != expectedSha256) {
            archive.delete()
            throw GradleException("SQLCipher archive SHA-256 mismatch: expected $expectedSha256, got $actual")
        }
    }
}

val extractSqlCipherSource = tasks.register<Exec>("extractSqlCipherSource") {
    dependsOn(downloadSqlCipherSource)
    val archive = sqlcipherArchiveFile
    val staging = sqlcipherStaging
    val source = sqlcipherSourceDir
    inputs.file(archive)
    outputs.dir(source)
    doFirst {
        source.deleteRecursively()
    }
    workingDir(staging)
    commandLine("tar", "-xzf", archive.absolutePath, "-C", staging.absolutePath)
}

// The CommonCrypto provider and codec are selected at compile time by the flags in build-sqlcipher-ios.sh. Configure
// only enables the temp-store setting and skips the Tcl test tooling.
val generateSqlCipherAmalgamation = tasks.register<Exec>("generateSqlCipherAmalgamation") {
    dependsOn(extractSqlCipherSource)
    val source = sqlcipherSourceDir
    inputs.dir(source)
    outputs.file(source.resolve("sqlite3.c"))
    outputs.file(source.resolve("sqlite3.h"))
    workingDir(source)
    environment("CFLAGS", "-DSQLITE_HAS_CODEC -DSQLCIPHER_CRYPTO_CC -DSQLITE_TEMP_STORE=3")
    environment("LDFLAGS", "-framework Security -framework Foundation")
    commandLine(
        "/bin/bash",
        "-c",
        "./configure --with-tempstore=yes --disable-tcl --disable-shared >configure.log 2>&1 && make sqlite3.c >make.log 2>&1",
    )
}

val buildSqlCipherIos = tasks.register<Exec>("buildSqlCipherIos") {
    dependsOn(generateSqlCipherAmalgamation)
    val source = sqlcipherSourceDir
    val out = sqlcipherIosDir
    val script = sqlcipherBuildScript
    inputs.dir(source)
    inputs.file(script)
    outputs.file(out.resolve("device/libsqlcipher.a"))
    outputs.file(out.resolve("simulator/libsqlcipher.a"))
    commandLine("/bin/bash", script.absolutePath, source.absolutePath, out.absolutePath, sqlcipherIosMinVersion)
}

kotlin {
    // Device-side tests open real SQLCipher databases through Zetetic's Android build and Room. Host tests stay in
    // commonTest and cover only pure code.
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val libSlice = when (konanTarget.name) {
            "ios_arm64" -> "device"
            "ios_simulator_arm64" -> "simulator"
            else -> throw GradleException("Unexpected iOS target ${konanTarget.name}")
        }
        compilations.getByName("main").cinterops.create("sqlcipher") {
            includeDirs(sqlcipherSourceDir)
            // sqlite3_key is declared only under SQLITE_HAS_CODEC in SQLCipher's header; the bridge keys through it.
            compilerOpts("-DSQLITE_HAS_CODEC")
            extraOpts("-libraryPath", sqlcipherIosDir.resolve(libSlice).absolutePath)
            tasks.named(interopProcessingTaskName) { dependsOn(buildSqlCipherIos) }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.security)
            api(libs.androidx.sqlite)
        }
        androidMain.dependencies {
            implementation(libs.sqlcipher.android)
        }
        // Room-backed integration tests run on the Android device and on iOS. Both compile the same test-only
        // database from databaseTest/, so the entity, DAO and assertions exist once.
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.junit)
            }
        }
        // The iOS test source set is created by the Kotlin hierarchy after this block runs.
        // The SQLCipher bindings are per-target: the cinterop of each iOS compilation is visible only to its own
        // main source set, so the native bridge is attached to both target source sets from one directory.
        getByName("iosArm64Main") { kotlin.srcDir("src/iosNative/kotlin") }
        getByName("iosSimulatorArm64Main") { kotlin.srcDir("src/iosNative/kotlin") }
        matching { it.name == "iosTest" }.configureEach {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.room.runtime)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Room's compiler runs only for the test-only database. The production module declares no entities yet.
dependencies {
    listOf("kspAndroidDeviceTest", "kspIosArm64Test", "kspIosSimulatorArm64Test").forEach {
        add(it, libs.androidx.room.compiler)
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
