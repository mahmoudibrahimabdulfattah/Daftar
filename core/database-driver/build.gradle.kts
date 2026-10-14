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
 *
 * Each checkout stages into its own directory, keyed by its absolute path. Two checkouts building at once never
 * download, extract or delete the same files.
 */
val sqlcipherVersion = "4.19.0"
val sqlcipherArchiveSha256 = "7075f96cbabe45b4ecfc2e6b1745a625f856f695b0827a5506ce9ed85b906aa0"
val sqlcipherIosMinVersion = "16.0"

val sqlcipherCheckoutKey: String = MessageDigest.getInstance("SHA-256")
    .digest(rootProject.projectDir.absolutePath.toByteArray())
    .take(8)
    .joinToString("") { "%02x".format(it) }
val sqlcipherStaging: File = gradle.gradleUserHomeDir.resolve(
    "caches/daftar-sqlcipher/$sqlcipherVersion-$sqlcipherCheckoutKey",
)
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
    // The amalgamation step builds host tools (jimsh). An Xcode build exports SDKROOT and deployment targets, which
    // would make `cc` target iOS, so the host environment is restored here.
    listOf("SDKROOT", "IPHONEOS_DEPLOYMENT_TARGET", "PLATFORM_NAME", "ARCHS", "CC", "CPP", "LD").forEach {
        environment.remove(it)
    }
    environment("CFLAGS", "-DSQLITE_HAS_CODEC -DSQLCIPHER_CRYPTO_CC -DSQLITE_TEMP_STORE=3")
    environment("LDFLAGS", "-framework Security -framework Foundation")
    // Host tools need the macOS SDK explicitly: the Xcode run has no SDKROOT here, and configure's compiler probe
    // otherwise fails to find stdlib.h.
    commandLine(
        "/bin/bash",
        "-c",
        "export SDKROOT=\"\$(/usr/bin/xcrun --sdk macosx --show-sdk-path)\" && " +
            "./configure --with-tempstore=yes --disable-tcl --disable-shared >configure.log 2>&1 && " +
            "make sqlite3.c >make.log 2>&1",
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

/*
 * Desktop: SQLite3 Multiple Ciphers in its SQLCipher v4 scheme, the same page format and raw-key keying the phones use.
 * macOS compiles the official amalgamation from source into a universal dylib; Windows uses the project's official
 * x64 DLL. Both archives are pinned by SHA-256 (the values in the release's SHA256SUMS) and verified on every build.
 */
val sqlite3mcVersion = "2.5.1"
val sqlite3mcSqliteVersion = "3.53.4"
val sqlite3mcAmalgamationSha256 = "4125f8ff275ea953dabb3289331b20a0e76d4fc060f57148f4a5df3bf3b0d5e0"
val sqlite3mcWin64Sha256 = "858c4f2e9e262caca06e6c4fafee62d13dd90c21b649b047f17992bf0402981e"
val sqlite3mcStaging: File = gradle.gradleUserHomeDir.resolve("caches/daftar-sqlite3mc/$sqlite3mcVersion")
val sqlite3mcNativeDir: File = layout.buildDirectory.dir("sqlite3mc/resources").get().asFile
val sqlite3mcRelease = "https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$sqlite3mcVersion"

fun pinnedDownload(name: String, url: String, target: File, expectedSha256: String) = tasks.register(name) {
    outputs.file(target)
    doLast {
        fun sha256(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!target.exists() || sha256(target) != expectedSha256) {
            target.parentFile.mkdirs()
            target.delete()
            URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        val actual = sha256(target)
        if (actual != expectedSha256) {
            target.delete()
            throw GradleException("${target.name} SHA-256 mismatch: expected $expectedSha256, got $actual")
        }
    }
}

val sqlite3mcAmalgamationZip = sqlite3mcStaging.resolve("sqlite3mc-$sqlite3mcVersion-amalgamation.zip")
val sqlite3mcWin64Zip = sqlite3mcStaging.resolve("sqlite3mc-$sqlite3mcVersion-win64.zip")
val downloadSqlite3mcAmalgamation = pinnedDownload(
    "downloadSqlite3mcAmalgamation",
    "$sqlite3mcRelease/sqlite3mc-$sqlite3mcVersion-sqlite-$sqlite3mcSqliteVersion-amalgamation.zip",
    sqlite3mcAmalgamationZip,
    sqlite3mcAmalgamationSha256,
)
val downloadSqlite3mcWin64 = pinnedDownload(
    "downloadSqlite3mcWin64",
    "$sqlite3mcRelease/sqlite3mc-$sqlite3mcVersion-sqlite-$sqlite3mcSqliteVersion-win64.zip",
    sqlite3mcWin64Zip,
    sqlite3mcWin64Sha256,
)

// JNA looks for a bundled library under its platform prefix on the classpath, so each OS gets its own folder.
val buildSqlite3mcMac = tasks.register<Exec>("buildSqlite3mcMac") {
    dependsOn(downloadSqlite3mcAmalgamation)
    val zip = sqlite3mcAmalgamationZip
    val work = sqlite3mcStaging.resolve("amalgamation")
    val out = sqlite3mcNativeDir.resolve("darwin-aarch64/libsqlite3mc.dylib")
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    inputs.file(zip)
    outputs.file(out)
    outputs.file(sqlite3mcNativeDir.resolve("darwin-x86-64/libsqlite3mc.dylib"))
    doFirst {
        work.deleteRecursively()
        work.mkdirs()
        out.parentFile.mkdirs()
    }
    workingDir(work)
    // Threads are serialized by the connection (FULLMUTEX at open). Temp files stay in memory, like the phones.
    commandLine(
        "/bin/bash",
        "-c",
        "export SDKROOT=\"\$(/usr/bin/xcrun --sdk macosx --show-sdk-path)\" && " +
            "/usr/bin/unzip -oq '${zip.absolutePath}' && " +
            "clang -O2 -dynamiclib -arch arm64 -arch x86_64 -mmacosx-version-min=12.0 " +
            "-DSQLITE_THREADSAFE=1 -DSQLITE_TEMP_STORE=3 -DSQLITE_OMIT_LOAD_EXTENSION -DSQLITE_DQS=0 " +
            "-install_name @rpath/libsqlite3mc.dylib -o '${out.absolutePath}' sqlite3mc_amalgamation.c && " +
            "mkdir -p '${sqlite3mcNativeDir.resolve("darwin-x86-64").absolutePath}' && " +
            "cp '${out.absolutePath}' '${sqlite3mcNativeDir.resolve("darwin-x86-64/libsqlite3mc.dylib").absolutePath}'",
    )
}

val extractSqlite3mcWin64 = tasks.register<Copy>("extractSqlite3mcWin64") {
    dependsOn(downloadSqlite3mcWin64)
    from(zipTree(sqlite3mcWin64Zip)) {
        include("dll/sqlite3mc_x64.dll")
        eachFile { path = "win32-x86-64/sqlite3mc.dll" }
        includeEmptyDirs = false
    }
    into(sqlite3mcNativeDir)
}

val sqlite3mcNatives = tasks.register("sqlite3mcNatives") {
    dependsOn(buildSqlite3mcMac, extractSqlite3mcWin64)
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
        getByName("desktopMain") {
            resources.srcDir(sqlite3mcNatives.map { sqlite3mcNativeDir })
            dependencies {
                implementation(libs.jna)
            }
        }
        // Desktop runs the same Room integration tests on the JVM, over the bundled SQLite3 Multiple Ciphers.
        getByName("desktopTest") {
            kotlin.srcDir("src/databaseTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.room.runtime)
            }
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
    listOf("kspAndroidDeviceTest", "kspIosArm64Test", "kspIosSimulatorArm64Test", "kspDesktopTest").forEach {
        add(it, libs.androidx.room.compiler)
    }
}

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
