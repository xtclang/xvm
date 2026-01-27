package org.xtclang.tooling.generators

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.xtclang.tooling.model.KeywordCategory
import org.xtclang.tooling.model.LanguageModel
import org.xtclang.tooling.model.OperatorCategory

/**
 * Generates TextMate grammar files (.tmLanguage.json)
 *
 * TextMate grammars are supported by:
 * - VS Code (primary syntax highlighting)
 * - Zed
 * - Cursor
 * - Sublime Text
 * - Atom (legacy)
 * - TextMate
 * - Many other editors via plugins
 */
class TextMateGenerator(
    private val model: LanguageModel,
) {
    companion object {
        private val json = Json { prettyPrint = true }
    }

    fun generate(): String {
        val grammar =
            buildJsonObject {
                put("name", model.name)
                put("scopeName", model.scopeName)
                putJsonArray("fileTypes") {
                    model.fileExtensions.forEach { addString(it) }
                }

                putJsonObject("repository") {
                    // Comments
                    putJsonObject("comments") {
                        putJsonArray("patterns") {
                            // Doc comments
                            addJsonObject {
                                put("name", "comment.block.documentation.xtc")
                                put("begin", "/\\*\\*")
                                put("end", "\\*/")
                                putJsonArray("patterns") {
                                    addJsonObject {
                                        put("name", "keyword.other.documentation.xtc")
                                        put("match", "@[a-zA-Z]+")
                                    }
                                }
                            }
                            // Block comments
                            addJsonObject {
                                put("name", "comment.block.xtc")
                                put("begin", "/\\*")
                                put("end", "\\*/")
                            }
                            // Line comments
                            addJsonObject {
                                put("name", "comment.line.double-slash.xtc")
                                put("match", "//.*$")
                            }
                        }
                    }

                    // Keywords - must be array of patterns
                    putJsonObject("keywords") {
                        putJsonArray("patterns") {
                            // Control flow keywords
                            addJsonObject {
                                put("name", "keyword.control.xtc")
                                put("match", "\\b(${controlFlowKeywords().joinToString("|")})\\b")
                            }
                            // Declaration keywords
                            addJsonObject {
                                put("name", "keyword.declaration.xtc")
                                put("match", "\\b(${declarationKeywords().joinToString("|")})\\b")
                            }
                            // Modifier keywords
                            addJsonObject {
                                put("name", "storage.modifier.xtc")
                                put("match", "\\b(${modifierKeywords().joinToString("|")})\\b")
                            }
                        }
                    }

                    // Strings
                    putJsonObject("strings") {
                        putJsonArray("patterns") {
                            // Template strings
                            addJsonObject {
                                put("name", "string.interpolated.xtc")
                                put("begin", "\\$\"")
                                put("end", "\"")
                                putJsonArray("patterns") {
                                    addJsonObject {
                                        put("name", "constant.character.escape.xtc")
                                        put("match", "\\\\.")
                                    }
                                    addJsonObject {
                                        put("name", "variable.other.interpolated.xtc")
                                        put("begin", "\\{")
                                        put("end", "\\}")
                                        putJsonArray("patterns") {
                                            addJsonObject { put("include", "#expression") }
                                        }
                                    }
                                }
                            }
                            // Regular strings
                            addJsonObject {
                                put("name", "string.quoted.double.xtc")
                                put("begin", "\"")
                                put("end", "\"")
                                putJsonArray("patterns") {
                                    addJsonObject {
                                        put("name", "constant.character.escape.xtc")
                                        put("match", "\\\\.")
                                    }
                                }
                            }
                            // Character literals
                            addJsonObject {
                                put("name", "string.quoted.single.xtc")
                                put("begin", "'")
                                put("end", "'")
                                putJsonArray("patterns") {
                                    addJsonObject {
                                        put("name", "constant.character.escape.xtc")
                                        put("match", "\\\\.")
                                    }
                                }
                            }
                        }
                    }

                    // Numbers
                    putJsonObject("numbers") {
                        putJsonArray("patterns") {
                            // Hex
                            addJsonObject {
                                put("name", "constant.numeric.hex.xtc")
                                put("match", "\\b0[xX][0-9a-fA-F][0-9a-fA-F_]*\\b")
                            }
                            // Binary
                            addJsonObject {
                                put("name", "constant.numeric.binary.xtc")
                                put("match", "\\b0[bB][01][01_]*\\b")
                            }
                            // Float
                            addJsonObject {
                                put("name", "constant.numeric.float.xtc")
                                put("match", "\\b[0-9][0-9_]*\\.[0-9][0-9_]*([eE][+-]?[0-9]+)?\\b")
                            }
                            // Integer
                            addJsonObject {
                                put("name", "constant.numeric.integer.xtc")
                                put("match", "\\b[0-9][0-9_]*\\b")
                            }
                        }
                    }

                    // Types
                    putJsonObject("types") {
                        putJsonArray("patterns") {
                            // Built-in types
                            addJsonObject {
                                put("name", "support.type.builtin.xtc")
                                put("match", "\\b(${builtinTypes().joinToString("|")})\\b")
                            }
                            // Type names (PascalCase)
                            addJsonObject {
                                put("name", "entity.name.type.xtc")
                                put("match", "\\b[A-Z][a-zA-Z0-9_]*\\b")
                            }
                        }
                    }

                    // Annotations
                    putJsonObject("annotations") {
                        put("name", "storage.type.annotation.xtc")
                        put("match", "@[a-zA-Z_][a-zA-Z0-9_]*")
                    }

                    // Constants - from model
                    putJsonObject("constants") {
                        putJsonArray("patterns") {
                            // Boolean constants from model
                            val booleans = model.booleanLiterals
                            if (booleans.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "constant.language.boolean.xtc")
                                    put("match", "\\b(${booleans.joinToString("|")})\\b")
                                }
                            }
                            // Null constant from model
                            val nullLit = model.nullLiteral
                            if (nullLit != null) {
                                addJsonObject {
                                    put("name", "constant.language.null.xtc")
                                    put("match", "\\b$nullLit\\b")
                                }
                            }
                        }
                    }

                    // Operators - generated from model
                    putJsonObject("operators") {
                        putJsonArray("patterns") {
                            // Assignment operators from model
                            val assignmentOps = operatorsByCategory(OperatorCategory.ASSIGNMENT)
                            if (assignmentOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.assignment.xtc")
                                    put("match", assignmentOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Comparison operators from model
                            val comparisonOps = operatorsByCategory(OperatorCategory.COMPARISON)
                            if (comparisonOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.comparison.xtc")
                                    put("match", comparisonOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Logical operators from model
                            val logicalOps = operatorsByCategory(OperatorCategory.LOGICAL)
                            if (logicalOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.logical.xtc")
                                    put("match", logicalOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Bitwise operators from model
                            val bitwiseOps = operatorsByCategory(OperatorCategory.BITWISE)
                            if (bitwiseOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.bitwise.xtc")
                                    put("match", bitwiseOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Arithmetic operators from model
                            val arithmeticOps = operatorsByCategory(OperatorCategory.ARITHMETIC)
                            if (arithmeticOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.arithmetic.xtc")
                                    put("match", arithmeticOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Member access operators from model
                            val memberAccessOps = operatorsByCategory(OperatorCategory.MEMBER_ACCESS)
                            if (memberAccessOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.access.xtc")
                                    put("match", memberAccessOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                            // Other operators from model (range, etc.)
                            val otherOps = operatorsByCategory(OperatorCategory.OTHER)
                            if (otherOps.isNotEmpty()) {
                                addJsonObject {
                                    put("name", "keyword.operator.other.xtc")
                                    put("match", otherOps.sortedByDescending { it.length }.joinToString("|"))
                                }
                            }
                        }
                    }

                    // Function calls
                    putJsonObject("function-call") {
                        put("name", "meta.function-call.xtc")
                        put("match", "\\b([a-z_][a-zA-Z0-9_]*)\\s*(?=\\()")
                        putJsonObject("captures") {
                            putJsonObject("1") {
                                put("name", "entity.name.function.xtc")
                            }
                        }
                    }

                    // Class declarations
                    putJsonObject("class-declaration") {
                        put("name", "meta.class.xtc")
                        put("begin", "\\b(class|interface|mixin|service|const|enum)\\s+([A-Z][a-zA-Z0-9_]*)")
                        put("end", "(?=\\{|$)")
                        putJsonObject("beginCaptures") {
                            putJsonObject("1") {
                                put("name", "keyword.declaration.xtc")
                            }
                            putJsonObject("2") {
                                put("name", "entity.name.type.class.xtc")
                            }
                        }
                        putJsonArray("patterns") {
                            addJsonObject { put("include", "#type-parameters") }
                            addJsonObject { put("include", "#inheritance") }
                        }
                    }

                    // Module declaration
                    putJsonObject("module-declaration") {
                        put("name", "meta.module.xtc")
                        put("match", "\\b(module)\\s+([a-zA-Z_][a-zA-Z0-9_.]*)")
                        putJsonObject("captures") {
                            putJsonObject("1") {
                                put("name", "keyword.declaration.module.xtc")
                            }
                            putJsonObject("2") {
                                put("name", "entity.name.type.module.xtc")
                            }
                        }
                    }

                    // Method declaration
                    putJsonObject("method-declaration") {
                        put("name", "meta.method.xtc")
                        put("begin", "\\b([a-zA-Z_][a-zA-Z0-9_<>,\\s]*)\\s+([a-z_][a-zA-Z0-9_]*)\\s*(?=\\()")
                        put("end", "(?=\\{|;)")
                        putJsonObject("beginCaptures") {
                            putJsonObject("1") {
                                put("name", "storage.type.return.xtc")
                            }
                            putJsonObject("2") {
                                put("name", "entity.name.function.xtc")
                            }
                        }
                    }

                    // Expression (placeholder for complex expressions)
                    putJsonObject("expression") {
                        putJsonArray("patterns") {
                            includeAll("strings", "numbers", "constants", "operators", "function-call")
                        }
                    }
                }

                // Main patterns
                putJsonArray("patterns") {
                    includeAll(
                        "comments",
                        "annotations",
                        "module-declaration",
                        "class-declaration",
                        "method-declaration",
                        "keywords",
                        "types",
                        "strings",
                        "numbers",
                        "constants",
                        "operators",
                        "function-call",
                    )
                }
            }

        return json.encodeToString(JsonObject.serializer(), grammar)
    }

    private fun controlFlowKeywords(): List<String> {
        val control = model.keywordsByCategory(KeywordCategory.CONTROL)
        val exception = model.keywordsByCategory(KeywordCategory.EXCEPTION).filter { !it.contains(":") }
        return control + exception
    }

    private fun declarationKeywords() = model.keywordsByCategory(KeywordCategory.DECLARATION)

    private fun modifierKeywords(): List<String> {
        val modifiers = model.keywordsByCategory(KeywordCategory.MODIFIER)
        val typeRelations = model.keywordsByCategory(KeywordCategory.TYPE_RELATION)
        return modifiers + typeRelations
    }

    private fun builtinTypes() = model.builtinTypes

    private fun operatorsByCategory(category: OperatorCategory) =
        model.operators.filter { it.category == category }.map { escapeRegex(it.symbol) }
}
