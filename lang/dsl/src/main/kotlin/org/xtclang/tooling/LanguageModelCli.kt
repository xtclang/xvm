/**
 * CLI for the XTC Language Model.
 *
 * Provides commands to:
 * - Dump the language model as JSON
 * - Generate TextMate grammar
 * - Show statistics about the language model
 * - Validate against actual XTC source files
 */
package org.xtclang.tooling

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.xtclang.tooling.generators.EmacsGenerator
import org.xtclang.tooling.generators.SublimeSyntaxGenerator
import org.xtclang.tooling.generators.TextMateBundleManifestGenerator
import org.xtclang.tooling.generators.TextMateGenerator
import org.xtclang.tooling.generators.TreeSitterGenerator
import org.xtclang.tooling.generators.VSCodeConfigGenerator
import org.xtclang.tooling.generators.VimGenerator
import org.xtclang.tooling.model.LanguageModel
import java.io.File
import java.lang.invoke.MethodHandles

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

private val json =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "stats"
    logger.info("Executing command: {}", command)

    when (command) {
        "dump" -> dumpModel()
        "textmate" -> generateTextMate(args.getOrNull(1))
        "sublime" -> generateSublimeSyntax(args.getOrNull(1))
        "vim" -> generateVim(args.getOrNull(1))
        "emacs" -> generateEmacs(args.getOrNull(1))
        "tree-sitter" -> generateTreeSitter(args.getOrNull(1))
        "vscode-config" -> generateVSCodeConfig(args.getOrNull(1))
        "package-json" -> generatePackageJson(args.getOrNull(1))
        "stats" -> showStats()
        "validate" -> validateSources(args.drop(1))
        "help" -> showHelp()
        else -> {
            logger.warn("Unknown command: {}", command)
            println("Unknown command: $command")
            showHelp()
        }
    }
}

private fun dumpModel() {
    logger.info("Dumping language model as JSON")
    println(json.encodeToString(xtcLanguage))
}

private fun generateTextMate(outputPath: String?) {
    logger.info("Generating TextMate grammar")
    val generator = TextMateGenerator(xtcLanguage)
    val grammar = generator.generate()

    if (outputPath != null) {
        File(outputPath).writeText(grammar)
        logger.info("TextMate grammar written to: {}", outputPath)
        println("TextMate grammar written to: $outputPath")
    } else {
        println(grammar)
    }
}

private fun generateSublimeSyntax(outputPath: String?) {
    logger.info("Generating Sublime syntax file")
    val generator = SublimeSyntaxGenerator(xtcLanguage)
    val syntax = generator.generate()
    writeOutput(syntax, outputPath, "Sublime syntax")
}

private fun generateVim(outputPath: String?) {
    logger.info("Generating Vim syntax file")
    val generator = VimGenerator(xtcLanguage)
    val vim = generator.generate()
    writeOutput(vim, outputPath, "Vim syntax")
}

private fun generateEmacs(outputPath: String?) {
    logger.info("Generating Emacs major mode")
    val generator = EmacsGenerator(xtcLanguage)
    val elisp = generator.generate()
    writeOutput(elisp, outputPath, "Emacs mode")
}

private fun generateTreeSitter(outputDir: String?) {
    logger.info("Generating Tree-sitter grammar, config, and highlights")
    // Version is passed via system property from Gradle
    val version =
        System.getProperty("project.version")
            ?: error("project.version system property not set. Run via Gradle task, not directly.")
    val generator = TreeSitterGenerator(xtcLanguage, version)

    val grammar = generator.generateGrammar()
    val config = generator.generateConfig()
    val highlights = generator.generateHighlights()

    if (outputDir != null) {
        val dir = File(outputDir)
        dir.mkdirs()
        File(dir, "grammar.js").writeText(grammar)
        File(dir, "tree-sitter.json").writeText(config + "\n")
        File(dir, "highlights.scm").writeText(highlights)
        logger.info("Tree-sitter files written to: {}", outputDir)
        println("Tree-sitter grammar written to: $outputDir/grammar.js")
        println("Tree-sitter config written to: $outputDir/tree-sitter.json")
        println("Tree-sitter highlights written to: $outputDir/highlights.scm")
    } else {
        println("// grammar.js")
        println(grammar)
        println()
        println("// tree-sitter.json")
        println(config)
        println()
        println("; highlights.scm")
        println(highlights)
    }
}

private fun generateVSCodeConfig(outputPath: String?) {
    logger.info("Generating VS Code language configuration")
    val generator = VSCodeConfigGenerator(xtcLanguage)
    val config = generator.generate()
    writeOutput(config, outputPath, "VS Code config")
}

private fun generatePackageJson(outputPath: String?) {
    logger.info("Generating TextMate bundle manifest (package.json)")
    val version =
        System.getProperty("project.version")
            ?: error("project.version system property not set. Run via Gradle task, not directly.")
    val generator = TextMateBundleManifestGenerator(xtcLanguage, version)
    val packageJson = generator.generate()
    writeOutput(packageJson, outputPath, "TextMate bundle manifest (package.json)")
}

