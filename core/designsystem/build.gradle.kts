plugins {
    id("daftar.kmp.compose")
}

// `-Pdaftar.bankArtwork=official` (default) packages the official bank and broker marks; `monogram` packages none of
// them, on Android and iOS alike. The marks live outside commonMain, and the resources the module compiles are a merged copy.
val bankArtworkMode: String = providers.gradleProperty("daftar.bankArtwork").orElse("official").get()
check(bankArtworkMode == "official" || bankArtworkMode == "monogram") {
    "daftar.bankArtwork must be 'official' or 'monogram', was '$bankArtworkMode'"
}

val mergeComposeResources = tasks.register<Sync>("mergeBankArtworkResources") {
    inputs.property("daftar.bankArtwork", bankArtworkMode)
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    if (bankArtworkMode == "official") {
        from(layout.projectDirectory.dir("src/officialBankArtwork/composeResources"))
    }
    into(layout.buildDirectory.dir("generated/bankArtwork/composeResources"))
}

compose.resources {
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = mergeComposeResources.map { layout.projectDirectory.dir(it.destinationDir.path) },
    )
}

// Compose copies resources into Android assets without removing files that a previous mode added, so switching to the
// monogram build without `clean` would keep stale marks. This check fails that build instead of shipping them.
val verifyBankArtworkAssets = tasks.register("verifyBankArtworkAssets") {
    val assets = layout.buildDirectory.dir("generated/assets/copyAndroidMainComposeResourcesToAndroidAssets")
    val mode = bankArtworkMode
    inputs.property("daftar.bankArtwork", mode)
    inputs.files(assets).optional()
    dependsOn("copyAndroidMainComposeResourcesToAndroidAssets")
    doLast {
        val marks = assets.get().asFile.walkTopDown()
            .filter { it.isFile && (it.name.startsWith("bank_") || it.name.startsWith("broker_")) }
            .count()
        check(mode == "official" || marks == 0) {
            "The monogram build still holds $marks official bank or broker marks from an earlier build; run ./gradlew clean."
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(verifyBankArtworkAssets)
}
