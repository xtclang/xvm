package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.constants.FSNodeConstant
import org.xvm.asm.constants.FileStoreConstant
import org.xvm.asm.constants.StringConstant
import org.xvm.asm.constants.UInt8ArrayConstant
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.FileExpression
import org.xvm.compiler.ast.LiteralExpression
import java.security.MessageDigest
import java.util.HexFormat

private val resourcePrefixes = setOf("$.", "$/", "#.", "#/")

/** Detached resource values let a move reject changed contents even if both source graphs compile. */
internal fun compilerResourceValues(nodes: List<AstNode>): Map<SemanticModel.SourceLocation, String?> =
    nodes
        .filterIsInstance<Expression>()
        .filter { node ->
            // Parser lowers $path/#path includes directly to LiteralExpression. Their original
            // source span is retained even though the literal token now contains the resource bytes.
            node is FileExpression ||
                (node is LiteralExpression && node.source?.toString(node.startPosition, node.endPosition)?.take(2) in resourcePrefixes)
        }.associate { node ->
            fun position(at: Long) = SemanticModel.Position(Source.calculateLine(at), Source.calculateOffset(at))
            SemanticModel.SourceLocation(
                node.source?.fileName,
                SemanticModel.Range(position(node.startPosition), position(node.endPosition)),
            ) to
                resourceValue(node.toConstant())
        }

private fun resourceValue(value: Constant?): String? {
    val hash = MessageDigest.getInstance("SHA-256")

    fun bytes(value: ByteArray) {
        hash.update(value.size.toString().toByteArray())
        hash.update(0.toByte())
        hash.update(value)
    }

    fun text(value: String) = bytes(value.toByteArray())

    fun append(value: Constant?): Boolean {
        text(value?.format?.name ?: return false)
        when (value) {
            is StringConstant -> {
                text(value.value)
            }

            is UInt8ArrayConstant -> {
                bytes(value.value)
            }

            is FileStoreConstant -> {
                text(value.path)
                return append(value.value)
            }

            is FSNodeConstant -> {
                text(value.name)
                // Filesystem timestamps change during ordinary Move/Undo. Content and names
                // must remain equivalent; incidental timestamp metadata is not a binding.
                when (value.format) {
                    Constant.Format.FSFile -> {
                        bytes(value.fileBytes)
                    }

                    Constant.Format.FSDir -> {
                        val children = value.directoryContents.sortedBy { it.name }
                        text(children.size.toString())
                        return children.all(::append)
                    }

                    Constant.Format.FSLink -> {
                        return append(value.linkTarget)
                    }

                    else -> {
                        return false
                    }
                }
            }

            else -> {
                return false
            }
        }
        return true
    }
    return if (append(value)) HexFormat.of().formatHex(hash.digest()) else null
}
