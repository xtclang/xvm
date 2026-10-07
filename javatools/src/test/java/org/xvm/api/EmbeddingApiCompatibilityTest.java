package org.xvm.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.InvocationBinding;
import org.xvm.compiler.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable migration examples for the integrated API; extraction still needs its own build. */
public class EmbeddingApiCompatibilityTest {
    @Test
    public void reportAndAbortAreSeparateOperations() {
        List<ErrorListener.ErrorInfo> heard = new ArrayList<>();
        ErrorListener errors = ErrorListener.collecting(heard::add);
        errors.error("PARSER-04", ErrorListener.NOWHERE, "example");
        assertEquals(1, heard.size());
        assertTrue(errors.hasSeriousErrors());
        assertFalse(errors.isAbortDesired());
    }

    @Test
    public void nullListenerIsRejectedBeforeCompilation() {
        var support = new EmbeddingSupport().configure(new BuildRepository(), null);
        assertThrows(NullPointerException.class,
                () -> support.compileModule(new Source("module Compatibility {}"), null, null));
    }

    @Test
    public void originalCompilationConstructorsRemainAvailable() {
        var file = new FileStructure("Compatibility");
        var named = EmbeddingSupport.Compilation.forFile(file);
        // Positional nulls intentionally exercise the old source and binary constructor signatures.
        var original = new EmbeddingSupport.Compilation(null, file, null);
        var structural = new EmbeddingSupport.Compilation(null, file, null, List.of());
        var selectedCalls = new EmbeddingSupport.Compilation(null, file, null, List.of(), Map.of());
        var functionCalls = new EmbeddingSupport.Compilation(null, file, null, List.of(), Map.of(), Map.of());
        var constructorCalls = new EmbeddingSupport.Compilation(null, file, null, List.of(), Map.of(), Map.of(), Map.of());
        assertEquals(named, constructorCalls);
        assertEquals(named, original);
        assertEquals(named, structural);
        assertEquals(named, selectedCalls);
        assertEquals(named, functionCalls);
        assertSame(file.getConstantPool(), named.pool());
        assertEquals(0, switch (named) {
            case EmbeddingSupport.Compilation(var module, var structure, var ast,
                    var trees, var bindings, var functions, var constructors, var initializers) ->
                        trees.size() + bindings.size() + functions.size() + constructors.size() + initializers.size();
        });
    }

    @Test
    public void partialAnalysisAcceptsAnAbsentPoolWithoutAnOptionalParameter() {
        var pool = new FileStructure("PartialCompatibility").getConstantPool();
        var syntaxOnly = new EmbeddingSupport.PartialAnalysis(List.of(), List.of(), null);
        assertNull(syntaxOnly.pool());
        assertTrue(syntaxOnly.callBindings().isEmpty());

        List.of(
                new EmbeddingSupport.PartialAnalysis(List.of(), List.of(), pool),
                new EmbeddingSupport.PartialAnalysis(List.of(), List.of(), pool, Map.of()),
                new EmbeddingSupport.PartialAnalysis(List.of(), List.of(), pool, Map.of(), Map.of()),
                new EmbeddingSupport.PartialAnalysis(List.of(), List.of(), pool, Map.of(), Map.of(), Map.of()))
            .forEach(analysis -> assertSame(pool, analysis.pool()));
    }

    @Test
    public void cursorConstructorsRemainAvailableWhileRecordPatternsIncludeAllFacts() {
        var type = new FileStructure("Compatibility").getModule().getIdentityConstant().getType();
        var original = new CursorBinding(List.of(), type, true);
        var candidates = new CursorBinding(List.of(), type, true, List.of(), List.of(), false);
        var functions = new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of());
        var values = new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of());
        assertEquals(original, candidates);
        assertEquals(original, functions);
        assertEquals(original, values);
        // Exercise every retained public descriptor, not just the oldest source constructors.
        List.of(
                new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of(), List.of()),
                new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of()),
                new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of()),
                new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new CursorBinding(List.of(), type, true, List.of(), original.callFacts()))
            .forEach(retained -> assertEquals(original, retained));
        var literalScope = original.withArgumentLiterals(List.of("0"));
        assertEquals(List.of("0"), literalScope.withCandidates(List.of()).withTypes(List.of())
                .withFormals(List.of()).withArgumentProperties(List.of()).argumentLiterals());
        assertEquals(List.of("0"), literalScope.callFacts().withFunctions(List.of()).argumentLiterals());
        assertEquals(0, switch (candidates) {
            case CursorBinding(var variables, var thisType, var instance, var types,
                    var methods, var inspected, var callable, var argumentValues, var properties, var formals,
                    var literals, var expressions, var enclosing, var templates) -> argumentValues.size() + properties.size() + formals.size()
                            + literals.size() + expressions.size() + enclosing.size() + templates.size();
        });
    }

    @Test
    public void updatingCallFactsPreservesIndependentCursorProposals() {
        var type = new FileStructure("Compatibility").getModule().getIdentityConstant().getType();
        var scope = new CursorBinding(List.of(), type, true)
                .withArgumentLiterals(List.of("0"))
                .withArgumentExpressions(List.of("value"))
                .withEnclosingExpressions(List.of("Outer.this"))
                .withArgumentTemplates(List.of("() -> TODO()"));
        var updated = scope.withCandidates(List.of()).withFunctions(List.of())
                .withArgumentProperties(List.of()).withTypes(List.of()).withFormals(List.of());
        assertTrue(updated.callsInspected());
        assertFalse(scope.callsInspected());
        assertEquals(scope.argumentLiterals(), updated.argumentLiterals());
        assertEquals(scope.argumentExpressions(), updated.argumentExpressions());
        assertEquals(scope.enclosingExpressions(), updated.enclosingExpressions());
        assertEquals(scope.argumentTemplates(), updated.argumentTemplates());
        assertThrows(UnsupportedOperationException.class, () -> updated.argumentTemplates().add("invalid"));
    }

    @Test
    public void argumentConstructorsRemainAvailableWhileRecordPatternsIncludeLabel() {
        var positional = new InvocationBinding.Argument(1L, 2L, 0);
        var legacyNamed = new InvocationBinding.Argument(1L, 2L, 0, true);
        var label = new InvocationBinding.Label("input", 1L, 2L);
        var named = new InvocationBinding.Argument(1L, 8L, 0, true, label);
        assertFalse(positional.named());
        assertNull(positional.label());
        assertTrue(legacyNamed.named());
        assertNull(legacyNamed.label());
        assertEquals("input", switch (named) {
            case InvocationBinding.Argument(var start, var end, var index, var isNamed,
                    var writtenLabel) -> writtenLabel.name();
        });
    }

    @Test
    @SuppressWarnings("deprecation")
    public void oldPoolNameDelegatesToExplicitRuntimeInitialization() {
        var pool = new FileStructure("Compatibility").getConstantPool();
        var support = new EmbeddingSupport() {
            @Override
            public ConstantPool ensureRuntimePool() {
                return pool;
            }
        };
        assertSame(pool, support.getConstantPool());
    }
}
