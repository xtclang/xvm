package org.xvm.lsp.adapter.xdk

import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MultipleLValueStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.ParenthesizedExpression
import org.xvm.compiler.ast.SequentialAssignExpression
import org.xvm.compiler.ast.TupleExpression
import org.xvm.lsp.adapter.xdk.SemanticModel.Usage

/** Syntactic assignment targets, paired later with resolved identities. Receivers remain reads. */
internal fun compilerWrites(nodes: List<AstNode>): List<Pair<NameExpression, Usage>> =
    nodes.flatMap { node ->
        when (node) {
            is AssignmentStatement -> {
                val usage =
                    when (node.category) {
                        AssignmentStatement.Category.InPlace, AssignmentStatement.Category.CondLeft -> Usage.READ_WRITE
                        else -> Usage.WRITE
                    }
                writeTargets(node.lValue).map { it to usage }
            }

            is SequentialAssignExpression -> {
                node
                    .children()
                    .iterator()
                    .asSequence()
                    .flatMap { writeTargets(it) }
                    .map { it to Usage.READ_WRITE }
                    .toList()
            }

            else -> {
                emptyList()
            }
        }
    }

private fun writeTargets(node: AstNode): List<NameExpression> =
    when (node) {
        is NameExpression -> {
            listOf(node)
        }

        is MultipleLValueStatement, is TupleExpression, is ParenthesizedExpression -> {
            node
                .children()
                .iterator()
                .asSequence()
                .flatMap { writeTargets(it) }
                .toList()
        }

        else -> {
            emptyList()
        }
    }
