package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.DeclarationDigests
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleStructure
import org.xvm.asm.TypedefStructure
import org.xvm.asm.constants.AnnotatedTypeConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import org.xvm.asm.Component.Format as ComponentFormat
import org.xvm.asm.Constants.Access as ComponentAccess

/**
 * What a module that depends on an artifact compiles against, declaration by declaration: for each declaration of the
 * artifact, its module, packages, classes, typedefs, properties and methods, and those of the fingerprints of the
 * modules it imports, the digest of its own API ([DeclarationDigests]), keyed by the digest of its identity, which two
 * compiles of the same declaration share; where it is; the types that its own API names; and the declarations that a
 * module sees it through. Holds no compiler objects; built once for each artifact ([XdkDependency.moduleApi]).
 */
internal class XdkModuleApi private constructor(
    /**
     * The whole API and where each declaration is: what a module that depends on the artifact compiles against, the
     * artifact without its methods' code and source text, its build time and its source directory, with whether each
     * method has code ([org.xvm.asm.FileStructure.writeApiTo]), and the place of each declaration. A module that depends
     * on the artifact compiles against another with the same as it did against this one, and its facts name the
     * artifact's declarations at the same places.
     */
    val all: String,
    val declarations: List<Declaration>,
    /** The key of the declaration at each place, for the places that the artifact's declarations have. */
    val places: Map<SemanticModel.SourceLocation, String>,
    /** The components and constants whose digest is one that no other compile gives them ([DeclarationDigests.unstable]). */
    val unstable: Int,
) {
    /** What a module finds a declaration as: a namespace, found by name; a type, whose members it can match; or a member. */
    enum class Kind {
        NAMESPACE,
        TYPE,
        MEMBER,
    }

    /**
     * A declaration. [path] names it by its module and the names of what holds it, as a module's constants name it; the
     * overloads of a method share theirs, and [key], the digest of its identity, tells them apart. [digest] is that of
     * its own API, and [order] that of the order of its members, which a const's fields and a class's overloads are in,
     * for any declaration but a method, whose members are its body's; [publicOrder] is that of its members that are not
     * private, which another module than its own sees. [types] are the paths of the types that its own API names, its
     * own and those holding it among them, and of the namespaces it names as types; [supers], of a type, those of the
     * classes it extends, implements, incorporates, delegates to or is annotated with, without their type parameters, and
     * of a typedef, of the class it names.
     */
    class Declaration(
        val key: String,
        val path: String,
        val name: String,
        val kind: Kind,
        val digest: String,
        val order: String?,
        val publicOrder: String?,
        val types: Set<String>,
        val supers: Set<String>,
        /** What holds it, skipping the method family of a method; null for a module. */
        val parent: Declaration?,
        /** Private, which only its own class sees: a method family is when each of its methods is. */
        val private: Boolean,
        /** Declared in a method's body, such as a lambda or an anonymous class, which no other module can name. */
        val nested: Boolean,
        /** A declaration of another module that this module records it uses, not its own. */
        val fingerprint: Boolean,
    ) {
        /**
         * The paths of itself and of the declarations through which a module that uses a type as a whole sees it: what
         * holds it, up to and with the nearest type or namespace, but no further than one that is private, which only its
         * own class sees.
         */
        val visible: List<String> by lazy { chain(stopAtPrivate = true) }

        /** [visible], private declarations or not, through which a module whose classes extend the type sees it. */
        val enclosing: List<String> by lazy { chain(stopAtPrivate = false) }

        /** Whether a module sees it through a type: it, or the nearest type or namespace that holds it, short of a private one, is one. */
        fun seenThroughType(): Boolean {
            var next: Declaration? = this
            while (next != null) {
                if (next.kind == Kind.TYPE) return true
                if (next.kind == Kind.NAMESPACE || next.private) return false
                next = next.parent
            }
            return false
        }

        private fun chain(stopAtPrivate: Boolean): List<String> =
            buildList {
                add(path)
                var next: Declaration = this@Declaration
                while (!(stopAtPrivate && next.private)) {
                    next = next.parent ?: break
                    add(next.path)
                    if (next.kind != Kind.MEMBER) break
                }
            }

        override fun toString(): String = path
    }

    /** By path: a type's or namespace's conditional variants, or the overloads of a method. */
    val byPath: Map<String, List<Declaration>> by lazy { declarations.groupBy { it.path } }

    /** By the path of what holds them and their name, the declarations that no method holds. */
    val members: Map<String, Map<String, List<Declaration>>> by lazy {
        declarations
            .filter { !it.nested && it.parent != null }
            .groupBy { it.parent!!.path }
            .mapValues { (_, members) -> members.groupBy { it.name } }
    }

    /** The source files that the places are in. */
    val sources: Set<String> by lazy { places.keys.mapNotNullTo(hashSetOf()) { it.sourceName } }

    companion object {
        /**
         * The API of [file], an artifact read alone, which this strips; [located] gives the place of each declaration
         * that has one, by its identity, read from the file before it is stripped.
         */
        fun of(
            file: FileStructure,
            located: Map<IdentityConstant, SemanticModel.SourceLocation>,
        ): XdkModuleApi =
            ConstantPool.withPool(file.constantPool).use {
                val api = ByteArrayOutputStream()
                val digests = DeclarationDigests.writeApiTo(file, api)

                // A constant is named by its index in the API's pool, where the write put it: two artifacts whose
                // APIs are the same hold the same constant at each index. A description of it would ask of its
                // signature's types what only the dependencies' classes answer, which the artifact read alone holds
                // only as fingerprints, such as whether a parameterized type is a function's: in a library of the XVM
                // repository, which depends on the workspace's ecstasy, "missing class for constant". A constant that
                // the API does not hold is described, as before.
                fun named(constant: Constant): String = constant.position.takeIf { it >= 0 }?.let { "#$it" } ?: constant.toString()
                val all =
                    sha256 { digest ->
                        digest.update(api.toByteArray())
                        located.entries
                            .map { (identity, place) -> "${named(identity)} ${place.sourceName} ${place.range}" }
                            .sorted()
                            .forEach {
                                digest.update(it.toByteArray(Charsets.UTF_8))
                                digest.update(0)
                            }
                    }
                Builder(digests).run {
                    DeclarationDigests.children(file).forEach { module ->
                        declare(module, null, nested = false, fingerprint = (module as ModuleStructure).isFingerprint)
                    }
                    XdkModuleApi(
                        all,
                        declarations,
                        located.entries.associate { (identity, place) -> place to digests.of(identity) },
                        digests.unstable(),
                    )
                }
            }

        private class Builder(
            val digests: DeclarationDigests,
        ) {
            val declarations = mutableListOf<Declaration>()

            /** One string for each path, which the declarations' types share. */
            val paths = hashMapOf<String, String>()

            fun declare(
                component: Component,
                parent: Declaration?,
                nested: Boolean,
                fingerprint: Boolean,
            ) {
                val identity = component.identityConstant
                val kind =
                    when (component.format) {
                        ComponentFormat.MODULE, ComponentFormat.PACKAGE -> Kind.NAMESPACE
                        ComponentFormat.METHOD, ComponentFormat.PROPERTY, ComponentFormat.MULTIMETHOD -> Kind.MEMBER
                        else -> Kind.TYPE
                    }
                val children = DeclarationDigests.children(component)

                // a method's children are its body's lambdas and classes, which only the body can name
                fun order(members: List<Component>): String? =
                    if (component.format == ComponentFormat.METHOD) null else hash(members.map { digests.of(it.identityConstant) })
                val declaration =
                    Declaration(
                        digests.of(identity),
                        intern(path(identity)),
                        identity.name,
                        kind,
                        digests.of(component),
                        order(children),
                        order(children.filterNot(::isPrivate)),
                        types(digests.references(component), ::intern),
                        supers(component).mapTo(hashSetOf(), ::intern),
                        parent,
                        isPrivate(component),
                        nested,
                        fingerprint,
                    )
                declarations += declaration
                // a method family is no declaration of its own a module sees: its methods are held by its class
                val holder = if (component.format == ComponentFormat.MULTIMETHOD) parent else declaration
                children.forEach { child ->
                    declare(child, holder, nested || component.format == ComponentFormat.METHOD, fingerprint)
                }
            }

            private fun intern(path: String): String = paths.getOrPut(path) { path }

            /** Whether only its own class sees [component]: one that is private, or a method family whose methods all are. */
            private fun isPrivate(component: Component): Boolean =
                component.access == ComponentAccess.PRIVATE ||
                    (
                        component.format == ComponentFormat.MULTIMETHOD &&
                            DeclarationDigests.children(component).let { methods -> methods.isNotEmpty() && methods.all(::isPrivate) }
                    )

            private fun hash(parts: List<String>): String =
                sha256 { digest ->
                    parts.forEach {
                        digest.update(it.toByteArray(Charsets.UTF_8))
                        digest.update(0)
                    }
                }
        }
    }
}

