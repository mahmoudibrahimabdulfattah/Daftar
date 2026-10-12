// Applied by core:model, core:common and core:network. Shared (commonMain) code must not carry a Double/Float
// money path or Java-only parsing, decimal or date APIs. Comment lines are skipped. The check runs before the
// module's test compilation, so it gates every test task.
val architectureGuard = tasks.register("architectureGuard") {
    val commonSources = fileTree("src/commonMain/kotlin") { include("**/*.kt") }
    inputs.files(commonSources)
    doLast {
        val forbidden = listOf(
            Regex("""\bDouble\b"""),
            Regex("""\bFloat\b"""),
            Regex("""\bto(Double|Float)\(\)"""),
            Regex("""\bjava\."""),
            Regex("""\bkotlin\.jvm\."""),
            Regex("""\bJvmInline\b"""),
            Regex("""\bSimpleDateFormat\b"""),
            Regex("""\bkotlinx\.datetime\.Clock\b"""),
        )
        val violations = mutableListOf<String>()
        commonSources.files.sortedBy { it.path }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                forbidden.filter { it.containsMatchIn(code) }.forEach { pattern ->
                    violations += "${file.name}:${index + 1} matches ${pattern.pattern}"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Forbidden API in commonMain:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.configureEach {
    if (name.startsWith("compile") && name.contains("Test")) {
        dependsOn(architectureGuard)
    }
}
