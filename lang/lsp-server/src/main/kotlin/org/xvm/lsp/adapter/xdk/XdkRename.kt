package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit

/** Worker-only edit planning and binding comparison. Compiler constants never escape this proof. */
internal object XdkRename {
    data class Edit(
        val start: Int,
        val end: Int,
        val text: String,
    )

    class Plan(
        val original: Map<String, String>,
        val edits: Map<String, List<Edit>>,
    ) {
        val proposed =
            original.mapValues { (source, text) ->
                edits[source].orEmpty().sortedByDescending { it.start }.fold(text) { value, edit ->
                    value.replaceRange(edit.start, edit.end, edit.text)
                }
            }

        fun map(
            source: String,
            offset: Int,
        ): Int? {
            val edits = edits[source].orEmpty()
            if (edits.any { offset > it.start && offset < it.end }) return null
            return offset + edits.filter { it.end <= offset }.sumOf { it.text.length - (it.end - it.start) }
        }

        fun textEdits(source: String): List<TextEdit> =
            edits[source].orEmpty().map { edit ->
                val text = original.getValue(source)
                TextEdit(Range(position(text, edit.start), position(text, edit.end)), edit.text)
            }
    }

    fun plan(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        source: String,
        line: Int,
        column: Int,
        name: String,
        targets: Set<SemanticModel.SymbolId>? = null,
    ): Plan? {
        if (!identifier(name) || facts.models.any { it.status != SemanticModel.Status.COMPLETE }) return null
        val model = facts.models.singleOrNull { it.sourceName == source } ?: return null
        val target = model.symbolAt(line, column)?.takeIf { it.renameable || it.id in targets.orEmpty() } ?: return null
        val selected = targets ?: setOf(target.id)
        if (name == target.name) return Plan(texts, emptyMap())
        val edits =
            facts.models
                .associate { view ->
                    val sourceName = view.sourceName ?: return null
                    val text = texts[sourceName] ?: return null
                    sourceName to
                        view.occurrences.filter { it.symbol in selected && it.name != "super" }.map { occurrence ->
                            val start = offset(text, occurrence.range.start) ?: return null
                            val end = offset(text, occurrence.range.end) ?: return null
                            if (text.substring(start, end) != target.name) return null
                            Edit(start, end, name)
                        }
                }.filterValues { it.isNotEmpty() }
        return Plan(texts, edits)
    }

    /** Compare every written binding, including names untouched by the edit, and selected calls. */
    fun preservesBindings(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
    ): Boolean {
        if (after.models.any { it.status != SemanticModel.Status.COMPLETE }) return false
        val expected = edges(before, plan.original) { source, offset -> plan.map(source, offset) } ?: return false
        val actual = edges(after, plan.proposed) { _, offset -> offset } ?: return false
        if (expected != actual) return false
        val expectedDispatch = dispatch(before, plan.original) { source, offset -> plan.map(source, offset) } ?: return false
        val actualDispatch = dispatch(after, plan.proposed) { _, offset -> offset } ?: return false
        return expectedDispatch == actualDispatch
    }

    private data class Dispatch(
        val owner: Target,
        val methods: List<Target>,
        val supported: Boolean,
    )

    /** A compiling rename can add an override without changing any written name or call binding. */
    private fun dispatch(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        translate: (String, Int) -> Int?,
    ): Set<Dispatch>? {
        val declarations =
            facts.models
                .flatMap { it.symbols }
                .distinctBy { it.id }
                .mapNotNull { symbol -> facts.constants[symbol.id]?.let { it to symbol } }
                .toMap()

        fun target(constant: Constant): Target? {
            val symbol = declarations[constant] ?: return Target.External(constant)
            val source = symbol.declarationSource ?: return Target.External(constant)
            val text = texts[source] ?: return Target.External(constant)
            val range = symbol.declaration ?: return null
            val start = offset(text, range.start)?.let { translate(source, it) } ?: return null
            val end = offset(text, range.end)?.let { translate(source, it) } ?: return null
            return Target.Declaration(Site(source, start, end), symbol.kind)
        }
        return facts.methods.chains.mapTo(linkedSetOf()) { chain ->
            Dispatch(target(chain.owner) ?: return null, chain.methods.map { target(it) ?: return null }, chain.supported)
        }
    }

    private data class Site(
        val source: String,
        val start: Int,
        val end: Int,
        val call: Boolean = false,
    )

    private sealed interface Target {
        data class Declaration(
            val site: Site,
            val kind: SemanticModel.SymbolKind,
        ) : Target

        data class External(
            val constant: Constant,
        ) : Target
    }

    private fun edges(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        translate: (String, Int) -> Int?,
    ): Map<Site, Target>? {
        fun site(
            source: String,
            range: SemanticModel.Range,
            call: Boolean = false,
        ): Site? {
            val text = texts[source] ?: return null
            val start = offset(text, range.start)?.let { translate(source, it) } ?: return null
            val end = offset(text, range.end)?.let { translate(source, it) } ?: return null
            return Site(source, start, end, call)
        }

        fun target(
            model: SemanticModel,
            id: SemanticModel.SymbolId?,
        ): Target? {
            val symbol = id?.let(model::symbol) ?: return null
            val source = symbol.declarationSource
            val range = symbol.declaration
            return if (source != null && range != null && source in texts) {
                site(source, range)?.let { Target.Declaration(it, symbol.kind) }
            } else {
                facts.constants[id]?.let { Target.External(it) }
            }
        }
        val result = linkedMapOf<Site, Target>()
        facts.models.forEach { model ->
            val source = model.sourceName ?: return null
            model.occurrences.forEach { occurrence ->
                result[site(source, occurrence.range) ?: return null] = target(model, occurrence.symbol) ?: return null
            }
            model.calls.forEach { call ->
                result[site(source, call.callee, true) ?: return null] = target(model, call.method) ?: return null
            }
        }
        return result
    }

    private fun identifier(name: String): Boolean {
        val errors = ErrorList()
        val lexer = Lexer(Source(name), errors)
        if (!lexer.hasNext()) return false
        val token = lexer.next()
        return token.id == Token.Id.IDENTIFIER && token.valueText == name && !lexer.hasNext() && !errors.hasSeriousErrors()
    }

    private val newlines = Regex("\\r\\n|\\r|\\n")

    private fun offset(
        text: String,
        position: SemanticModel.Position,
    ): Int? {
        val breaks = newlines.findAll(text).toList()
        val start =
            if (position.line == 0) {
                0
            } else {
                breaks
                    .getOrNull(position.line - 1)
                    ?.range
                    ?.last
                    ?.plus(1) ?: return null
            }
        val end = breaks.getOrNull(position.line)?.range?.first ?: text.length
        return (start + position.column).takeIf { it <= end }
    }

    private fun position(
        text: String,
        offset: Int,
    ): Position {
        val breaks = newlines.findAll(text).takeWhile { it.range.last < offset }.toList()
        return Position(
            breaks.size,
            offset - (
                breaks
                    .lastOrNull()
                    ?.range
                    ?.last
                    ?.plus(1) ?: 0
            ),
        )
    }
}