private fun writeOutput(
    content: String,
    outputPath: String?,
    name: String,
) {
    if (outputPath != null) {
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        logger.info("{} written to: {}", name, outputPath)
        println("$name written to: $outputPath")
    } else {
        println(content)
    }
}

private fun showStats() {
    logger.info("Displaying language model statistics")
    val stats = ModelStatistics.from(xtcLanguage)
    println(StatsRenderer.render(stats))
}

private fun validateSources(paths: List<String>) {
    logger.info("Validating XTC source files against language model")
    val model = xtcLanguage

    println("Validating XTC source files against language model...")
    println("Keywords to match: ${model.allKeywords.size}")
    println("Token patterns: ${model.tokens.size}")
    println()

    if (paths.isEmpty()) {
        logger.warn("No paths specified for validation")
        println("No paths specified. Usage: validate <path1> [path2] ...")
        println("Example: validate ../lib_ecstasy/src/main/x")
        return
    }

    var totalFiles = 0
    var totalKeywordsFound = 0
    val keywordCounts = mutableMapOf<String, Int>()

    for (path in paths) {
        val file = File(path)
        if (!file.exists()) {
            logger.warn("Path not found: {}", path)
            println("Path not found: $path")
            continue
        }

        val xtcFiles =
            if (file.isDirectory) {
                file.walkTopDown().filter { it.extension == "x" }.toList()
            } else if (file.extension == "x") {
                listOf(file)
            } else {
                emptyList()
            }

        for (xtcFile in xtcFiles) {
            totalFiles++
            val content = xtcFile.readText()

            for (keyword in model.allKeywords) {
                val regex = Regex("\\b${Regex.escape(keyword)}\\b")
                val matches = regex.findAll(content).count()
                if (matches > 0) {
                    keywordCounts[keyword] = (keywordCounts[keyword] ?: 0) + matches
                    totalKeywordsFound += matches
                }
            }
        }
    }

    logger.info("Scanned {} .x files, found {} keyword occurrences", totalFiles, totalKeywordsFound)
    println("Scanned $totalFiles .x files")
    println("Found $totalKeywordsFound keyword occurrences")
    println()
    println("Top 20 keywords by frequency:")
    keywordCounts.entries
        .sortedByDescending { it.value }
        .take(20)
        .forEachIndexed { i, (kw, count) ->
            println("  ${(i + 1).toString().padStart(2)}. $kw: $count")
        }

    val unusedKeywords = model.allKeywords.filter { it !in keywordCounts }
    if (unusedKeywords.isNotEmpty()) {
        logger.info("Found {} unused keywords", unusedKeywords.size)
        println()
        println("Keywords defined but not found in sources (${unusedKeywords.size}):")
        unusedKeywords.forEach { println("  - $it") }
    }
}

private fun showHelp() {
    println(
        """
        |XTC Language Model CLI
        |
        |Usage: java -jar lang.jar <command> [args]
        |
        |Commands:
        |  stats                  Show statistics about the language model (default)
        |  dump                   Dump the language model as JSON
        |  textmate [output]      Generate TextMate grammar (.tmLanguage.json)
        |  sublime [output]       Generate Sublime syntax file (.sublime-syntax)
        |  vim [output]           Generate Vim syntax file (.vim)
        |  emacs [output]         Generate Emacs major mode (.el)
        |  tree-sitter [dir]      Generate Tree-sitter grammar and highlights
        |  vscode-config [output] Generate VS Code language configuration
        |  package-json [output]  Generate VS Code package.json
        |  validate <paths>       Validate language model against .x source files
        |  help                   Show this help message
        |
        |Gradle Tasks:
        |  ./gradlew languageStats        Show statistics
        |  ./gradlew dumpLanguageModel    Dump model as JSON
        |  ./gradlew generateTextMate     Generate TextMate grammar
        |  ./gradlew generateSublime      Generate Sublime/bat syntax
        |  ./gradlew generateVim          Generate Vim syntax
        |  ./gradlew generateEmacs        Generate Emacs mode
        |  ./gradlew generateTreeSitter   Generate Tree-sitter grammar
        |  ./gradlew generateVSCodeConfig Generate VS Code config
        |  ./gradlew generateAllEditorSupport  Generate all editor support files
        """.trimMargin(),
    )
}

/**
 * Data-driven statistics model for the language.
 * All statistics are computed once and stored in a structured format.
 */
