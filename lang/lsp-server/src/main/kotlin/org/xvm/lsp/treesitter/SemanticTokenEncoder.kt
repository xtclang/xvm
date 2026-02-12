package org.xvm.lsp.treesitter

/**
 * Standard LSP semantic token legend: token types and modifiers.
 *
 * These lists are sent to the client during initialization so it knows how to interpret
 * the integer indices in the token data array.
 */
object SemanticTokenLegend {
    val TOKEN_TYPES: List<String> =
        listOf(
            "namespace", // 0
            "type", // 1
            "class", // 2
            "enum", // 3
            "interface", // 4
            "struct", // 5
            "typeParameter", // 6
            "parameter", // 7
            "variable", // 8
            "property", // 9
            "enumMember", // 10
            "event", // 11
            "function", // 12
            "method", // 13
            "macro", // 14
            "keyword", // 15
            "modifier", // 16
            "comment", // 17
            "string", // 18
            "number", // 19
            "regexp", // 20
            "operator", // 21
            "decorator", // 22
        )

    val TOKEN_MODIFIERS: List<String> =
        listOf(
            "declaration", // 0
            "definition", // 1
            "readonly", // 2
            "static", // 3
            "deprecated", // 4
            "abstract", // 5
            "async", // 6
            "modification", // 7
            "documentation", // 8
            "defaultLibrary", // 9
        )

    val TYPE_INDEX: Map<String, Int> = TOKEN_TYPES.withIndex().associate { (i, v) -> v to i }
    val MOD_INDEX: Map<String, Int> = TOKEN_MODIFIERS.withIndex().associate { (i, v) -> v to i }

    fun modifierBitmask(vararg mods: String): Int {
        var mask = 0
        for (mod in mods) {
            val bit = MOD_INDEX[mod] ?: continue
            mask = mask or (1 shl bit)
        }
        return mask
    }
}

/**
 * Walks an XTC tree-sitter AST and produces LSP semantic token data.
 *
 * A fresh instance should be created per request (accumulates mutable state).
 * Thread-safe since each `supplyAsync` gets its own instance.
 */
class SemanticTokenEncoder {
    private val tokens = mutableListOf<RawToken>()
    private val classified = mutableSetOf<Long>()

    private data class RawToken(
        val line: Int,
        val column: Int,
        val length: Int,
        val tokenType: Int,
        val tokenModifiers: Int,
    )

    fun encode(root: XtcNode): List<Int> {
        tokens.clear()
        classified.clear()
        walkNode(root)
        return deltaEncode(tokens.sortedWith(compareBy({ it.line }, { it.column })))
    }

    private fun walkNode(node: XtcNode) {
        when (node.type) {
            "class_declaration" -> classifyTypeDeclaration(node, "class")
            "interface_declaration" -> classifyTypeDeclaration(node, "interface")
            "mixin_declaration" -> classifyTypeDeclaration(node, "interface")
            "service_declaration" -> classifyTypeDeclaration(node, "class")
            "const_declaration" -> classifyTypeDeclaration(node, "struct", "readonly")
            "enum_declaration" -> classifyTypeDeclaration(node, "enum")
            "method_declaration" -> classifyMethodDeclaration(node)
            "constructor_declaration" -> classifyConstructorDeclaration(node)
            "property_declaration" -> classifyPropertyDeclaration(node)
            "variable_declaration" -> classifyVariableDeclaration(node)
            "parameter" -> classifyParameter(node)
            "module_declaration" -> classifyModuleDeclaration(node)
            "package_declaration" -> classifyPackageDeclaration(node)
            "annotation" -> classifyAnnotation(node)
            "type_expression" -> classifyTypeExpression(node)
            "call_expression" -> classifyCallExpression(node)
            "member_expression" -> classifyMemberExpression(node)
        }

        for (child in node.children) {
            if (!isClassified(child)) {
                walkNode(child)
            }
        }
    }

    private fun classifyTypeDeclaration(
        node: XtcNode,
        tokenType: String,
        vararg extraMods: String,
    ) {
        val typeName = node.childByType("type_name") ?: node.childByType("identifier")
        if (typeName != null) {
            val mods = buildModifiers(node, "declaration", *extraMods)
            emitToken(typeName, tokenType, mods)
        }
    }

    private fun classifyMethodDeclaration(node: XtcNode) {
        // Emit return type
        val returnType = node.childByType("type_expression")
        if (returnType != null) {
            classifyTypeExpression(returnType)
            markClassified(returnType)
        }

        // Emit method name
        val id = node.childByType("identifier")
        if (id != null) {
            val mods = buildModifiers(node, "declaration")
            emitToken(id, "method", mods)
        }

        // Classify parameters
        val params = node.childByType("parameters")
        if (params != null) {
            for (child in params.children) {
                if (child.type == "parameter") {
                    classifyParameter(child)
                    markClassified(child)
                }
            }
        }
    }

    private fun classifyConstructorDeclaration(node: XtcNode) {
        // Emit the "construct" keyword as a method token
        for (child in node.children) {
            if (child.type == "construct" || child.text == "construct") {
                emitToken(child, "method", SemanticTokenLegend.modifierBitmask("declaration"))
                break
            }
        }

        // Classify parameters
        val params = node.childByType("parameters")
        if (params != null) {
            for (child in params.children) {
                if (child.type == "parameter") {
                    classifyParameter(child)
                    markClassified(child)
                }
            }
        }
    }

