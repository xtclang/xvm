package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.SemanticModel
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The facts of one module, as text that two compiles of the same give alike. A compile numbers its symbols and types in
 * the order it meets them, which the order of another module's constants decides: each is named by its place among them
 * in the order of a label that holds where its source first names it and its text, in which each symbol or type it
 * refers to is named by its own label, refined a few times over, as two compiles give alike. Any other identity of a
 * compile, new in each, such as an unproven one's, is a mark, and the entries of each collection are in the order of
 * their text, chains keeping theirs. A symbol's dependency names the artifact of another module that the compile read by
 * its revision and constant index, which no reader of these facts uses but to tell that the symbol has one: the symbol's
 * own declaration place, which the text holds, stands for it. A proof's identities keep their artifact keys whole.
 */
internal fun factsText(facts: CompilerRenameFacts?): String {
    if (facts == null) return "null"
    // each symbol's and type's text, and where its source first names it
    val texts = linkedMapOf<String, String>()
    val first = hashMapOf<String, String>()
    facts.models.forEach { model ->
        model.symbols.forEach { texts[it.id.toString()] = DEPENDENCY.replace(it.toString(), "dependency=$1") }
        model.types.forEach { texts[it.id.toString()] = it.toString() }
        // each element of the model that is at a place in the source names the symbols and types it refers to there
        (
            model.occurrences + model.expressions + model.calls + model.functionCalls + model.lambdas + model.sourceLinks +
                model.callables.values + model.typeDeclarations.values
        ).forEach { element ->
            val text = element.toString()
            val at = "${model.sourceName} ${RANGE.find(text)?.value}"
            NUMBERED.findAll(text).forEach { id -> first.merge(id.value, at) { one, other -> minOf(one, other) } }
        }
    }
    var labels = texts.mapValues { (id, text) -> "${first[id]} ${NUMBERED.replace(text, "_")}" }
    repeat(3) {
        val previous = labels
        labels =
            texts.mapValues { (id, text) ->
                "${first[id]} " + NUMBERED.replace(text) { "<" + digest(previous[it.value] ?: "?") + ">" }
            }
    }
    val names = hashMapOf<String, String>()
    labels.entries
        .sortedBy { it.value }
        .forEachIndexed { index, (id, _) -> names[id] = if (id.startsWith("SymbolId")) "S$index" else "T$index" }

    fun text(value: Any?): String =
        IDENTITY.replace(NUMBERED.replace(DEPENDENCY.replace(value.toString(), "dependency=$1")) { names[it.value] ?: "?" }, "#")

    fun all(values: Collection<*>): String = values.map(::text).sorted().toString()

    fun map(values: Map<*, *>): String = all(values.entries.map { "${text(it.key)}=${text(it.value)}" })
    val extraction = facts.extraction
    val inputs = facts.missingMethodInputs
    val text =
        buildString {
            facts.models.forEach { model ->
                appendLine("model ${model.status} ${model.sourceName}")
                appendLine("  symbols ${all(model.symbols)}")
                appendLine("  types ${all(model.types)}")
                appendLine("  occurrences ${all(model.occurrences)}")
                appendLine("  expressions ${all(model.expressions)}")
                appendLine("  calls ${all(model.calls)}")
                appendLine("  functionCalls ${all(model.functionCalls)}")
                appendLine("  lambdas ${all(model.lambdas)}")
                appendLine("  sourceLinks ${all(model.sourceLinks)}")
                appendLine("  imports ${all(model.imports.map { "${it.name} ${it.declaration} ${it.uses}" })}")
                appendLine("  typeDeclarations ${map(model.typeDeclarations)}")
                appendLine("  callables ${map(model.callables)}")
                appendLine("  parameters ${map(model.parameters)}")
                // the tables of go to definition, type definition and implementation, which the model keeps to itself
                val navigation = FACTS.get(model) as SemanticModel.Facts
                appendLine("  declarations ${map(navigation.declarations)}")
                appendLine("  typeDefinitions ${map(navigation.typeDefinitions)}")
                appendLine("  implementations ${map(navigation.implementations)}")
            }
            appendLine("constants ${map(facts.constants)}")
            appendLine("methods ${all(facts.methods.declarations)} ${all(facts.methods.chains)}")
            appendLine("properties ${all(facts.properties.declarations)} ${all(facts.properties.chains)}")
            appendLine("imports ${all(facts.imports)}")
            appendLine("memberActions ${all(facts.memberActions)}")
            appendLine("typeNames ${all(facts.typeNames)}")
            appendLine("typePaths ${all(facts.typePaths)}")
            appendLine("resourceValues ${map(facts.resourceValues)}")
            appendLine("callables ${map(facts.callables)}")
            appendLine("removableLocals ${all(facts.removableLocals)}")
            appendLine("extraction types ${map(extraction.types)} sources ${map(extraction.sourceTypes)}")
            appendLine(
                "extraction values ${all(
                    extraction.stableValues,
                )} ${all(extraction.plainValues)} ${all(extraction.injectedValues)}",
            )
            appendLine("missingMethods ${all(facts.missingMethods)}")
            appendLine("methodSignatures ${map(facts.methodSignatures)}")
            appendLine("missingMethodInputs ${map(inputs.localTypes)} ${map(inputs.receivers)} ${map(inputs.expressions)}")
            appendLine("constantProperties ${all(facts.constantProperties)}")
            appendLine("packageImports ${map(facts.packageImports)}")
            appendLine("binaryModules ${facts.binaryModules}")
            appendLine("exposed ${all(facts.exposed)}")
        }
    // an object without a text of its own would print where it is, which differs in each compile
    assertThat(text).doesNotContainPattern("[A-Za-z]@[0-9a-f]{5,8}\\b")
    return text
}

/** The model's facts, which it holds private: their navigation tables have no other reader. */
private val FACTS = SemanticModel::class.java.getDeclaredField("facts").apply { isAccessible = true }

/** A range in the source. */
private val RANGE = Regex("Range\\(start=Position\\(line=[0-9]+, column=[0-9]+\\), end=Position\\(line=[0-9]+, column=[0-9]+\\)\\)")

/** A short digest of [text]. */
private fun digest(text: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)), 0, 12)

/** A symbol's or type's identity, numbered within its compile. */
private val NUMBERED = Regex("(?:SymbolId|TypeId)\\(snapshot=[0-9a-f-]+, index=[0-9]+\\)")

/** A symbol's dependency, whose revision and index the facts' readers do not use. */
private val DEPENDENCY = Regex("dependency=SymbolKey\\(module=([^,]+), revision=[0-9a-f]+, index=[0-9]+\\)")

private val IDENTITY = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
