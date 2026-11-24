package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.FileStructure
import org.xvm.asm.MethodStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.TypedefStructure
import org.xvm.asm.constants.IdentityConstant
import java.util.concurrent.CancellationException

/**
 * Which of the modules that depend on a rename's edited modules its proof compiles after the edit; each of the others
 * compiles as it did, and keeps the revision's compile.
 *
 * A rename changes the names of its declarations and where the declarations on its edited lines are; nothing else of
 * a module it edits. A module that depends on one can bind differently after it only where:
 * - it writes the old or the new name, at a use, an override, an import, a named argument or a method reference;
 * - or it uses a type whose members the rename changes, which a name it does not write can reach: an interface that
 *   such a type is matched with by its members (duck typing), an overload or a generic inference that such a match
 *   decides, a class of its own that extends, implements or incorporates the type, or a type alias of the type. Those
 *   types are the edited modules' types with a member of either name, and each type, typedef, method or property, in
 *   them or in a module that depends on them, whose declaration names one; its compile names each type it uses, in
 *   its artifact's constants, inferred or not;
 * - or its facts name a declaration on an edited line, whose place the edit moves;
 * - or its facts name, by artifact revision, a module that the proof compiles again.
 *
 * Any other module keeps its compile: everything it reads of the edited modules is as it was.
 */
internal object XdkRenameReach {
    /** The modules to compile, by root URI, or, as null, all of them, for [reason]. */
    class Reach(
        val modules: Set<String>?,
        val reason: String,
    )

    /**
     * The modules that depend on [plan]'s edited modules, by root URI, that its proof compiles after the edit, with
     * why each other one keeps its compile; [texts] has each module's sources by root URI, and [base] the revision's
     * compile, its artifacts and facts.
     */
    fun of(
        plan: XdkRename.Plan,
        project: XdkProject,
        texts: Map<String, Collection<String>>,
        base: XdkCodeActionCache.Base,
    ): Reach {
        if (plan.moves.isNotEmpty()) return Reach(null, "the edit moves sources")
        // the names that the edits replace and write, each whole: a rename's edits replace one name with another
        val names =
            plan.edits.flatMapTo(hashSetOf()) { (source, edits) ->
                val text = plan.original[source].orEmpty()
                edits.flatMap { listOf(text.substring(it.start, it.end), it.text) }
            }
        if (names.isEmpty() || names.any { !IDENTIFIER.matches(it) }) {
            return Reach(null, "a name it replaces or writes is not a plain identifier")
        }
        val edited = plan.edits.keys.mapNotNullTo(hashSetOf()) { project.scope(it) }
        val dependents = edited.flatMapTo(linkedSetOf(), project::affected) - edited
        if (dependents.isEmpty()) return Reach(emptySet(), "no module depends on the edited ones")
        val modules = project.modules.values.associateBy { it.uri }
        return try {
            val files =
                (edited + dependents).associateWith { uri ->
                    val module = modules.getValue(uri)
                    base.artifacts[module.name]?.open() ?: return Reach(null, "${module.name} has no compile to read")
                }
            val headers = files.mapValues { (_, file) -> headerReferences(file) }

            // the types with a member of either name, and everything whose declaration names one, to a fixpoint
            val family = edited.flatMapTo(hashSetOf()) { owners(files.getValue(it), names) }
            do {
                val before = family.size
                headers.values.forEach { references ->
                    references.forEach { (key, named) -> if (key !in family && named.any(family::contains)) family += key }
                }
            } while (family.size != before)

            val lines = editedLines(plan)
            val compiled = linkedSetOf<String>()
            dependents.forEach { uri ->
                val facts = base.facts.module(uri)
                if (
                    facts == null ||
                    texts[uri].orEmpty().any { text -> names.any { spells(text, it) } } ||
                    poolReferences(files.getValue(uri)).any(family::contains) ||
                    places(facts).any { place -> lines[place.sourceName]?.let { place.range.lines().any(it::contains) } == true }
                ) {
                    compiled += uri
                }
            }
            // a module whose facts name a module compiled again by its artifact's revision, which changes, compiles too
            do {
                val before = compiled.size
                val recompiled = (edited + compiled).mapTo(hashSetOf()) { modules.getValue(it).name }
                dependents.filter { it !in compiled }.forEach { uri ->
                    if (base.facts
                            .module(uri)
                            ?.binaryModules
                            .orEmpty()
                            .any(recompiled::contains)
                    ) {
                        compiled += uri
                    }
                }
            } while (compiled.size != before)
            Reach(compiled, "the others write neither ${names.sorted().joinToString(" nor ")} nor use a type whose members it renames")
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: RuntimeException) {
            Reach(null, "the compiles could not be read (${failure.message})")
        }
    }

    /** The keys of the components of [file]'s module that have a member of one of [names], and of those members. */
    private fun owners(
        file: FileStructure,
        names: Set<String>,
    ): Set<String> =
        ConstantPool.withPool(file.constantPool).use {
            components(file.module)
                .flatMap { component ->
                    val named = component.children().filter { it.name in names }
                    if (named.isEmpty()) emptyList() else (named + component).map { key(it.identityConstant) }
                }.toSet()
        }

