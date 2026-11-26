package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.model.OnDispatcher

/** X352, registered after X351 in catalog order: a loop's label, its uses, and its rename. */
internal fun ParityScenarios.labelCases() {
    case("X352") { data ->
        val document = openModule(data)
        val name = data.string("name")
        val uses = document.uses(data)
        val (declaration, first) = uses
        val broken = uses.last()
        with(driver) { nativeHover(document, first, data.pattern("hover")) }
        checkUses(document, declaration, uses, name, highlights = true)
        check(targets(document, "definition", broken).map { it["range"] } == listOf(sourceRange(document, declaration, name.length))) {
            "Go to Definition from break"
        }
        // renamed span, the label would hide the property span from the loop, whose span.first would read the label's
        check(proposedRename(document, declaration, data.string("refusedName")).isJsonNull)
        applyRename(document, declaration, data.string("newName"))
        check(document.text == data.string("source").replace(name, data.string("newName"))) { document.text }
    }
}

/**
 * X353, registered after X352 in catalog order: a parameter of a constructor with a finally block, which the compiler
 * compiles as a method of its own over the same parameters, from its use in the body and from a named argument.
 */
internal fun ParityScenarios.constructorFinallyCases() {
    case("X353") { data ->
        val document = openModule(data)
        val name = data.string("name")
        data["from"].asJsonArray.map { it.asInt }.forEach { from ->
            replace(document, data.string("source"))
            val uses = document.uses(data)
            checkUses(document, uses[from], uses, name, highlights = false)
            applyRename(document, uses[from], data.string("newName"))
            check(
                document.text == data.string("source").replace(name, data.string("newName")),
            ) { "Rename from ${uses[from]}: ${document.text}" }
        }
    }
}

/** The offsets of the case's name at each of its uses, an anchor and the name's offset in it. */
private fun ParityWorkspace.Document.uses(data: JsonObject): List<Int> =
    data["uses"].rows().map { at(it.string("anchor"), it.int("offset")) }

/**
 * Checks that the server's references from [at] in [document], and its native Find Usages there, are [name] at [uses], its
 * offsets, the first its declaration, which the usage view may leave out; and, with [highlights], that Highlight Usages
 * highlights each of them.
 */
private fun ParityWorkspace.checkUses(
    document: ParityWorkspace.Document,
    at: Int,
    uses: List<Int>,
    name: String,
    highlights: Boolean,
) {
    val references = query("textDocument/references", document, at, mapOf("context" to mapOf("includeDeclaration" to true)))
    assertRanges(references.rows().map { it["range"] }, uses.map { sourceRange(document, it, name.length) })
    val starts = uses.toSet()

    fun shown(ranges: List<IntRange>) = ranges.map { it.first }.toSet()
    with(driver) {
        val usages = { ranges: List<IntRange> -> shown(ranges).let { it == starts || it == starts - uses.first() } }
        if (highlights) {
            usagesAndHighlights(document.editor, at, "are the uses of $name", highlights = { shown(it) == starts }, usages = usages)
        } else {
            withContext(OnDispatcher.EDT) {
                document.editor.editor
                    .getCaretModel()
                    .moveToOffset(at)
            }
            awaitFindUsages(document.editor, "native references are the uses of $name", usages)
        }
    }
}
