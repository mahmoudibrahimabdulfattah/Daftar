import groovy.json.JsonSlurper

plugins {
    id("daftar.kmp.library")
}

// Bank and broker directories and the artwork switch. Each directory is a checked-in JSON file; the build turns it into
// one immutable Kotlin object for every platform, through the same generator. The artwork mode is a build property,
// never a user preference: `-Pdaftar.bankArtwork=official` (default) or `-Pdaftar.bankArtwork=monogram`. Any other
// value fails configuration.
val bankArtworkMode: String = providers.gradleProperty("daftar.bankArtwork").orElse("official").get()
check(bankArtworkMode == "official" || bankArtworkMode == "monogram") {
    "daftar.bankArtwork must be 'official' or 'monogram', was '$bankArtworkMode'"
}

/** The v1 bank identities. Future countries add data only; these fourteen are fixed by the design. */
val v1EgyptIds: Set<String> = setOf(
    "eg.nbe", "eg.banque-misr", "eg.cib", "eg.qnb", "eg.banque-du-caire", "eg.aaib", "eg.hsbc",
    "eg.alexbank", "eg.faisal", "eg.adib", "eg.emirates-nbd", "eg.credit-agricole", "eg.bank-abc", "eg.egebank",
)

/** The v1 broker identities: Thndr only, plus the Other action, which stores no ID. */
val v1BrokerIds: Set<String> = setOf("eg.thndr")

/** One directory the generator turns into Kotlin. Read at configuration time only; the task action captures plain values. */
class DirectorySpec(
    val taskName: String,
    val source: File,
    val manifest: File,
    val packageName: String,
    val objectName: String,
    val outputFile: String,
    val expectedEgyptIds: Set<String>,
    val requiredMonogramForeground: String?,
    val includesArtworkConfig: Boolean,
)

// The generator runs inside each task action. Script-level functions and classes must not be referenced from there:
// the configuration cache cannot serialize the script object they belong to, so every helper is a local.
val directories = listOf(
    DirectorySpec(
        taskName = "generateBankDirectory",
        source = layout.projectDirectory.file("src/bankDirectory/banks.json").asFile,
        manifest = rootProject.file("docs/design/bank-marks/manifest.json"),
        packageName = "com.mk.daftar.core.model.bank",
        objectName = "BankDirectoryData",
        outputFile = "BankDirectoryData.kt",
        expectedEgyptIds = v1EgyptIds,
        requiredMonogramForeground = "#FFFFFF",
        includesArtworkConfig = true,
    ),
    DirectorySpec(
        taskName = "generateBrokerDirectory",
        source = layout.projectDirectory.file("src/brokerDirectory/brokers.json").asFile,
        manifest = rootProject.file("docs/design/broker-marks/manifest.json"),
        packageName = "com.mk.daftar.core.model.broker",
        objectName = "BrokerDirectoryData",
        outputFile = "BrokerDirectoryData.kt",
        expectedEgyptIds = v1BrokerIds,
        requiredMonogramForeground = null,
        includesArtworkConfig = false,
    ),
)

val artworkRoot = rootProject.file("core/designsystem/src/officialBankArtwork/composeResources")

