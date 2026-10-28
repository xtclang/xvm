package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import kotlin.time.Duration.Companion.seconds

private data class HighlightToken(
    val start: Int,
    val end: Int,
    val text: String,
    val type: String,
    val modifiers: Set<String>,
)

private fun ParityWorkspace.highlightTokens(doc: ParityWorkspace.Document): List<HighlightToken> {
    val expected =
        query("textDocument/semanticTokens/full", doc)
            .takeUnless { it.isJsonNull }
            ?.asJsonObject
            ?.get("data")
            ?.asJsonArray
            ?.map { it.asInt }
            .orEmpty()
    val support = with(driver) { semanticSupport(doc.editor).getSemanticTokensSupport() }
    val values =
        awaitUi("native semantic highlighting uses the current document", 30.seconds, getter = {
            support.getValidLSPFuture()?.takeIf { it.isDone() && !it.isCompletedExceptionally() }?.let { future ->
                future
                    .get()
                    ?.let {
                        protocol
                            .copy(it.getSemanticTokens())
                            .asJsonObject["data"]
                            .asJsonArray
                            .map { number -> number.asInt }
                    }.orEmpty()
            }
        }, checker = { it == expected })!!
    val legend =
        protocol
            .capabilities()
            .asJsonObject["semanticTokensProvider"]
            .asJsonObject["legend"]
            .asJsonObject
    val lines = doc.text.lines()
    var line = 0
    var column = 0
    return values.chunked(5).map { tuple ->
        line += tuple[0]
        column = (if (tuple[0] == 0) column else 0) + tuple[1]
        check(line < lines.size && column + tuple[2] <= lines[line].length) { "Stale semantic token $tuple" }
        val start = lines.take(line).sumOf { it.length + 1 } + column
        HighlightToken(
            start,
            start + tuple[2],
            doc.text.substring(start, start + tuple[2]),
            legend["tokenTypes"].asJsonArray[tuple[3]].asString,
            legend["tokenModifiers"]
                .asJsonArray
                .mapIndexedNotNull {
                    bit,
                    name,
                    ->
                    name.asString.takeIf { tuple[4] and (1 shl bit) != 0 }
                }.toSet(),
        )
    }
}

internal fun ParityScenarios.highlightingCases() {
    case("X276") { data ->
        val doc = open(data.string("file"), data.string("source"))
        clean(doc)
        val tokens = highlightTokens(doc)
        data["tokens"].rows().forEach { expected ->
            val start = doc.at(expected.string("anchor"), expected.int("offset"))
            val token = tokens.single { it.start == start }
            check(token.text == expected.string("text") && token.type == expected.string("type")) { "$expected; $token" }
            check(("defaultLibrary" in token.modifiers) == expected["library"].asBoolean) { "$expected; $token" }
        }
        val probe = with(driver) { utility(HighlightingProbe::class) }
        val annotationOffset = doc.at("@Marked", 1)
        awaitUi("native annotation decorator markup is installed", 30.seconds) {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    probe.semanticOverlays(doc.editor.editor, annotationOffset).any { "DECORATOR" in it }
                }
            }
        }
    }
    case("X277") { data ->
        val source = data.string("source")
        val doc = open(data.string("file"), source)
        clean(doc)
        val before = highlightTokens(doc)
        val offsets =
            data["lexical"].asJsonArray.flatMap { item ->
                val start = doc.at(item.asString)
                (start until start + item.asString.length).toList()
            }
        check(before.none { token -> offsets.any { it in token.start until token.end } })
        val probe = with(driver) { utility(HighlightingProbe::class) }
        with(driver) {
            withContext(OnDispatcher.EDT) {
                data["matchingStyles"].rows().forEach { pair ->
                    val left = doc.at(pair.string("left"))
                    val right = doc.at(pair.string("right"))
                    (0 until pair.int("length")).forEach { index ->
                        check(probe.lexical(doc.editor.editor, left + index) == probe.lexical(doc.editor.editor, right + index)) {
                            "Expression-body lexical styling differs at $pair, character $index"
                        }
                    }
                }
                offsets.forEach { offset ->
                    check(probe.lexical(doc.editor.editor, offset).isNotBlank())
                    check(
                        probe.semanticOverlays(doc.editor.editor, offset).isEmpty(),
                    ) { "Semantic markup masks lexical detail at $offset" }
                }
            }
        }
        captureColorScheme(doc)
        data["recovery"].rows().forEach { edit ->
            replace(doc, source.replace(edit.string("from"), edit.string("to")))
            errors(doc)
            val current = highlightTokens(doc)
            check(current.filter { it.type != "comment" }.all { it.text.matches(Regex("[A-Za-z_][A-Za-z_0-9]*")) })
            replace(doc, source)
            clean(doc)
            check(highlightTokens(doc) == before) { "Repair did not restore exact classifications" }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.HighlightingUi", plugin = "org.xtclang.playbook.probe")
internal interface HighlightingProbe {
    fun lexical(
        editor: Editor,
        offset: Int,
    ): String

    fun semanticOverlays(
        editor: Editor,
        offset: Int,
    ): List<String>
}