/**
 * The path of [identity]: its module's name and the names of what holds it, as `json.xtclang.org:Doc/parse/parse` for a
 * method parse of class Doc; overloads share it. Reads nothing that an artifact read alone does not hold.
 */
internal fun path(identity: IdentityConstant): String =
    identity.moduleConstant.name + ":" +
        generateSequence(identity) { it.parentConstant }
            .takeWhile { it.format != Constant.Format.Module }
            .toList()
            .asReversed()
            .joinToString("/") { it.name.orEmpty() }

/**
 * The paths of the classes that [component], a class or a typedef, takes members from: those it extends, implements,
 * incorporates, delegates to or is annotated with, and the one a typedef names ([heads]); none for another component.
 */
internal fun supers(component: Component): Set<String> =
    when (component) {
        is ClassStructure ->
            component.contributionsAsList
                .filter { it.composition != Component.Composition.Import }
                .flatMapTo(hashSetOf()) { contribution ->
                    heads(contribution.typeConstant) + listOfNotNull(contribution.annotation).flatMap { types(listOf(it.annotationClass)) }
                }

        is TypedefStructure -> heads(component.type)

        else -> emptySet()
    }

/**
 * The paths of the classes that [type] is of, as a contribution names them: the class that defines it, without the types
 * of its parameters, and each annotation's; every type it names when no single class defines it, or that cannot be told
 * from an artifact read alone.
 */
