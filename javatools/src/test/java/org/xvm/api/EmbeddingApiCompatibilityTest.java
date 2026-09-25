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
        assertEquals(named, original);
        assertEquals(named, structural);
        assertEquals(named, selectedCalls);
        assertSame(file.getConstantPool(), named.pool());
        assertEquals(0, switch (named) {
            case EmbeddingSupport.Compilation(var module, var structure, var ast,
                    var trees, var bindings, var functions) -> trees.size() + bindings.size() + functions.size();
        });
    }

    @Test
    public void cursorConstructorsRemainAvailableWhileRecordPatternsIncludeArgumentValues() {
        var type = new FileStructure("Compatibility").getModule().getIdentityConstant().getType();
        var original = new CursorBinding(List.of(), type, true);
        var candidates = new CursorBinding(List.of(), type, true, List.of(), List.of(), false);
        var functions = new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of());
        var values = new CursorBinding(List.of(), type, true, List.of(), List.of(), false, List.of(), List.of());
        assertEquals(original, candidates);
        assertEquals(original, functions);
        assertEquals(original, values);
        assertEquals(0, switch (candidates) {
            case CursorBinding(var variables, var thisType, var instance, var types,
                    var methods, var inspected, var callable, var argumentValues, var properties) ->
                    argumentValues.size() + properties.size();
        });
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
