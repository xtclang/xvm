package org.xvm.lsp.adapter.xdk

import org.slf4j.LoggerFactory
import org.xvm.asm.Component
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.ECSTASY_MODULE
import org.xvm.asm.Constants.TURTLE_MODULE

/**
 * The member-use tracker: which of the modules whose own sources a build leaves as they were, but that depend on a
 * module it compiles again, keep the compile they had, [prior]'s, as compiling them again would give the same facts,
 * artifact and errors. In build order, each module that the build compiles tells the tracker its artifact
 * ([compiled]), and each module that depends on one asks whether it keeps its compile ([decide]); a module compiled again
 * tells its own artifact in turn, so a change reaches the modules that depend on it through those that use it.
 *
 * A module keeps its compile when it compiled before, and of each module it depends on that compiled to another
 * artifact:
 * - its facts name no declaration of it by that artifact's revision ([CompilerRenameFacts.binaryModules]), and each
 *   declaration place they name in its sources holds the same declaration as before, at the same place;
 * - no declaration of it whose own API changed, was added or was removed ([XdkModuleApi]; a declaration in a method's
 *   body, which no other module can name, does not count) has a name that the module writes, in its code, comments or
 *   strings alike, or that its constants name ([XdkModuleUses.names]): a name it looks up, an overload set it resolves,
 *   a named argument, or a name that a new declaration would capture. A constructor, found only on the class a module
 *   names, and an accessor, reached only through its property, have no name of their own here;
 * - and none is seen through a type that it uses as a whole, each type its constants name ([XdkModuleUses.types]), with
 *   all its members and their order. A change is seen through the declaration that changed and what holds it, up to the
 *   nearest type or namespace, and past a type held by a type to its holder, as a module writes such a virtual child's
 *   type as its holder's and its name; and, to a fixpoint over the modules it depends on ([World.deep]), through each
 *   declaration whose own declaration names a type through which a change is seen, as a method's signature or a
 *   property's type does, and through each type that extends, implements or incorporates one. The compiler matches a
 *   type with an interface by its members (duck typing), and their types with the interface's members' types, whether to
 *   assign it, to infer a type parameter or to pick an overload; finds whether a class it instantiates is abstract from
 *   all its members; checks a switch over an enum against all its values; resolves operators and conversions by their
 *   annotations, not their names; and builds a class's members from all of its base's. Each of those consults a type as
 *   a whole, not a member by name. A member of a namespace that such a change reaches counts by its name, as above. A
 *   private member counts only for the types that the module's own classes extend, implement, incorporate or are
 *   annotated with, and those it names with another access than public ([XdkModuleUses.extended]): other types are
 *   public to it. A module or package that it uses as a type, as a value, has its members matched with an interface by
 *   their names only, so a change of one counts when its name is that of a member of a type the module uses as a whole.
 *   The types of the modules that the compiler knows by name, ecstasy and its turtle, are used as a whole by every
 *   module, as the compiler consults them (Object, Iterable, Const, ...) where no constant of the module names them: a
 *   change in one of them counts for every module, but for a private member's, which only its own class sees, and a
 *   namespace's member's, which a module finds by name.
 *
 * A module whose compile failed before keeps it only when the whole API of each such artifact ([XdkModuleApi.all]) is
 * as it was. A digest gap, an API that changed where no declaration's did, compiles every module that depends on it
 * again. Whatever the tracker cannot read compiles the module again; each decision says why, for the log.
 */
