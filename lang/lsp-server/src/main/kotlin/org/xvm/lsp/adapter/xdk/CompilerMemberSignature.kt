package org.xvm.lsp.adapter.xdk

import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.CharConstant
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.DifferenceTypeConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ImmutableTypeConstant
import org.xvm.asm.constants.IntConstant
import org.xvm.asm.constants.IntersectionTypeConstant
import org.xvm.asm.constants.ParameterizedTypeConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.RecursiveTypeConstant
import org.xvm.asm.constants.RelationalTypeConstant
import org.xvm.asm.constants.SignatureConstant
import org.xvm.asm.constants.SingletonConstant
import org.xvm.asm.constants.StringConstant
import org.xvm.asm.constants.TerminalTypeConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.asm.constants.UnionTypeConstant

/**
 * Render compiler-selected types, never a diagnostic display string or copied source expression.
 */
internal fun memberSignature(
    signature: SignatureConstant,
    declaration: MethodStructure,
    owner: IdentityConstant,
    modules: Map<String, String>,
    literalDefaults: Map<String, String>,
): String? {
    if (
        signature.paramCount != declaration.params.size ||
        declaration.params.any { it.annotations.isNotEmpty() }
    ) {
        return null
    }
    val formals =
        declaration.params.take(declaration.typeParamCount).associate { parameter ->
            val name = parameter.name?.takeIf(XdkRename::identifier) ?: return null
            parameter.asTypeParameterConstant(declaration.identityConstant) to name
        }

    fun render(type: TypeConstant): String? = type.memberSourceType(owner, formals, modules)
    val typeParameters =
        signature.params.take(declaration.typeParamCount).zip(formals.values).map { (type, name) ->
            val constraint = type.paramTypes.singleOrNull() ?: return null
            if (constraint == constraint.constantPool.typeObject()) {
                name
            } else {
                "$name extends ${render(constraint) ?: return null}"
            }
        }
    val parameters =
        signature.params.zip(declaration.params).drop(declaration.typeParamCount).map { (type, parameter) ->
            val name = parameter.name?.takeIf(XdkRename::identifier) ?: return null
            val rendered = render(type) ?: return null
            val default =
                if (parameter.hasDefaultValue()) {
                    // Validated primitive constants are independent of their original
                    // expression/scope.
                    // Fresh declaration repair accepts only parser-owned literal tokens; the
                    // complete
                    // proposed compilation must validate both the original and generated defaults.
                    val value =
                        when (val constant = parameter.defaultValue) {
                            is StringConstant -> {
                                constant.valueString
                            }

                            is CharConstant -> {
                                constant.valueString
                            }

                            is IntConstant -> {
                                constant.valueString
                            }

                            is SingletonConstant -> {
                                when (constant) {
                                    constant.constantPool.valTrue() -> "ecstasy.Boolean.True"
                                    constant.constantPool.valFalse() -> "ecstasy.Boolean.False"
                                    constant.constantPool.valNull() -> "ecstasy.Nullable.Null"
                                    else -> return null
                                }
                            }

                            null -> {
                                literalDefaults[name] ?: return null
                            }

                            else -> {
                                return null
                            }
                        }
                    " = $value"
                } else {
                    ""
                }
            "$rendered $name$default"
        }
    val returns =
        signature.returns.drop(if (declaration.isConditionalReturn) 1 else 0).map {
            render(it) ?: return null
        }
    val result =
        when (returns.size) {
            0 -> "void"
            1 -> returns.single()
            else -> returns.joinToString(", ", "(", ")")
        }
    val generic = if (typeParameters.isEmpty()) "" else typeParameters.joinToString(", ", "<", "> ")
    val conditional = if (declaration.isConditionalReturn) "conditional " else ""
    return "$generic$conditional$result ${signature.name}(${parameters.joinToString(", ")})"
}

/**
 * Supported type constructors and this method's own formals. Recursive aliases require a proven
 * destination spelling and import route before code generation can support them.
 */
internal fun TypeConstant.memberSourceType(
    owner: IdentityConstant,
    formals: Map<TypeParameterConstant, String>,
    modules: Map<String, String>,
): String? {
    return when (this) {
        // A recursive alias is a TerminalTypeConstant without a single defining constant.
        is RecursiveTypeConstant -> {
            null
        }

        is ParameterizedTypeConstant -> {
            val base = underlyingType.memberSourceType(owner, formals, modules) ?: return null
            val arguments =
                paramTypes.map {
                    it.memberSourceType(owner, formals, modules) ?: return null
                }
            arguments.joinToString(", ", "$base<", ">")
        }

        is ImmutableTypeConstant -> {
            underlyingType.memberSourceType(owner, formals, modules)?.let { "immutable $it" }
        }

        is RelationalTypeConstant -> {
            val operator =
                when (this) {
                    is UnionTypeConstant -> "|"
                    is IntersectionTypeConstant -> "+"
                    is DifferenceTypeConstant -> "-"
                    else -> return null
                }
            val left = underlyingType.memberSourceType(owner, formals, modules) ?: return null
            val right = underlyingType2.memberSourceType(owner, formals, modules) ?: return null
            "($left $operator $right)"
        }

        is TerminalTypeConstant -> {
            when (val identity = definingConstant) {
                is TypeParameterConstant -> {
                    formals[identity]
                }

                is PropertyConstant -> {
                    // Class formals are properties, not method type parameters. Only the exact
                    // owning class or an enclosing class can supply this spelling; an unrelated
                    // owner's identically named formal must never be substituted by name.
                    identity.name.takeIf { it ->
                        identity.isFormalType && XdkRename.identifier(it) &&
                            generateSequence(owner) { it.parentConstant }.any { it == identity.parentConstant }
                    }
                }

                is ClassConstant -> {
                    when {
                        constantPool.getImplicitlyImportedIdentity(identity.name) == identity -> {
                            identity.name
                        }

                        identity.moduleConstant == owner.moduleConstant -> {
                            identity.pathString
                        }

                        XdkAutoImports.target(identity) != null -> {
                            modules[identity.moduleConstant.name]?.let {
                                "$it.${identity.pathString}"
                            }
                        }

                        else -> {
                            null
                        }
                    }
                }

                else -> {
                    null
                }
            }
        }

        else -> {
            null
        }
    }
}

/** Inspect only type structures whose source spelling the renderer understands. */
internal fun TypeConstant.memberClasses(): List<ClassConstant> =
    when (this) {
        is RecursiveTypeConstant -> {
            emptyList()
        }

        is ParameterizedTypeConstant -> {
            underlyingType.memberClasses() + paramTypes.flatMap { it.memberClasses() }
        }

        is RelationalTypeConstant -> {
            underlyingType.memberClasses() + underlyingType2.memberClasses()
        }

        is ImmutableTypeConstant -> {
            underlyingType.memberClasses()
        }

        is TerminalTypeConstant -> {
            listOfNotNull(definingConstant as? ClassConstant)
        }

        else -> {
            emptyList()
        }
    }
