package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.util.ExecutionTrace

/** Worker-only edit planning and binding comparison. Compiler constants never escape this proof. */
internal object XdkRename {
    data class Edit(
        val start: Int,
        val end: Int,
        val text: String,
    )

    /** Exact source text moved into an edit; old references must follow it during proof. */
    data class Relocation(
        val start: Int,
        val end: Int,
        val destination: Edit,
        val contentOffset: Int,
    )

    class Plan(
        val original: Map<String, String>,
        val edits: Map<String, List<Edit>>,
        val moves: Map<String, String> = emptyMap(),
        private val relocations: Map<String, List<Relocation>> = emptyMap(),
        val qualifications: Map<String, List<Edit>> = emptyMap(),
    ) {
        init {
            require(qualifications.all { (source, changes) -> changes.all { it in edits[source].orEmpty() } })
            relocations.forEach { (source, moved) ->
                moved.forEach { relocation ->
                    val insertion = relocation.destination
                    require(insertion in edits[source].orEmpty())
                    require(relocation.start < relocation.end)
                    require(
                        original.getValue(source).substring(relocation.start, relocation.end) ==
                            insertion.text.substring(
                                relocation.contentOffset,
                                relocation.contentOffset + relocation.end - relocation.start,
                            ),
                    ) { "A relocation must preserve the exact original text" }
                }
                require(moved.sortedBy { it.start }.zipWithNext().all { (first, next) -> first.end < next.start })
            }
        }

        val proposed =
            original.entries.associate { (source, text) ->
                sourceAfter(source) to
                    edits[source]
                        .orEmpty()
                        .sortedByDescending { it.start }
                        .fold(text) { value, edit ->
                            value.replaceRange(edit.start, edit.end, edit.text)
                        }
            }

        fun sourceAfter(source: String): String = moves[source] ?: source

        fun map(
            source: String,
            offset: Int,
        ): Int? {
            val edits = edits[source].orEmpty()
            relocations[source].orEmpty().firstOrNull { offset in it.start..it.end }?.let { moved ->
                val preceding = edits.filter { it != moved.destination && it.end <= moved.destination.start }
                return moved.destination.start + preceding.sumOf { it.text.length - (it.end - it.start) } +
                    moved.contentOffset + offset - moved.start
            }
            if (edits.any { offset > it.start && offset < it.end }) return null
            return offset +
                edits.filter { it.end <= offset }.sumOf { it.text.length - (it.end - it.start) }
        }

        fun textEdits(source: String): List<TextEdit> =
            edits[source].orEmpty().map { edit ->
                val text = original.getValue(source)
                TextEdit(Range(position(text, edit.start), position(text, edit.end)), edit.text)
            }

        fun callStart(
            source: String,
            offset: Int,
        ): Int? =
            map(source, offset)?.minus(
                qualifications[source].orEmpty().filter { it.start == offset && it.start == it.end }.sumOf { it.text.length },
            )
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
        if (!identifier(name) || facts.models.any { it.status != SemanticModel.Status.COMPLETE }) {
            return null
        }
        val model = facts.models.singleOrNull { it.sourceName == source } ?: return null
        val target =
            model.symbolAt(line, column)?.takeIf { it.renameable || it.id in targets.orEmpty() }
                ?: return null
        if (
            target.kind == SemanticModel.SymbolKind.MODULE &&
            model.occurrenceAt(line, column)?.name != target.name
        ) {
            return null
        }
        val selected = targets ?: setOf(target.id)
        if (
            facts.models
                .flatMap { it.symbols }
                .filter { it.id in selected }
                .all { it.name == name }
        ) {
            return Plan(texts, emptyMap())
        }
        val edits =
            facts.models
                .filter { it.sourceName != null }
                .associate { view ->
                    val sourceName = view.sourceName ?: return null
                    val text = texts[sourceName] ?: return null
                    sourceName to
                        view.occurrences
                            .filter {
                                it.symbol in selected &&
                                    it.name != "super" &&
                                    // Package aliases resolve to the imported module identity too;
                                    // renaming that module must leave the caller's local alias
                                    // intact.
                                    (
                                        target.kind != SemanticModel.SymbolKind.MODULE ||
                                            it.name == target.name
                                    )
                            }.map { occurrence ->
                                val start = offset(text, occurrence.range.start) ?: return null
                                val end = offset(text, occurrence.range.end) ?: return null
                                if (text.substring(start, end) != occurrence.name) return null
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
        val beforePrefixes = qualificationSites(before, plan.original, plan.qualifications) ?: return false
        val afterQualifications =
            plan.qualifications.entries.associate { (source, edits) ->
                plan.sourceAfter(source) to
                    edits.map { edit ->
                        val start = plan.map(source, edit.start)?.minus(if (edit.start == edit.end) edit.text.length else 0) ?: return false
                        Edit(start, start + edit.text.length, edit.text)
                    }
            }
        val afterPrefixes = qualificationSites(after, plan.proposed, afterQualifications) ?: return false
        val expected =
            edges(before, plan.original, plan::sourceAfter, ignored = beforePrefixes, callStart = plan::callStart) { source, offset ->
                plan.map(source, offset)
            } ?: return false
        val actual = edges(after, plan.proposed, ignored = afterPrefixes) { _, offset -> offset } ?: return false
        if (expected != actual) return false
        // Relocation must retain import targets in the new lexical owner. Ordinary source
        // actions may intentionally remove unused imports; their remaining bindings suffice.
        if (plan.moves.isNotEmpty() && (!preservesImports(before, after, plan) || !preservesResources(before, after, plan))) return false
        val expectedDispatch =
            dispatch(before, plan.original, plan::sourceAfter) { source, offset ->
                plan.map(source, offset)
            } ?: return false
        val actualDispatch = dispatch(after, plan.proposed) { _, offset -> offset } ?: return false
        return expectedDispatch == actualDispatch
    }

    /** Only static namespace/type prefixes may disappear; terminal bindings and calls still match. */
    private fun qualificationSites(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        qualifications: Map<String, List<Edit>>,
    ): Set<SemanticModel.SourceLocation>? =
        buildSet {
            facts.models.forEach { model ->
                val source = model.sourceName ?: return@forEach
                if (source !in qualifications) return@forEach
                val text = texts[source] ?: return null
                model.occurrences.forEach { occurrence ->
                    val start = offset(text, occurrence.range.start) ?: return null
                    val end = offset(text, occurrence.range.end) ?: return null
                    if (qualifications[source].orEmpty().any { start >= it.start && end <= it.end }) {
                        val symbol = occurrence.symbol?.let(model::symbol) ?: return null
                        if (symbol.kind !in
                            setOf(SemanticModel.SymbolKind.MODULE, SemanticModel.SymbolKind.PACKAGE, SemanticModel.SymbolKind.TYPE)
                        ) {
                            return null
                        }
                        add(SemanticModel.SourceLocation(source, occurrence.range))
                    }
                }
            }
        }

    fun preservesResources(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
    ): Boolean {
        fun values(
            facts: CompilerRenameFacts,
            texts: Map<String, String>,
            moved: (String) -> String,
            translate: (String, Int) -> Int?,
        ): Map<Site, String>? =
            facts.resourceValues.entries.associate { (location, value) ->
                val source = location.sourceName ?: return null
                val text = texts[source] ?: return null
                val start = offset(text, location.range.start)?.let { translate(source, it) } ?: return null
                val end = offset(text, location.range.end)?.let { translate(source, it) } ?: return null
                Site(moved(source), start, end) to (value ?: return null)
            }
        val expected = values(before, plan.original, plan::sourceAfter, plan::map) ?: return false
        return expected == values(after, plan.proposed, { it }) { _, offset -> offset }
    }

    private fun preservesImports(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
    ): Boolean {
        fun imported(
            facts: CompilerRenameFacts,
            texts: Map<String, String>,
            moved: (String) -> String,
            translate: (String, Int) -> Int?,
        ): Map<Site, Target>? =
            facts.typeNames.filter { it.imported }.associate { name ->
                val source = name.location.sourceName ?: return null
                val text = texts[source] ?: return null
                val start = offset(text, name.terminal.start)?.let { translate(source, it) } ?: return null
                val end = offset(text, name.terminal.end)?.let { translate(source, it) } ?: return null
                Site(moved(source), start, end) to (composedTarget(name.target, texts, moved, translate) ?: return null)
            }
        val expected = imported(before, plan.original, plan::sourceAfter, plan::map) ?: return false
        val actual = imported(after, plan.proposed, { it }) { _, offset -> offset } ?: return false
        return expected == actual
    }

    /** A repair may bind unresolved names, but cannot change any binding already established. */
    fun preservesKnownBindings(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
    ): Boolean {
        if (after.models.any { it.status != SemanticModel.Status.COMPLETE }) return false
        val expected =
            edges(before, plan.original, allowUnresolved = true, sourceParameters = true) {
                source,
                offset,
                ->
                plan.map(source, offset)
            } ?: return false
        val actual =
            edges(after, plan.proposed, sourceParameters = true) { _, offset -> offset }
                ?: return false
        if (expected.any { (site, target) -> actual[site] != target }) return false
        val knownDispatch =
            dispatch(before, plan.original) { source, offset -> plan.map(source, offset) }
                ?: return false
        val actualDispatch = dispatch(after, plan.proposed) { _, offset -> offset } ?: return false
        return actualDispatch.containsAll(knownDispatch)
    }

    /** Removing a local may remove its declaration, written type and sole read, but no other edges. */
    fun preservesLocalRemoval(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
        removed: Set<SemanticModel.SourceLocation>,
    ): Boolean {
        if (after.models.any { it.status != SemanticModel.Status.COMPLETE }) return false
        val expected = edges(before, plan.original, ignored = removed, translate = plan::map) ?: return false
        val actual = edges(after, plan.proposed) { _, offset -> offset } ?: return false
        if (expected != actual) return false
        val expectedDispatch = dispatch(before, plan.original, translate = plan::map) ?: return false
        val actualDispatch = dispatch(after, plan.proposed) { _, offset -> offset } ?: return false
        return expectedDispatch == actualDispatch
    }

    /**
     * Allow exactly the selected implementations and their inherited effects; preserve other
     * bindings.
     */
    fun preservesMemberAdditions(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: Plan,
        candidates: List<XdkMemberActions.Candidate>,
        insertion: Edit,
    ): Boolean {
        if (after.models.any { it.status != SemanticModel.Status.COMPLETE }) return false
        val owner = candidates.firstOrNull()?.owner ?: return false
        if (candidates.any { it.owner != owner }) return false
        val source = owner.sourceName ?: return false
        val edits = plan.edits[source] ?: return false
        if (plan.edits.size != 1 || insertion !in edits || edits.any { it.start != it.end }) {
            return false
        }
        val insertionStart =
            insertion.start + edits.filter { it.start < insertion.start }.sumOf { it.text.length }
        val oldDispatch =
            dispatch(before, plan.original) { path, offset -> plan.map(path, offset) }
                ?.let(::memberDispatch) ?: return false
        val importRanges =
            edits
                .filter { it != insertion }
                .map { edit ->
                    val start =
                        edit.start + edits.filter { it.start < edit.start }.sumOf { it.text.length }
                    start until start + edit.text.length
                }
        // New package aliases have their own inherited dispatch chains. They have no previous
        // owner to preserve; only package declarations inside the generated import insertions
        // are excluded. All existing owners and every old reference still participate in proof.
        val newDispatch =
            dispatch(after, plan.proposed) { _, offset -> offset }
                ?.let(::memberDispatch)
                ?.filterNot { chain ->
                    val declaration = chain.owner as? Target.Declaration
                    declaration?.kind == SemanticModel.SymbolKind.PACKAGE &&
                        declaration.site.source == source &&
                        importRanges.any {
                            declaration.site.start in it && declaration.site.end - 1 in it
                        }
                }?.toSet() ?: return false

        fun matches(
            target: Target,
            location: SemanticModel.SourceLocation,
        ): Boolean {
            val declaration = target as? Target.Declaration ?: return false
            val path = location.sourceName ?: return false
            val text = plan.original[path] ?: return false
            val start =
                offset(text, location.range.start)?.let { plan.map(path, it) } ?: return false
            val end = offset(text, location.range.end)?.let { plan.map(path, it) } ?: return false
            return declaration.site == Site(path, start, end)
        }

        fun matchesContract(
            target: Target,
            contract: ProofIdentity,
        ): Boolean =
            when (contract) {
                is ProofIdentity.Source -> {
                    if (contract.location.sourceName in plan.original) {
                        matches(target, contract.location)
                    } else {
                        target == Target.External(contract)
                    }
                }

                is ProofIdentity.Binary,
                is ProofIdentity.Method,
                -> {
                    target == Target.External(contract)
                }

                else -> {
                    false
                }
            }
        val changed = newDispatch - oldDispatch
        val additions =
            candidates
                .map { candidate ->
                    val removed =
                        (oldDispatch - newDispatch).singleOrNull { chain ->
                            matches(chain.owner, owner) &&
                                chain.members.any { matchesContract(it, candidate.contract) }
                        } ?: return false
                    val added =
                        changed.singleOrNull { chain ->
                            chain.owner == removed.owner &&
                                chain.members.containsAll(removed.members) &&
                                (chain.members - removed.members.toSet()).size == 1
                        } ?: return false
                    val member =
                        (added.members - removed.members.toSet()).single() as? Target.Declaration
                            ?: return false
                    if (
                        member.kind != SemanticModel.SymbolKind.METHOD ||
                        member.site.source != source ||
                        member.site.start < insertionStart ||
                        member.site.end > insertionStart + insertion.text.length
                    ) {
                        return false
                    }
                    member to removed
                }.toMap()
        if (additions.size != candidates.size) return false
        // Removing exactly these new methods must reconstruct every original chain in order,
        // including descendants, overloads and properties. Each method belongs to its own family.
        if (
            changed.any { chain ->
                !chain.supported ||
                    chain.members.count { it in additions } != 1 ||
                    chain.copy(members = chain.members.filterNot(additions::containsKey)) !in
                    oldDispatch
            }
        ) {
            return false
        }
        if (
            newDispatch.mapTo(linkedSetOf()) { chain ->
                if (chain in changed) {
                    chain.copy(members = chain.members.filterNot(additions::containsKey))
                } else {
                    chain
                }
            } != oldDispatch
        ) {
            return false
        }
        val expected =
            edges(before, plan.original, allowUnresolved = true, sourceParameters = true) {
                path,
                offset,
                ->
                plan.map(path, offset)
            } ?: return false
        val actual =
            edges(after, plan.proposed, sourceParameters = true) { _, offset -> offset }
                ?: return false
        val oldParameters =
            memberParameterSlots(before, plan.original) { path, offset -> plan.map(path, offset) }
        val newParameters = memberParameterSlots(after, plan.proposed) { _, offset -> offset }

        fun declarationSite(
            target: Target,
            chains: Set<Dispatch>,
        ): Site? =
            when (target) {
                is Target.Declaration -> {
                    target.site
                }

                is Target.SourceProof -> {
                    target.site
                }

                is Target.Composed -> {
                    (memberBridge(target, chains)?.firstOrNull() as? Target.Declaration)?.site
                }

                else -> {
                    null
                }
            }

        fun selectedRebinding(
            old: Target,
            new: Target,
        ): Boolean {
            val oldSite = declarationSite(old, oldDispatch)
            val newSite = declarationSite(new, newDispatch) ?: return false
            return additions.any { (member, original) ->
                member.site == newSite &&
                    original.members.any {
                        it == old ||
                            (oldSite != null && declarationSite(it, oldDispatch) == oldSite)
                    }
            }
        }

        return expected.all { (site, target) ->
            val replacement = actual[site] ?: return@all false
            if (target == replacement || selectedRebinding(target, replacement)) return@all true
            // Named arguments may now identify the new override's parameter. Only the same
            // compiler-proven slot on the selected method family is allowed to change owner.
            val oldSlot = oldParameters[target] ?: target as? Target.Parameter ?: return@all false
            val newSlot =
                newParameters[replacement] ?: replacement as? Target.Parameter ?: return@all false
            oldSlot.index == newSlot.index && selectedRebinding(oldSlot.method, newSlot.method)
        }
    }

    /**
     * A generic cap is equivalent only when the compiler also supplies that exact written chain.
     */
    private fun memberBridge(
        target: Target.Composed,
        chains: Set<Dispatch>,
    ): List<Target>? {
        val owner = (target.owner as? Target.SourceProof)?.site ?: return null
        if (target.delegates.isNotEmpty() || target.cycles.isNotEmpty()) return null
        val written =
            target.members.map {
                when (it) {
                    is Target.SourceProof -> {
                        if (it.format != Constant.Format.Method) return null
                        Target.Declaration(it.site, SemanticModel.SymbolKind.METHOD)
                    }

                    is Target.External -> {
                        if (
                            it.constant !is ProofIdentity.Binary &&
                            it.constant !is ProofIdentity.Method
                        ) {
                            return null
                        }
                        it
                    }

                    else -> {
                        return null
                    }
                }
            }
        return written.takeIf {
            chains.any { chain ->
                chain.supported &&
                    (chain.owner as? Target.Declaration)?.site == owner &&
                    chain.members == written
            }
        }
    }

    private fun memberDispatch(chains: Set<Dispatch>): Set<Dispatch> =
        chains.mapTo(linkedSetOf()) { chain ->
            if (!chain.supported) return@mapTo chain
            val members =
                chain.members
                    .flatMap { target ->
                        if (target is Target.Composed) {
                            memberBridge(target, chains) ?: listOf(target)
                        } else {
                            listOf(target)
                        }
                    }.distinct()
            // An inherited cap may belong to a base owner. Require the descendant's own explicit
            // chain too, so a bridge never invents a new dispatch relationship for that descendant.
            chain.copy(members = members).takeIf { it in chains } ?: chain
        }

    private fun memberParameterSlots(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        translate: (String, Int) -> Int?,
    ): Map<Target, Target.Parameter> =
        facts.models
            .flatMap { it.symbols }
            .mapNotNull { symbol ->
                val identity =
                    facts.constants[symbol.id] as? ProofIdentity.Parameter ?: return@mapNotNull null
                val source = symbol.declarationSource ?: return@mapNotNull null
                val text = texts[source] ?: return@mapNotNull null
                val range = symbol.declaration ?: return@mapNotNull null
                val start =
                    offset(text, range.start)?.let { translate(source, it) }
                        ?: return@mapNotNull null
                val end =
                    offset(text, range.end)?.let { translate(source, it) } ?: return@mapNotNull null
                val slot =
                    composedTarget(identity, texts, { it }, translate) as? Target.Parameter
                        ?: return@mapNotNull null
                Target.Declaration(Site(source, start, end), symbol.kind) to slot
            }.toMap()

    private data class Dispatch(
        val owner: Target,
        val members: List<Target>,
        val supported: Boolean,
    )

    /** A compiling rename can add an override without changing any written name or call binding. */
    private fun dispatch(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        moved: (String) -> String = { it },
        translate: (String, Int) -> Int?,
    ): Set<Dispatch>? {
        val declarations =
            facts.models
                .flatMap { it.symbols }
                .distinctBy { it.id }
                .mapNotNull { symbol -> facts.constants[symbol.id]?.let { it to symbol } }
                .toMap()

        fun target(constant: ProofIdentity): Target? {
            if (constant is ProofIdentity.Directory) return Target.Directory(moved(constant.path))
            if (constant is ProofIdentity.Composed) {
                return composedTarget(constant, texts, moved, translate)
            }
            val symbol = declarations[constant] ?: return Target.External(constant)
            val source = symbol.declarationSource ?: return Target.External(constant)
            val text = texts[source] ?: return Target.External(constant)
            val range = symbol.declaration ?: return null
            val start = offset(text, range.start)?.let { translate(source, it) } ?: return null
            val end = offset(text, range.end)?.let { translate(source, it) } ?: return null
            return Target.Declaration(Site(moved(source), start, end), symbol.kind)
        }
        return facts.methods.chains.mapTo(linkedSetOf()) { chain ->
            Dispatch(
                target(chain.owner) ?: return null,
                chain.members.map { target(it) ?: return null },
                chain.supported,
            )
        } +
            facts.properties.chains.map { chain ->
                Dispatch(
                    target(chain.owner) ?: return null,
                    chain.members.map { target(it) ?: return null },
                    chain.supported,
                )
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
            val constant: ProofIdentity,
        ) : Target

        data class Directory(
            val path: String,
        ) : Target

        data class Parameter(
            val method: Target,
            val index: Int,
        ) : Target

        data class PrimaryConstructor(
            val owner: Target,
        ) : Target

        data class Super(
            val method: Target,
        ) : Target

        data class Receiver(
            val owner: Target,
            val register: Int,
        ) : Target

        data class SourceProof(
            val site: Site,
            val format: Constant.Format,
        ) : Target

        data class Composed(
            val owner: Target,
            val members: List<Target>,
            val delegates: List<Target>,
            val cycles: List<Target>,
        ) : Target

        data class Alternatives(
            val targets: Set<Target>,
        ) : Target
    }

    private fun composedTarget(
        identity: ProofIdentity,
        texts: Map<String, String>,
        moved: (String) -> String,
        translate: (String, Int) -> Int?,
    ): Target? {
        return when (identity) {
            is ProofIdentity.Alternatives -> {
                Target.Alternatives(identity.targets.mapTo(linkedSetOf()) {
                    composedTarget(it, texts, moved, translate) ?: return null
                })
            }

            is ProofIdentity.Parameter -> {
                composedTarget(identity.method, texts, moved, translate)?.let {
                    Target.Parameter(it, identity.index)
                }
            }

            is ProofIdentity.PrimaryConstructor -> {
                composedTarget(identity.owner, texts, moved, translate)
                    ?.let(Target::PrimaryConstructor)
            }

            is ProofIdentity.Super -> {
                composedTarget(identity.method, texts, moved, translate)?.let(Target::Super)
            }

            is ProofIdentity.Receiver -> {
                composedTarget(identity.owner, texts, moved, translate)?.let { Target.Receiver(it, identity.register) }
            }

            is ProofIdentity.Composed -> {
                val owner = composedTarget(identity.owner, texts, moved, translate) ?: return null
                val members =
                    identity.members.map {
                        composedTarget(it, texts, moved, translate) ?: return null
                    }
                val delegates =
                    identity.delegates.map {
                        composedTarget(it, texts, moved, translate) ?: return null
                    }
                val cycles = identity.cycles.map { composedTarget(it, texts, moved, translate) ?: return null }
                Target.Composed(owner, members, delegates, cycles)
            }

            is ProofIdentity.Source -> {
                val source = identity.location.sourceName ?: return null
                val text = texts[source] ?: return Target.External(identity)
                val start =
                    offset(text, identity.location.range.start)?.let { translate(source, it) }
                        ?: return null
                val end =
                    offset(text, identity.location.range.end)?.let { translate(source, it) }
                        ?: return null
                Target.SourceProof(Site(moved(source), start, end), identity.format)
            }

            else -> {
                Target.External(identity)
            }
        }
    }

    private fun edges(
        facts: CompilerRenameFacts,
        texts: Map<String, String>,
        moved: (String) -> String = { it },
        allowUnresolved: Boolean = false,
        sourceParameters: Boolean = false,
        ignored: Set<SemanticModel.SourceLocation> = emptySet(),
        callStart: ((String, Int) -> Int?)? = null,
        translate: (String, Int) -> Int?,
    ): Map<Site, Target>? {
        val declarations =
            facts.models
                .flatMap { it.symbols }
                .filter { it.declaration != null && it.declarationSource in texts }
                .mapNotNull { symbol -> facts.constants[symbol.id]?.let { it to symbol } }
                .toMap()

        fun site(
            source: String,
            range: SemanticModel.Range,
            call: Boolean = false,
        ): Site? {
            val text = texts[source] ?: return null
            val start =
                offset(text, range.start)?.let { (if (call) callStart else null)?.invoke(source, it) ?: translate(source, it) }
                    ?: return null
            val end = offset(text, range.end)?.let { translate(source, it) } ?: return null
            return Site(moved(source), start, end, call)
        }

        fun target(
            model: SemanticModel,
            id: SemanticModel.SymbolId?,
        ): Target? {
            val original = id?.let(model::symbol) ?: return null
            val identity = facts.constants[id]
            val symbol =
                if (original.declaration == null) {
                    declarations[facts.constants[id]] ?: original
                } else {
                    original
                }
            // Before repairing an unresolved signature, the compiler can identify its written
            // parameter but cannot assign a method slot yet. Compare the same source identity
            // after repair; generated/composed slots still require their normal dispatch proof.
            if (
                sourceParameters &&
                symbol.kind == SemanticModel.SymbolKind.PARAMETER &&
                symbol.declarationSource in texts &&
                symbol.declaration != null
            ) {
                return site(requireNotNull(symbol.declarationSource), symbol.declaration)?.let {
                    Target.Declaration(it, symbol.kind)
                }
            }
            if (identity is ProofIdentity.Directory) return Target.Directory(moved(identity.path))
            if (
                identity is ProofIdentity.Composed ||
                identity is ProofIdentity.Alternatives ||
                identity is ProofIdentity.Super ||
                identity is ProofIdentity.Receiver ||
                identity is ProofIdentity.PrimaryConstructor ||
                identity is ProofIdentity.Parameter
            ) {
                return composedTarget(identity, texts, moved, translate)
            }
            val source = symbol.declarationSource
            val range = symbol.declaration
            return if (source != null && range != null && source in texts) {
                site(source, range)?.let { Target.Declaration(it, symbol.kind) }
            } else {
                facts.constants[id]?.let { Target.External(it) }
            }
        }
        val result = linkedMapOf<Site, Target>()
        facts.models
            .filter { it.sourceName != null }
            .forEach { model ->
                val source = model.sourceName ?: return null
                model.occurrences.forEach { occurrence ->
                    if (SemanticModel.SourceLocation(source, occurrence.range) !in ignored &&
                        (!allowUnresolved || occurrence.symbol != null)
                    ) {
                        result[site(source, occurrence.range) ?: return null] =
                            target(model, occurrence.symbol) ?: return null
                    }
                }
                model.calls.forEach { call ->
                    result[site(source, call.callee, true) ?: return null] =
                        target(model, call.method) ?: return null
                }
            }
        return result
    }

    internal fun identifier(name: String): Boolean =
        ExecutionTrace.api("Lexer.identifier(rename)") {
            val errors = ErrorList()
            val lexer = Lexer(Source(name), errors)
            if (!lexer.hasNext()) return@api false
            val token = lexer.next()
            token.id == Token.Id.IDENTIFIER &&
                token.valueText == name &&
                !lexer.hasNext() &&
                !errors.hasSeriousErrors()
        }

    private val newlines = Regex("\\r\\n|\\r|\\n")

    internal fun offset(
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

    internal fun position(
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