internal class XdkMemberUseTracker(
    private val prior: XdkCodeActionCache.Base,
) {
    /** Whether a module keeps its compile, and why. */
    class Decision(
        val keeps: Boolean,
        val reason: String,
    )

    /**
     * A declaration of [module] that changed, whether a module that writes its name compiles again, and the declarations
     * through which a module that uses them as a whole sees the change ([seen], [XdkModuleApi.Declaration.visible]), and
     * one whose classes extend them ([extended]).
     */
    private class Changed(
        val module: String,
        val declaration: XdkModuleApi.Declaration,
        named: Boolean,
        seen: List<XdkModuleApi.Declaration>,
        extended: List<XdkModuleApi.Declaration>,
    ) {
        /**
         * Whether a module that writes its name compiles again. A constructor, which a module finds only on the class it
         * names, and an accessor, which it reaches only through its property, are found by no name of their own.
         */
        val named: Boolean =
            named && declaration.name != "construct" && declaration.parent?.kind != XdkModuleApi.Kind.MEMBER

        /** The paths of the declarations through which a module that uses them as a whole sees the change. */
        val visible: List<String> = seen.map { it.path }

        /** The paths of the declarations through which a module whose classes extend them sees the change. */
        val enclosing: List<String> = extended.map { it.path }

        /**
         * The namespace through which [visible] sees it as a member, if any: a module or a package that a module uses as a
         * type sees its members as an interface sees a type's, by their names alone, as no class extends a namespace.
         */
        val namespace: String? =
            seen
                .drop(1)
                .lastOrNull()
                ?.takeIf { it.kind == XdkModuleApi.Kind.NAMESPACE }
                ?.path

        /** The types among [visible], without the members and namespaces: those whose members another type's can match. */
        val visibleTypes: List<String> = seen.filter { it.kind == XdkModuleApi.Kind.TYPE }.map { it.path }

        /** The types among [enclosing]. */
        val enclosingTypes: List<String> = extended.filter { it.kind == XdkModuleApi.Kind.TYPE }.map { it.path }
    }

    /** How a module's artifact changed in this build. */
    private sealed interface Change {
        /** Not one the tracker can read, as [reason] says. */
        class Unreadable(
            val reason: String,
        ) : Change

        class Readable(
            val before: XdkModuleApi,
            val after: XdkModuleApi,
            val changed: List<Changed>,
            /** Whether a declaration's place changed. */
            val moved: Boolean,
            /** The whole API changed where no declaration's own API, the order of its members or its place did. */
            val gap: Boolean,
        ) : Change
    }

    /** A module compiled in this build, with its artifact before and after, null for a compile that failed. */
    private class Recompiled(
        val name: String,
        val before: XdkDependency?,
        val after: XdkDependency?,
    ) {
        val change: Change by lazy { change(name, before, after) }
    }

    /** The modules compiled in this build whose artifact changed, by name, in build order. */
    private val recompiled = linkedMapOf<String, Recompiled>()

    /** [module] compiled in this build, to [after], or failed to compile, as null. */
    fun compiled(
        module: String,
        after: XdkDependency?,
    ) {
        val before = prior.artifacts[module]
        if (after != null && before?.revision == after.revision) {
            recompiled.remove(module)
        } else {
            recompiled[module] = Recompiled(module, before, after)
        }
    }

    /**
     * Whether [module], whose own sources are as they were, keeps its compile: [upstream] names the source modules it
     * depends on, directly or not, [texts] are its sources, and [artifacts] holds, by module name, those its compile would
     * read now.
     */
    fun decide(
        module: XdkSourceModule,
        upstream: Set<String>,
        texts: Collection<String>,
        artifacts: Map<String, XdkDependency>,
    ): Decision {
        val changed = recompiled.values.filter { it.name in upstream }
        if (changed.isEmpty()) return Decision(true, "no module it depends on compiled to another artifact")
        changed.firstOrNull { it.after == null }?.let { return Decision(false, "${it.name} does not compile") }
        val facts = prior.facts.module(module.uri) ?: return Decision(false, "it has no facts to keep")
        changed.firstOrNull { it.name in facts.binaryModules }?.let {
            return Decision(false, "its facts name declarations of ${it.name} by its artifact")
        }
        val changes =
            changed.map { recompiled ->
                when (val change = recompiled.change) {
                    is Change.Unreadable -> return Decision(false, change.reason)

                    is Change.Readable -> {
                        if (change.gap) {
                            return Decision(false, "the API of ${recompiled.name} changed where no declaration's did (a digest gap)")
                        }
                        recompiled.name to change
                    }
                }
            }
        if (prior.artifacts[module.name] == null) {
            // facts of a compile that failed name every member of the types it got to, private ones too
            return changes.firstOrNull { (_, change) -> change.before.all != change.after.all }?.let { (name, _) ->
                Decision(false, "its compile failed, and the API of $name changed")
            } ?: Decision(true, "its compile failed, and the API of each module it depends on is as it was")
        }
        changes.filter { it.second.moved }.takeIf { it.isNotEmpty() }?.let { moved ->
            places(facts).forEach { place ->
                moved.forEach { (name, change) ->
                    if (place.sourceName in change.before.sources || place.sourceName in change.after.sources) {
                        val key = change.before.places[place]
                        if (key == null || change.after.places[place] != key) {
                            return Decision(false, "its facts name the declaration of $name at ${describe(place)}, which moves")
                        }
                    }
                }
            }
        }
        val declarations = changes.flatMap { it.second.changed }
        if (declarations.isEmpty()) return Decision(true, "no declaration it can use changed")
        val uses =
            requireNotNull(prior.artifacts[module.name]).uses
                ?: return Decision(false, "its artifact cannot be read alone")
        // a change that only its own class sees, such as the order of a type's private members, is seen through no type,
        // and one of a namespace's member is found by name
        declarations
            .firstOrNull { it.module in IMPLICIT && !it.declaration.fingerprint && it.visibleTypes.isNotEmpty() }
            ?.let {
                return Decision(false, "every module uses ${it.module} as a whole, whose ${it.declaration} changes")
            }
        val world = World(artifacts, changed, upstream)
        val all = declarations + world.deep(declarations)
        val names = Names(uses.names, texts)
        all.firstOrNull { it.named && names.spells(it.declaration.name) }?.let {
            return Decision(false, "it uses the name ${it.declaration.name}, which ${it.module} changes")
        }
        val observed = uses.types + uses.extended
        world.unknown(observed)?.let { return Decision(false, it) }
        // a namespace that it uses as a type is matched with an interface, as a value, by the names of its members
        val matched by lazy { world.memberNames(observed) }
        all
            .firstOrNull { change ->
                !change.declaration.fingerprint &&
                    (
                        change.visible.any { it in observed && (it != change.namespace || change.declaration.name in matched) } ||
                            change.enclosing.any(uses.extended::contains)
                    )
            }?.let { change ->
                val through = (change.visible.filter(observed::contains) + change.enclosing.filter(uses.extended::contains)).first()
                return Decision(false, "it uses $through as a whole, whose ${change.declaration} in ${change.module} changes")
            }
        return Decision(true, "it uses none of the declarations of ${changed.joinToString { it.name }} that changed")
    }

    /** The names that a module writes, in its code, comments or strings alike, and those its constants name. */
    private class Names(
        private val named: Set<String>,
        private val texts: Collection<String>,
    ) {
        /** Every identifier the texts hold, each whole. */
        private val written: Set<String> by lazy {
            texts.flatMapTo(hashSetOf()) { text ->
                IDENTIFIER.findAll(text).map { it.value }.toList()
            }
        }

        fun spells(name: String): Boolean =
            name in named ||
                if (IDENTIFIER.matches(name)) name in written else texts.any { spells(it, name) }

        /** Whether [text] writes [name] whole, for a name that is not a plain identifier. */
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
    }

    /**
     * The declarations a module can see: those of the artifacts its compile reads now, of the modules it depends on and
     * of the host's binaries, and of those that the modules compiled again in this build had, by module name.
     */
    private class World(
        private val artifacts: Map<String, XdkDependency>,
        changed: List<Recompiled>,
        /** The source modules that the module depends on, directly or not, whose declarations it can see. */
        private val upstream: Set<String>,
    ) {
        private val before = changed.associate { it.name to it.before }
        private val replaced = changed.map { it.name }.filter { it in XdkLibraries.moduleNames }

        /** The modules whose declarations the module can see: those it depends on, and the host's binaries. */
        private val visibleModules = artifacts.keys.filter { it in upstream || artifacts[it]?.fromSource == false }

        /** Why a module's artifact cannot be read alone, by module name. */
        private val unreadable = linkedMapOf<String, String>()

        private val apis = hashMapOf<String, List<XdkModuleApi>?>()

        private fun apis(module: String): List<XdkModuleApi>? =
            apis.getOrPut(module) {
                val current = artifacts[module] ?: return@getOrPut null
                listOfNotNull(current, before[module]).map { artifact ->
                    artifact.moduleApi ?: return@getOrPut null.also {
                        unreadable[module] = "the API of $module's artifact cannot be read alone"
                    }
                }
            }

        /**
         * The declarations whose own API is as it was but that change with [changes], to a fixpoint: each that names, in
         * its own declaration, a type through which another change is seen, as a method's signature or a property's type
         * does, and each type that extends, implements or incorporates one, whose members it takes. The compiler that
         * matches a type with an interface compares the types of their members, and those of their members' types, and
         * so on; a type takes its base's private members too, which only a module whose classes extend it sees. A member
         * of a namespace, found by name, is found by its name again.
         */
        fun deep(changes: List<Changed>): List<Changed> {
            val seen = changes.mapTo(hashSetOf()) { it.declaration }
            val sources = changes.filterNot { it.declaration.fingerprint }
            // the types through which the changes are seen, whose members another type's can match; a namespace's
            // members are found by name, which the change of each of them is checked by
            val visible = sources.flatMapTo(hashSetOf()) { it.visibleTypes }
            val enclosing = sources.flatMapTo(hashSetOf()) { it.enclosingTypes }.apply { addAll(visible) }
            val candidates =
                visibleModules
                    .flatMap { module -> apis(module).orEmpty() }
                    .flatMap { api -> api.declarations }
                    .filter { !it.nested && !it.fingerprint }
            val found = mutableListOf<Changed>()
            do {
                val grown = found.size
                candidates.forEach { declaration ->
                    if (declaration in seen) return@forEach
                    val module = declaration.path.substringBefore(':')
                    when {
                        declaration.header.any(visible::contains) || declaration.supers.any(visible::contains) -> {
                            seen += declaration
                            val named = declaration.parent?.kind == XdkModuleApi.Kind.NAMESPACE
                            val change = Changed(module, declaration, named, declaration.visible, declaration.enclosing)
                            found += change
                            visible += change.visibleTypes
                            enclosing += change.enclosingTypes
                        }

                        declaration.supers.any(enclosing::contains) -> {
                            seen += declaration
                            val extending = declaration.enclosing.filter { it.kind == XdkModuleApi.Kind.TYPE }
                            val change = Changed(module, declaration, named = false, emptyList(), extending)
                            found += change
                            enclosing += change.enclosingTypes
                        }
                    }
                }
            } while (found.size != grown)
            return found
        }

        /**
         * Why a module that uses the types of [paths] cannot be shown to use none of what changed, or null: an artifact of
         * the build that cannot be read alone, whose declarations are not known; or a bundled library, which this does
         * not read, when another bundled library is replaced and may change what it holds.
         */
        fun unknown(paths: Set<String>): String? {
            visibleModules.forEach(::apis)
            unreadable.values.firstOrNull()?.let { return it }
            return paths
                .map { it.substringBefore(':') }
                .distinct()
                .firstNotNullOfOrNull { module ->
                    when {
                        module in artifacts -> null

                        module in XdkLibraries.moduleNames ->
                            "it uses the bundled $module, which can depend on the replaced ${replaced.joinToString()}"
                                .takeIf { replaced.isNotEmpty() }

                        else -> "it uses $module, whose artifact it does not have"
                    }
                }
        }

        /**
         * The names of the members of the types among [paths]: from their artifacts' APIs, or from the bundled library
         * that holds them, which never changes and so is not read for its API.
         */
        fun memberNames(paths: Set<String>): Set<String> =
            paths.flatMapTo(hashSetOf()) { path ->
                val module = path.substringBefore(':')
                apis(module)?.flatMap { api ->
                    if (api.byPath[path].orEmpty().any {
                            it.kind == XdkModuleApi.Kind.TYPE
                        }
                    ) {
                        api.members[path].orEmpty().keys
                    } else {
                        emptySet()
                    }
                } ?: bundledMembers(module, path)
            }

        /** The names of the members of the type at [path] of the bundled library [module], if it has one. */
        private fun bundledMembers(
            module: String,
            path: String,
        ): Set<String> {
            if (module !in XdkLibraries.moduleNames) return emptySet()
            val root = XdkLibraries.module(module) ?: return emptySet()
            return ConstantPool.withPool(root.constantPool).use {
                val names = path.substringAfter(':').split('/').filter { it.isNotEmpty() }
                val type = names.fold<String, Component?>(root) { holder, name -> holder?.getChild(name) }
                type
                    ?.takeUnless { it.format == Component.Format.MODULE || it.format == Component.Format.PACKAGE }
                    ?.children()
                    ?.mapTo(hashSetOf()) { it.name }
                    .orEmpty()
            }
        }
    }

    private companion object {
        /** The modules that the compiler knows by name, whose types it consults where no constant names them. */
        val IMPLICIT = setOf(ECSTASY_MODULE, TURTLE_MODULE)

        val IDENTIFIER = Regex("[\\p{L}\\p{N}_]+")

        fun change(
            name: String,
            before: XdkDependency?,
            after: XdkDependency?,
        ): Change {
            if (before == null || after == null) return Change.Unreadable("$name has no earlier artifact")
            val old = before.moduleApi ?: return Change.Unreadable("the API of $name's earlier artifact cannot be read alone")
            val new = after.moduleApi ?: return Change.Unreadable("the API of $name's artifact cannot be read alone")
            (old.unstable + new.unstable).takeIf { it > 0 }?.let { unstable ->
                logger.info(
                    "compile: {} components and constants of the APIs of {}'s artifacts have a digest that no other compile gives " +
                        "them (DeclarationDigests.unstable), and count as changed",
                    unstable,
                    name,
                )
            }
            val oldKeys = old.declarations.groupBy { it.key }
            val newKeys = new.declarations.groupBy { it.key }
            var any = false
            val changed =
                (oldKeys.keys + newKeys.keys).flatMap { key ->
                    val before = oldKeys[key].orEmpty()
                    val after = newKeys[key].orEmpty()
                    val own = before.map { it.digest } != after.map { it.digest }
                    val order = before.map { it.order } != after.map { it.order }
                    val publicOrder = before.map { it.publicOrder } != after.map { it.publicOrder }
                    any = any || own || order
                    (before + after).filterNot { it.nested }.mapNotNull { declaration ->
                        // the type itself, and each type that holds it as its virtual child
                        val types = declaration.visible.filter { it.kind == XdkModuleApi.Kind.TYPE }
                        val extending = declaration.enclosing.filter { it.kind == XdkModuleApi.Kind.TYPE }
                        when {
                            own || (publicOrder && declaration.kind == XdkModuleApi.Kind.MEMBER) ->
                                Changed(name, declaration, named = true, declaration.visible, declaration.enclosing)

                            // an overload or accessor that only its own class sees
                            order && declaration.kind == XdkModuleApi.Kind.MEMBER ->
                                Changed(name, declaration, named = true, emptyList(), declaration.enclosing)

                            // a type's or namespace's members are changes of their own, which a module finds by name or
                            // through what holds them; their order is the type's own, which a module sees as a whole, and
                            // that of its private members only one whose classes extend it, and a namespace's no module's,
                            // which finds its members by name alone
                            declaration.kind == XdkModuleApi.Kind.NAMESPACE -> null

                            publicOrder -> Changed(name, declaration, named = false, types, extending)

                            order -> Changed(name, declaration, named = false, emptyList(), extending)

                            else -> null
                        }
                    }
                }
            val moved = old.places != new.places
            val gap = old.all != new.all && !any && !moved
            return Change.Readable(old, new, changed, moved, gap)
        }

        fun describe(place: SemanticModel.SourceLocation): String =
            "${place.sourceName?.substringAfterLast('/')}:${place.range.start.line + 1}:${place.range.start.column + 1}"

        /** Every declaration place that [facts] name: their symbols' and those in the identities they bind and dispatch to. */
        fun places(facts: CompilerRenameFacts): Sequence<SemanticModel.SourceLocation> =
            facts.models.asSequence().flatMap { it.symbols.asSequence() }.mapNotNull { symbol ->
                symbol.declaration?.let { range -> symbol.declarationSource?.let { SemanticModel.SourceLocation(it, range) } }
            } +
                (facts.constants.values.asSequence() + facts.callables.values.asSequence())
                    .flatMap(::identityPlaces) +
                (facts.methods.chains + facts.properties.chains).asSequence().flatMap { chain ->
                    (listOf(chain.owner) + chain.members + chain.cycles + chain.alternatives).asSequence().flatMap(::identityPlaces)
                }

        fun identityPlaces(identity: ProofIdentity): Sequence<SemanticModel.SourceLocation> =
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
                        (identity.members + identity.delegates + identity.cycles + identity.alternatives).asSequence().flatMap(
                            ::identityPlaces,
                        )

                is ProofIdentity.Alternatives -> identity.targets.asSequence().flatMap(::identityPlaces)

                is ProofIdentity.TypeShape -> identity.components.asSequence().flatMap(::identityPlaces)

                is ProofIdentity.Binary, is ProofIdentity.Value, is ProofIdentity.Directory, is ProofIdentity.Unproven -> emptySequence()
            }
    }
}

private val logger = LoggerFactory.getLogger(XdkMemberUseTracker::class.java)