data class ModelStatistics(
    val name: String,
    val fileExtensions: List<String>,
    val scopeName: String,
    val lexicalStats: Map<String, Int>,
    val semanticStats: Map<String, Int>,
    val operatorsByPrecedence: Map<Int, List<String>>,
    val conceptHierarchy: List<ConceptNode>,
) {
    data class ConceptNode(
        val name: String,
        val isAbstract: Boolean,
        val children: List<ConceptNode>,
    )

    companion object {
        fun from(model: LanguageModel): ModelStatistics {
            val rootConcepts = model.concepts.filter { it.parentConcept == null }

            fun buildNode(conceptName: String): ConceptNode {
                val concept = model.getConcept(conceptName)
                val children =
                    model.concepts
                        .filter { it.parentConcept == conceptName }
                        .map { buildNode(it.name) }
                return ConceptNode(
                    name = conceptName,
                    isAbstract = concept?.isAbstract ?: false,
                    children = children,
                )
            }

            return ModelStatistics(
                name = model.name,
                fileExtensions = model.fileExtensions,
                scopeName = model.scopeName,
                lexicalStats =
                    mapOf(
                        "Reserved Keywords" to model.keywords.size,
                        "Context Keywords" to model.contextKeywords.size,
                        "Token Rules" to model.tokens.size,
                        "Operators" to model.operators.size,
                        "Punctuation" to model.punctuation.size,
                    ),
                semanticStats =
                    mapOf(
                        "Scopes" to model.scopes.size,
                        "Total AST Concepts" to model.concepts.size,
                        "Abstract Concepts" to model.abstractConcepts.size,
                        "Concrete Concepts" to model.concreteConcepts.size,
                    ),
                operatorsByPrecedence =
                    model.operators
                        .groupBy { it.precedence }
                        .mapValues { (_, ops) -> ops.map { it.symbol } }
                        .toSortedMap(),
                conceptHierarchy = rootConcepts.map { buildNode(it.name) },
            )
        }
    }
}

/**
 * Renders statistics in a beautiful box-drawing format.
 * All widths are computed dynamically from the data.
 */
object StatsRenderer {
    private const val BOX_WIDTH = 64

    fun render(stats: ModelStatistics): String =
        buildString {
            appendHeader("XTC (Ecstasy) Language Model Statistics")
            appendSeparator()

            // Metadata section
            appendRow("Language", stats.name)
            appendRow("File Extensions", stats.fileExtensions.joinToString(", "))
            appendRow("Scope Name", stats.scopeName)
            appendSeparator()

            // Lexical elements section
            appendSectionHeader("LEXICAL ELEMENTS")
            stats.lexicalStats.forEach { (label, value) ->
                appendRow(label, value.toString())
            }
            appendSeparator()

            // Semantic elements section
            appendSectionHeader("SEMANTIC ELEMENTS")
            stats.semanticStats.forEach { (label, value) ->
                appendRow(label, value.toString())
            }
            appendSeparator()

            // Operator precedence section
            appendSectionHeader("OPERATOR PRECEDENCE LEVELS")
            stats.operatorsByPrecedence.forEach { (prec, ops) ->
                val opsStr = ops.joinToString(" ")
                appendRow("Level $prec", opsStr.take(45))
            }
            appendSeparator()

            // Concept hierarchy section
            appendSectionHeader("AST CONCEPT HIERARCHY")
            renderConceptHierarchy(stats.conceptHierarchy, maxRoots = 10, maxChildren = 5)

            appendFooter()
        }

    private fun StringBuilder.appendHeader(title: String) {
        appendLine("╔${"═".repeat(BOX_WIDTH - 2)}╗")
        appendLine("║${title.center(BOX_WIDTH - 2)}║")
    }

    private fun StringBuilder.appendSeparator() {
        appendLine("╠${"═".repeat(BOX_WIDTH - 2)}╣")
    }

    private fun StringBuilder.appendFooter() {
        appendLine("╚${"═".repeat(BOX_WIDTH - 2)}╝")
    }

    private fun StringBuilder.appendSectionHeader(title: String) {
        appendLine("║  $title${" ".repeat(BOX_WIDTH - title.length - 4)}║")
        appendLine("║  ${"─".repeat(BOX_WIDTH - 6)}  ║")
    }

    private fun StringBuilder.appendRow(
        label: String,
        value: String,
    ) {
        val content = "  $label: $value"
        appendLine("║${content.padEnd(BOX_WIDTH - 2)}║")
    }

    private fun StringBuilder.renderConceptHierarchy(
        roots: List<ModelStatistics.ConceptNode>,
        maxRoots: Int,
        maxChildren: Int,
    ) {
        val displayedRoots = roots.take(maxRoots)
        for (root in displayedRoots) {
            val marker = if (root.isAbstract) " (abstract)" else ""
            val content = "  ${root.name}$marker"
            appendLine("║${content.padEnd(BOX_WIDTH - 2)}║")

            val displayedChildren = root.children.take(maxChildren)
            for (child in displayedChildren) {
                val childMarker = if (child.isAbstract) " (abstract)" else ""
                val childContent = "    └─ ${child.name}$childMarker"
                appendLine("║${childContent.padEnd(BOX_WIDTH - 2)}║")
            }
            if (root.children.size > maxChildren) {
                val moreContent = "    └─ ... and ${root.children.size - maxChildren} more"
                appendLine("║${moreContent.padEnd(BOX_WIDTH - 2)}║")
            }
        }
        if (roots.size > maxRoots) {
            val moreContent = "  ... and ${roots.size - maxRoots} more root concepts"
            appendLine("║${moreContent.padEnd(BOX_WIDTH - 2)}║")
        }
    }

    private fun String.center(width: Int): String {
        if (length >= width) return this
        val padding = width - length
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return " ".repeat(leftPad) + this + " ".repeat(rightPad)
    }
}
