package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Annotation
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component
import org.xvm.asm.Component.Composition
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.DeclarationDigests
import org.xvm.asm.FileStructure
import org.xvm.asm.constants.AccessTypeConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.Constants.Access as ComponentAccess

/**
 * What a module's compile used of the modules it depends on, read from its artifact's constants, which name every
 * declaration of another module that its code, its declarations and the code the compiler adds to them (such as a
 * const's equals and appendTo) refer to, and each type of each such declaration's signature. The member-use tracker
 * ([XdkMemberUseTracker]) compiles the module again only when one of these, or a name it writes, changes. Built once for
 * each artifact ([XdkDependency.uses]).
 */
internal class XdkModuleUses private constructor(
    /** The simple name of each declaration its constants name, its own included. */
    val names: Set<String>,
    /**
     * The path of each type of another module that its constants name ([types]): each class and typedef, those that hold a
     * member it names among them, and each module or package named as a type.
     */
    val types: Set<String>,
    /** The path of each module and package of another module that its constants name, in which it finds members by name. */
    val scopes: Set<String>,
    /**
     * The path of each type of another module whose members it can see even when private: each that its own classes
     * extend, implement, incorporate, delegate to or are annotated with, each annotation it applies, and each type it
     * names with an access other than public, such as a struct.
     */
    val extended: Set<String>,
) {
    companion object {
        /** The uses of [file], an artifact read alone. */
        fun of(file: FileStructure): XdkModuleUses =
            ConstantPool.withPool(file.constantPool).use {
                val module = file.module.name
                val own = "$module:"
                val constants = file.constantPool.constants.toList()
                val identities = constants.filterIsInstance<IdentityConstant>()
                val contributions =
                    components(file.module)
                        .filterIsInstance<ClassStructure>()
                        .flatMap { structure ->
                            // a package's import names the module it imports, whose members no class takes
                            structure.contributionsAsList.filter { it.composition != Composition.Import }.flatMap { contribution ->
                                listOfNotNull<Constant>(
                                    contribution.typeConstant,
                                    contribution.annotation,
                                    contribution.delegatePropertyConstant,
                                ) + contribution.typeParams?.values.orEmpty()
                            } + structure.typeParams.values
                        }
                val annotations = constants.filterIsInstance<Annotation>()
                val accesses = constants.filterIsInstance<AccessTypeConstant>().filter { it.access != ComponentAccess.PUBLIC }
                XdkModuleUses(
                    identities.mapNotNullTo(hashSetOf()) { it.name },
                    types(constants).filterNotTo(hashSetOf()) { it.startsWith(own) },
                    identities
                        .filter { it.format == Constant.Format.Module || it.format == Constant.Format.Package }
                        .map(::path)
                        .filterNotTo(hashSetOf()) { it.startsWith(own) },
                    types(contributions + annotations + accesses).filterNotTo(hashSetOf()) { it.startsWith(own) },
                )
            }

        private fun components(root: Component): List<Component> = listOf(root) + DeclarationDigests.children(root).flatMap(::components)
    }
}