    /**
     * For each component of [file]'s module, by key, the keys that its declaration names, each with the keys of what
     * contains it: a class's contributions and type parameters, a method's parameter and return types, a property's
     * type, a typedef's type.
     */
    private fun headerReferences(file: FileStructure): Map<String, Set<String>> =
        ConstantPool.withPool(file.constantPool).use {
            components(file.module).associate { component ->
                val declared =
                    when (component) {
                        is ClassStructure -> {
                            component.contributionsAsList.flatMap { contribution ->
                                listOfNotNull(contribution.typeConstant, contribution.delegatePropertyConstant) +
                                    contribution.typeParams?.values.orEmpty()
                            } + component.typeParams.values
                        }

                        is MethodStructure -> component.paramTypes.toList() + component.returnTypes

                        is PropertyStructure -> listOfNotNull(component.type)

                        is TypedefStructure -> listOfNotNull(component.type)

                        else -> emptyList()
                    }
                key(component.identityConstant) to declared.flatMapTo(hashSetOf(), ::identityKeys)
            }
        }

    /** The keys of every identity in [file]'s constants, each with the keys of what contains it. */
    private fun poolReferences(file: FileStructure): Set<String> =
        ConstantPool.withPool(file.constantPool).use {
            file.constantPool.constants
                .filterIsInstance<IdentityConstant>()
                .flatMapTo(hashSetOf(), ::containers)
        }

    /** The keys of the identities that [constant] refers to, itself included, and of what contains each. */
    private fun identityKeys(constant: Constant): Set<String> {
        val seen = hashSetOf<Constant>()
        val keys = hashSetOf<String>()

        // a constant may leave a part out, such as a type without a parameter
        fun visit(next: Constant?) {
            if (next == null || !seen.add(next)) return
            if (next is IdentityConstant) keys += containers(next)
            next.forEachUnderlying(::visit)
        }
        visit(constant)
        return keys
    }

    /** The keys of [identity] and of each identity that contains it. */
    private fun containers(identity: IdentityConstant): List<String> = generateSequence(identity) { it.parentConstant }.map(::key).toList()

    /**
     * A key for [identity] that needs nothing resolved: its module's name and the names on its path. Overloads of one
     * name share a key, which only makes more modules compile.
     */
    private fun key(identity: IdentityConstant): String =
        identity.moduleConstant.name + ":" +
            generateSequence(identity) { it.parentConstant }
                .toList()
                .asReversed()
                .joinToString("/") { it.name.orEmpty() }

    private fun components(root: Component): List<Component> = listOf(root) + root.children().flatMap(::components)

    /** Whether [text] writes [name] as a whole identifier, in its code, comments or strings alike. */
    private fun spells(
        text: String,
        name: String,
    ): Boolean {
        var from = text.indexOf(name)
        while (from >= 0) {
            val end = from + name.length
            if ((from == 0 || !identifierPart(text[from - 1])) && (end == text.length || !identifierPart(text[end]))) return true
            from = text.indexOf(name, from + 1)
        }
        return false
    }

    private fun identifierPart(char: Char): Boolean = char == '_' || char.isLetterOrDigit()

    /** The lines, by source, that [plan]'s edits are on, from 0. */
    private fun editedLines(plan: XdkRename.Plan): Map<String, Set<Int>> =
        plan.edits.mapValues { (source, edits) ->
            val text = plan.original[source].orEmpty()
            edits.mapTo(hashSetOf()) { edit -> text.substring(0, edit.start).count { it == '\n' } }
        }

    private fun SemanticModel.Range.lines(): IntRange = start.line..end.line

    /** Every declaration place that [facts] name: their symbols' and those in the identities they bind and dispatch to. */
    private fun places(facts: CompilerRenameFacts): Sequence<SemanticModel.SourceLocation> =
        facts.models.asSequence().flatMap { it.symbols.asSequence() }.mapNotNull { symbol ->
            symbol.declaration?.let { range -> symbol.declarationSource?.let { SemanticModel.SourceLocation(it, range) } }
        } +
            (facts.constants.values.asSequence() + facts.callables.values.asSequence())
                .flatMap(::identityPlaces) +
            (facts.methods.chains + facts.properties.chains).asSequence().flatMap { chain ->
                (listOf(chain.owner) + chain.members + chain.cycles + chain.alternatives).asSequence().flatMap(::identityPlaces)
            }

    private fun identityPlaces(identity: ProofIdentity): Sequence<SemanticModel.SourceLocation> =
        when (identity) {
            is ProofIdentity.Source -> sequenceOf(identity.location)

            is ProofIdentity.Method -> identityPlaces(identity.parent)

            is ProofIdentity.Parameter -> identityPlaces(identity.method)

            is ProofIdentity.MethodFormal -> identityPlaces(identity.method)

            is ProofIdentity.PrimaryConstructor -> identityPlaces(identity.owner)

            is ProofIdentity.Generated -> identityPlaces(identity.owner) + identity.contracts.asSequence().flatMap(::identityPlaces)

            is ProofIdentity.Super -> identityPlaces(identity.method)

            is ProofIdentity.Receiver -> identityPlaces(identity.owner)

            is ProofIdentity.Composed ->
                identityPlaces(identity.owner) +
                    (identity.members + identity.delegates + identity.cycles + identity.alternatives).asSequence().flatMap(::identityPlaces)

            is ProofIdentity.Alternatives -> identity.targets.asSequence().flatMap(::identityPlaces)

            is ProofIdentity.TypeShape -> identity.components.asSequence().flatMap(::identityPlaces)

            is ProofIdentity.Binary, is ProofIdentity.Value, is ProofIdentity.Directory, is ProofIdentity.Unproven -> emptySequence()
        }

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