    private fun classifyPropertyDeclaration(node: XtcNode) {
        val typeExpr = node.childByType("type_expression")
        if (typeExpr != null) {
            classifyTypeExpression(typeExpr)
            markClassified(typeExpr)
        }

        val id = node.childByType("identifier")
        if (id != null) {
            val mods = buildModifiers(node, "declaration")
            emitToken(id, "property", mods)
        }
    }

    private fun classifyVariableDeclaration(node: XtcNode) {
        val typeExpr = node.childByType("type_expression")
        if (typeExpr != null) {
            classifyTypeExpression(typeExpr)
            markClassified(typeExpr)
        }

        val id = node.childByType("identifier")
        if (id != null) {
            emitToken(id, "variable", SemanticTokenLegend.modifierBitmask("declaration"))
        }
    }

    private fun classifyParameter(node: XtcNode) {
        val typeExpr = node.childByType("type_expression")
        if (typeExpr != null) {
            classifyTypeExpression(typeExpr)
            markClassified(typeExpr)
        }

        val id = node.childByType("identifier")
        if (id != null) {
            emitToken(id, "parameter", SemanticTokenLegend.modifierBitmask("declaration"))
        }
    }

    private fun classifyModuleDeclaration(node: XtcNode) {
        val qname = node.childByType("qualified_name") ?: node.childByType("identifier")
        if (qname != null) {
            emitToken(qname, "namespace", SemanticTokenLegend.modifierBitmask("declaration"))
            markClassified(qname)
        }
    }

    private fun classifyPackageDeclaration(node: XtcNode) {
        val id = node.childByType("identifier")
        if (id != null) {
            emitToken(id, "namespace", SemanticTokenLegend.modifierBitmask("declaration"))
        }
    }

    private fun classifyAnnotation(node: XtcNode) {
        val id = node.childByType("identifier") ?: node.childByType("type_name")
        if (id != null) {
            emitToken(id, "decorator", 0)
            markClassified(id)
        }
    }

    private fun classifyTypeExpression(node: XtcNode) {
        val typeName = node.childByType("type_name")
        if (typeName != null) {
            emitToken(typeName, "type", 0)
            markClassified(typeName)
        }

        // Classify type parameters within the type expression
        for (child in node.children) {
            if (child.type == "type_parameter") {
                val id = child.childByType("identifier")
                if (id != null) {
                    emitToken(id, "typeParameter", 0)
                    markClassified(id)
                }
                markClassified(child)
            } else if (child.type == "type_expression") {
                classifyTypeExpression(child)
                markClassified(child)
            }
        }
    }

    private fun classifyCallExpression(node: XtcNode) {
        // Direct call: the function identifier
        val id = node.childByType("identifier")
        if (id != null) {
            emitToken(id, "method", 0)
            return
        }

        // Member call: obj.method(args) — classify the last identifier as method
        val memberExpr = node.childByType("member_expression")
        if (memberExpr != null) {
            val ids = memberExpr.children.filter { it.type == "identifier" }
            val lastId = ids.lastOrNull()
            if (lastId != null) {
                emitToken(lastId, "method", 0)
                markClassified(lastId)
            }
            markClassified(memberExpr)
        }
    }

    private fun classifyMemberExpression(node: XtcNode) {
        // Skip if parent is call_expression — classifyCallExpression handles it
        val parentType = node.parent?.type
        if (parentType == "call_expression") return

        val ids = node.children.filter { it.type == "identifier" }
        val lastId = ids.lastOrNull()
        if (lastId != null) {
            emitToken(lastId, "property", 0)
        }
    }

    private fun buildModifiers(
        node: XtcNode,
        vararg baseMods: String,
    ): Int {
        val mods = baseMods.toMutableList()

        for (child in node.children) {
            when (child.type) {
                "static" -> mods.add("static")
                "abstract" -> mods.add("abstract")
                "visibility_modifier" -> {
                    // No specific modifier for visibility in LSP semantic tokens
                }
            }
            // Check for readonly/immutable keywords
            if (child.text == "readonly" || child.text == "immutable") {
                mods.add("readonly")
            }
        }

        return SemanticTokenLegend.modifierBitmask(*mods.toTypedArray())
    }

    private fun emitToken(
        node: XtcNode,
        tokenType: String,
        modifiers: Int,
    ) {
        // Skip multi-line tokens — LSP semantic tokens are single-line
        if (node.startLine != node.endLine) return

        val typeIndex = SemanticTokenLegend.TYPE_INDEX[tokenType] ?: return
        val length = node.endColumn - node.startColumn
        if (length <= 0) return

        val key = nodeKey(node)
        if (key in classified) return
        classified.add(key)

        tokens.add(RawToken(node.startLine, node.startColumn, length, typeIndex, modifiers))
    }

    private fun deltaEncode(sortedTokens: List<RawToken>): List<Int> {
        if (sortedTokens.isEmpty()) return emptyList()

        val result = ArrayList<Int>(sortedTokens.size * 5)
        var prevLine = 0
        var prevColumn = 0

        for (token in sortedTokens) {
            val deltaLine = token.line - prevLine
            val deltaStart = if (deltaLine == 0) token.column - prevColumn else token.column

            result.add(deltaLine)
            result.add(deltaStart)
            result.add(token.length)
            result.add(token.tokenType)
            result.add(token.tokenModifiers)

            prevLine = token.line
            prevColumn = token.column
        }

        return result
    }

    private fun nodeKey(node: XtcNode): Long = (node.startLine.toLong() shl 32) or (node.startColumn.toLong() and 0xFFFFFFFFL)

    private fun isClassified(node: XtcNode): Boolean = nodeKey(node) in classified

    private fun markClassified(node: XtcNode) {
        classified.add(nodeKey(node))
    }
}
