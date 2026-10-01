package org.xtclang.idea.lsp

import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path

/** LSP4IJ server settings join the same global command as source edits and resource moves. */
internal class SourceGraphEdit
    private constructor(
        private val snapshot: Snapshot,
        private val graph: SourceGraphReplacement,
    ) {
        fun isCurrent(): Boolean = snapshot.isCurrent()

        /** This action runs first on Redo, before any file is changed. Undo's guard is below. */
        fun beforeApply() {
            UndoManager
                .getInstance(snapshot.project)
                .undoableActionPerformed(
                    object : BasicUndoableAction() {
                        override fun isGlobal(): Boolean = true

                        override fun undo() = Unit

                        override fun redo() {
                            replacement(graph.before, graph.after)
                        }
                    },
                )
        }

        fun apply() {
            snapshot.install(replacement(graph.before, graph.after))
            UndoManager
                .getInstance(snapshot.project)
                .undoableActionPerformed(
                    object : BasicUndoableAction() {
                        override fun isGlobal(): Boolean = true

                        override fun undo() = snapshot.install(replacement(graph.after, graph.before))

                        override fun redo() = snapshot.install(replacement(graph.before, graph.after))
                    },
                )
        }

        private fun replacement(
            expected: List<SourceModuleConfiguration>,
            next: List<SourceModuleConfiguration>,
        ): String =
            try {
                snapshot.replacement(expected, next)
            } catch (failure: IllegalArgumentException) {
                throw UnexpectedUndoException(failure.message)
            }

        class Snapshot(
            val project: Project,
            val serverId: String,
            val content: String?,
        ) {
            private val settings = CompilerSettings.store(project, serverId)

            fun isCurrent(): Boolean =
                !project.isDisposed &&
                    CompilerSettings.store(project, serverId) === settings &&
                    settings.getLanguageServerSettings(serverId)?.configurationContent == content

            fun replacement(graph: SourceGraphReplacement): SourceGraphEdit {
                SourceGraphConfiguration.replace(
                    requireNotNull(content) {
                        "Module rename requires explicit LSP4IJ compiler settings"
                    },
                    graph.before,
                    graph.after,
                    Path.of(requireNotNull(project.basePath)).toUri(),
                )
                return SourceGraphEdit(this, graph)
            }

            fun replacement(
                expected: List<SourceModuleConfiguration>,
                next: List<SourceModuleConfiguration>,
            ): String =
                SourceGraphConfiguration.replace(
                    requireSameStore(),
                    expected,
                    next,
                    Path.of(requireNotNull(project.basePath)).toUri(),
                )

            private fun requireSameStore(): String {
                require(CompilerSettings.store(project, serverId) === settings) {
                    "Compiler configuration ownership changed; rename history was not applied"
                }
                return requireNotNull(
                    settings.getLanguageServerSettings(serverId)?.configurationContent,
                )
            }

            fun install(content: String?) {
                val current = requireNotNull(settings.getLanguageServerSettings(serverId))
                val copy = XmlSerializerUtil.createCopy(current)
                copy.configurationContent = content
                settings.updateSettings(serverId, copy)
            }
        }

        companion object {
            fun capture(
                project: Project,
                serverId: String,
            ): Snapshot =
                Snapshot(
                    project,
                    serverId,
                    CompilerSettings
                        .store(project, serverId)
                        .getLanguageServerSettings(serverId)
                        ?.configurationContent,
                )
        }
    }