internal fun heads(type: TypeConstant?): Set<String> {
    if (type == null) return emptySet()
    if (type is AnnotatedTypeConstant) return types(listOf(type.annotation.annotationClass)) + heads(type.underlyingType)
    return try {
        val defining = type.takeIf { it.isSingleDefiningConstant }?.definingConstant
        if (defining is IdentityConstant && (defining.format == Constant.Format.Class || defining.format == Constant.Format.Typedef)) {
            setOf(path(defining))
        } else {
            types(listOf(type))
        }
    } catch (_: RuntimeException) {
        // a typedef of another module, which only its own artifact holds
        types(listOf(type))
    }
}

/**
 * The paths of the types that [constants] name, those that they refer to and so on: each class and typedef, and each
 * module or package named as a type; [intern] gives each path the string to use.
 */
internal fun types(
    constants: Iterable<Constant>,
    intern: (String) -> String = { it },
): Set<String> {
    val seen = hashSetOf<Constant>()
    val types = hashSetOf<String>()

    fun visit(constant: Constant?) {
        if (constant == null || !seen.add(constant)) return
        when {
            constant.format == Constant.Format.Class || constant.format == Constant.Format.Typedef ->
                types += intern(path(constant as IdentityConstant))

            constant is TerminalTypeConstant -> {
                // a module or package named as a type, whose members a module can match as a type's
                constant.forEachUnderlying { underlying ->
                    if (underlying.format == Constant.Format.Module || underlying.format == Constant.Format.Package) {
                        types += intern(path(underlying as IdentityConstant))
                    }
                }
            }
        }
        constant.forEachUnderlying(::visit)
    }
    constants.forEach(::visit)
    return types
}

private fun sha256(update: (MessageDigest) -> Unit): String =
    MessageDigest.getInstance("SHA-256").let { digest ->
        update(digest)
        HexFormat.of().formatHex(digest.digest(), 0, 16)
    }
