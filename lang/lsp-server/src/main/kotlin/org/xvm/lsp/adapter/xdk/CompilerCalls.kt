package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.Source
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.tool.ModuleInfo

/** All normal, dependency and refactoring-proof compilations cross this timed boundary. */
internal class CompilerCalls(
    private val sourceCompiler: (Source, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val treeCompiler: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val cursorCompiler: (
        Source,
        ModuleInfo?,
        Long,
        ModuleRepository?,
        ErrorListener,
    ) -> EmbeddingSupport.PartialAnalysis,
) {
    fun compileSource(
        source: Source,
        repository: ModuleRepository?,
        errors: ErrorListener,
    ): EmbeddingSupport.Compilation =
        ExecutionTrace.api("EmbeddingSupport.compileModule(source)", source.fileName) {
            sourceCompiler(source, repository, errors)
        }

    fun compileTree(
        sources: ModuleInfo,
        repository: ModuleRepository?,
        errors: ErrorListener,
    ): EmbeddingSupport.Compilation =
        ExecutionTrace.api(
            "EmbeddingSupport.compileModule(tree)",
            sources.sourceFile.toURI().toString(),
        ) {
            treeCompiler(sources, repository, errors)
        }

    fun analyzeCursor(
        source: Source,
        sources: ModuleInfo?,
        cursor: Long,
        repository: ModuleRepository?,
        errors: ErrorListener,
    ): EmbeddingSupport.PartialAnalysis =
        ExecutionTrace.api("EmbeddingSupport.analyzeIncomplete", source.fileName) {
            cursorCompiler(source, sources, cursor, repository, errors)
        }
}