val generatedDirectoryTasks = directories.map { spec ->
    tasks.register(spec.taskName) {
        // Plain values only: every name below is a local, so the action serializes without the script.
        val artworkMode = bankArtworkMode
        val source: File = spec.source
        val manifest: File = spec.manifest
        val artwork: File = artworkRoot
        val packageName: String = spec.packageName
        val objectName: String = spec.objectName
        val outputFileName: String = spec.outputFile
        val expectedEgyptIds: Set<String> = spec.expectedEgyptIds
        val requiredMonogramForeground: String? = spec.requiredMonogramForeground
        val includesArtworkConfig: Boolean = spec.includesArtworkConfig
        val taskName: String = spec.taskName
        val outputDir = layout.buildDirectory.dir("generated/$taskName/commonMain/kotlin").get().asFile
        inputs.file(source)
        inputs.file(manifest)
        inputs.dir(artwork)
        inputs.property("daftar.bankArtwork", artworkMode)
        outputs.dir(outputDir)
        doLast {
            val root = JsonSlurper().parse(source) as Map<*, *>
            val manifestText = manifest.readText()
            val hexColour = Regex("#[0-9A-F]{6}")
            val institutionIdPattern = Regex("[a-z]{2}\\.[a-z0-9]+(?:[.-][a-z0-9]+)*")
            val officialBackings = setOf("white", "brand", "dark")
            check(root["schemaVersion"] == 1) { "${source.name} schemaVersion must be 1" }
            val countries = (root["countries"] as List<*>).map { it as Map<*, *> }
            val countryCodes = countries.map { it["code"] as String }.toSet()
            check(countryCodes.size == countries.size) { "duplicate country code in ${source.name}" }
            val institutions = (root["institutions"] as List<*>).map { it as Map<*, *> }
            val ids = institutions.map { it["id"] as String }
            check(ids.toSet().size == ids.size) { "duplicate institution id in ${source.name}" }
            institutions.forEach { entry ->
                val id = entry["id"] as String
                check(id.length <= 64 && institutionIdPattern.matches(id)) { "invalid institution id '$id'" }
                check((entry["country"] as String) in countryCodes) { "institution '$id' names an unknown country" }
                check(hexColour.matches(entry["brandBackground"] as String)) { "bad brand colour for '$id'" }
                val foreground = entry["monogramForeground"] as String
                check(hexColour.matches(foreground)) { "bad monogram colour for '$id'" }
                requiredMonogramForeground?.let {
                    check(foreground == it) { "monogram initials must be $it for '$id'" }
                }
                check((entry["monogram"] as String).length in 1..4) { "monogram must be 1 to 4 characters for '$id'" }
                if (entry["officialAsset"] != null) {
                    check(entry["manifestRef"] != null) { "official asset of '$id' needs a manifest record" }
                    val backing = entry["officialBacking"]
                    check(backing in officialBackings) {
                        "official asset of '$id' needs a backing of white, brand or dark"
                    }
                    if (backing == "dark") {
                        val background = entry["officialBackground"] as String?
                        check(background != null && hexColour.matches(background)) {
                            "dark backing of '$id' needs an official background of #RRGGBB"
                        }
                    } else {
                        check(entry["officialBackground"] == null) {
                            "only a dark backing of '$id' carries a background"
                        }
                    }
                    if (artworkMode == "official") {
                        val png = artwork.resolve("drawable-xxhdpi/${entry["officialAsset"]}.png")
                        check(png.isFile) { "official mode needs $png for '$id'" }
                        check(manifestText.contains("\"institutionId\": \"$id\"")) { "no manifest record for '$id'" }
                    }
                }
            }
            if (expectedEgyptIds.isNotEmpty()) {
                val egypt = institutions.map { it["id"] as String }.filter { it.startsWith("eg.") }.toSet()
                check(egypt == expectedEgyptIds) {
                    "the Egyptian v1 directory ${source.name} holds the wrong identities"
                }
            }

            fun kotlinString(text: String): String = buildString {
                append('"')
                text.forEach { ch ->
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '$' -> append("\\$")
                        '\n' -> append("\\n")
                        else -> append(ch)
                    }
                }
                append('"')
            }
            fun optional(value: Any?): String = value?.let { kotlinString(it as String) } ?: "null"
            fun list(values: List<*>): String = values.joinToString(", ", "listOf(", ")") { kotlinString(it as String) }

            // The broker directory reuses the bank model types, so it imports them from the bank package.
            val modelImports = if (packageName == "com.mk.daftar.core.model.bank") {
                ""
            } else {
                "import com.mk.daftar.core.model.bank.BankCountry\nimport com.mk.daftar.core.model.bank.BankInstitution\n"
            }
            val body = buildString {
                appendLine("// Generated by $taskName from ${source.name}. Do not edit.")
                appendLine("package $packageName")
                appendLine()
                appendLine("import com.mk.daftar.core.model.InstitutionId")
                append(modelImports)
                appendLine()
                appendLine("/** The bundled $objectName, version ${root["directoryVersion"]}. Immutable. */")
                appendLine("object $objectName {")
                appendLine("    const val DIRECTORY_VERSION: Int = ${root["directoryVersion"]}")
                appendLine()
                appendLine("    val countries: List<BankCountry> = listOf(")
                countries.forEach { c ->
                    appendLine(
                        "        BankCountry(${kotlinString(c["code"] as String)}, ${kotlinString(
                            c["nameAr"] as String,
                        )}, ${kotlinString(c["nameEn"] as String)}),",
                    )
                }
                appendLine("    )")
                appendLine()
                appendLine("    val institutions: List<BankInstitution> = listOf(")
                institutions.forEach { e ->
                    appendLine("        BankInstitution(")
                    appendLine("            id = InstitutionId(${kotlinString(e["id"] as String)}),")
                    appendLine("            countryCode = ${kotlinString(e["country"] as String)},")
                    appendLine("            nameAr = ${kotlinString(e["nameAr"] as String)},")
                    appendLine("            nameEn = ${kotlinString(e["nameEn"] as String)},")
                    appendLine("            aliasesAr = ${list(e["aliasesAr"] as List<*>)},")
                    appendLine("            aliasesEn = ${list(e["aliasesEn"] as List<*>)},")
                    appendLine("            monogram = ${kotlinString(e["monogram"] as String)},")
                    appendLine("            brandBackground = ${kotlinString(e["brandBackground"] as String)},")
                    appendLine("            monogramForeground = ${kotlinString(e["monogramForeground"] as String)},")
                    appendLine("            officialAsset = ${optional(e["officialAsset"])},")
                    appendLine("            manifestRef = ${optional(e["manifestRef"])},")
                    appendLine("            officialBacking = ${optional(e["officialBacking"])},")
                    appendLine("            officialBackground = ${optional(e["officialBackground"])},")
                    appendLine("        ),")
                }
                appendLine("    )")
                appendLine("}")
                if (includesArtworkConfig) {
                    appendLine()
                    appendLine("/** The build's artwork mode, fixed at compile time. */")
                    appendLine("object BankArtworkConfig {")
                    appendLine(
                        "    val mode: BankArtworkMode = BankArtworkMode.${if (artworkMode == "monogram") "MONOGRAM" else "OFFICIAL"}",
                    )
                    appendLine("}")
                }
            }
            val packageDir = File(outputDir, packageName.replace('.', '/'))
            packageDir.mkdirs()
            File(packageDir, outputFileName).writeText(body, Charsets.UTF_8)
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.bignum)
        }
    }
}

// Registering each task itself as a source directory gives the common compilation the dependency.
generatedDirectoryTasks.forEach { task -> kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(task) }

apply(from = rootProject.file("core/architecture-guard.gradle.kts"))
