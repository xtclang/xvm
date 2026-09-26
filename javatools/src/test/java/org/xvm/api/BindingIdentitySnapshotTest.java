package org.xvm.api;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.InvocationBinding;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.IncompleteStatement;
import org.xvm.compiler.ast.InvocationExpression;
import org.xvm.compiler.ast.NameExpression;
import org.xvm.compiler.ast.StatementBlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Publication must preserve distinct syntax identities even when an AST subclass adds equality. */
public class BindingIdentitySnapshotTest {
    @Test
    public void methodFactsPreserveIdentityAtEveryPublicationBoundary() {
        var host      = new HostInputs();
        var pool      = host.file().getConstantPool();
        var signature = pool.ensureSignatureConstant("call", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method    = pool.ensureMethodConstant(host.file().getModule().getIdentityConstant(), signature);
        var binding   = new InvocationBinding(method, signature, List.of());

        assertEquals(new EqualInvocation(), new EqualInvocation());
        List.<UnaryOperator<Map<InvocationExpression, InvocationBinding>>>of(
                methods -> new InvocationBinding.Facts(methods, Map.of()).methods(),
                methods -> host.compilation(methods, Map.of()).callBindings(),
                methods -> host.partial(methods, Map.of(), Map.of()).callBindings())
                .forEach(copy -> verifySnapshot(copy, EqualInvocation::new, binding));
    }

    @Test
    public void functionFactsPreserveIdentityAtEveryPublicationBoundary() {
        var host = new HostInputs();
        var type = host.file().getConstantPool().buildFunctionType(ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var call = new InvocationBinding.FunctionCall(type, List.of());

        List.<UnaryOperator<Map<InvocationExpression, InvocationBinding.FunctionCall>>>of(
                functions -> new InvocationBinding.Facts(Map.of(), functions).functions(),
                functions -> host.compilation(Map.of(), functions).functionBindings(),
                functions -> host.partial(Map.of(), Map.of(), functions).functionBindings())
                .forEach(copy -> verifySnapshot(copy, EqualInvocation::new, call));
    }

    @Test
    public void cursorFactsAreDetachedImmutableAndRejectNullEntries() {
        var host    = new HostInputs();
        var binding = new CursorBinding(List.of(), host.file().getModule().getIdentityConstant().getType(), false);

        verifySnapshot(cursors -> host.partial(Map.of(), cursors, Map.of()).cursorBindings(),
                () -> new IncompleteStatement(name(), 4, "TEST"), binding);
    }

    private static <K, V> void verifySnapshot(UnaryOperator<Map<K, V>> copy, Supplier<K> key, V value) {
        K first  = key.get();
        K second = key.get();
        Map<K, V> source = new IdentityHashMap<>();
        source.put(first, value);
        source.put(second, value);

        Map<K, V> snapshot = copy.apply(source);
        assertEquals(2, snapshot.size());
        assertSame(value, snapshot.get(first));
        assertSame(value, snapshot.get(second));
        assertFalse(snapshot.containsKey(key.get()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(key.get(), value));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(first));
        source.clear();
        assertEquals(2, snapshot.size());

        source.put(null, value);
        assertThrows(NullPointerException.class, () -> copy.apply(source));
        source.clear();
        source.put(first, null);
        assertThrows(NullPointerException.class, () -> copy.apply(source));
        assertThrows(NullPointerException.class, () -> copy.apply(null));
    }

    private static Token name() {
        return new Token(0, 4, Id.IDENTIFIER, "call");
    }

    private static class EqualInvocation extends InvocationExpression {
        EqualInvocation() {
            super(new NameExpression(name()), false, List.of(), 4);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualInvocation;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private record HostInputs(FileStructure file, StatementBlock tree) {
        HostInputs() {
            this(new FileStructure("IdentityFacts"), new StatementBlock(List.of()));
        }

        EmbeddingSupport.Compilation compilation(Map<InvocationExpression, InvocationBinding> methods,
                Map<InvocationExpression, InvocationBinding.FunctionCall> functions) {
            return new EmbeddingSupport.Compilation(file.getModule(), file, tree, List.of(tree), methods, functions);
        }

        EmbeddingSupport.PartialAnalysis partial(Map<InvocationExpression, InvocationBinding> methods,
                Map<IncompleteStatement, CursorBinding> cursors,
                Map<InvocationExpression, InvocationBinding.FunctionCall> functions) {
            return new EmbeddingSupport.PartialAnalysis(List.of(tree), List.of(),
                    Optional.of(file.getConstantPool()), methods, cursors, functions);
        }
    }
}
