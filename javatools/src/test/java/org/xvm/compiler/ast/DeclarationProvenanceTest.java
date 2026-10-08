package org.xvm.compiler.ast;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Register;

import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AST4 lifetime controls: observation is passive, resolution is resumable, clones need owners. */
class DeclarationProvenanceTest {
    @Test
    void deferredResolutionPublishesOnlyCompletedSegmentsAndDoesNotDuplicateThemOnRetry() {
        var owner = new ResolvingScope();
        var type = owner.type("Outer", "Inner");
        var resolver = type.getNameResolver();
        var errors = new ErrorList();
        assertEquals(NameResolver.Result.DEFERRED, resolver.resolve(errors));
        assertTrue(resolver.getResolvedNames().isEmpty());
        assertTrue(type.getNameBindings().stream().allMatch(binding -> binding.target() == null));

        owner.ready = true;
        // Observing bindings must not advance a resolver even when its dependency is now ready.
        assertTrue(type.getNameBindings().stream().allMatch(binding -> binding.target() == null));
        assertEquals(NameResolver.Result.RESOLVED, resolver.resolve(errors));
        var resolved = resolver.getResolvedNames();
        assertEquals(List.of("Outer", "Inner"), type.getNameBindings().stream()
                .map(binding -> binding.name().getValueText()).toList());
        assertEquals(resolved, type.getNameBindings().stream().map(NamedTypeExpression.NameBinding::target).toList());
        assertEquals(2, resolved.size());
        assertEquals(NameResolver.Result.RESOLVED, resolver.resolve(errors));
        assertEquals(resolved, resolver.getResolvedNames());
        assertThrows(UnsupportedOperationException.class, resolved::clear);
        assertThrows(UnsupportedOperationException.class, () -> type.getNameBindings().clear());
        assertTrue(errors.getErrors().isEmpty());
    }

    @Test
    void failedSuffixRetainsTheProvenPrefixAndTerminalRetriesDoNotInventAnotherBinding() {
        var owner = new ResolvingScope();
        owner.ready = true;
        var type = owner.type("Outer", "Inner", "Missing");
        var before = type.getNameBindings();
        var resolver = type.getNameResolver();
        var errors = new ErrorList();
        assertEquals(NameResolver.Result.ERROR, resolver.resolve(errors));
        var resolved = resolver.getResolvedNames();
        assertEquals(2, resolved.size());
        assertEquals(resolved, type.getNameBindings().subList(0, 2).stream()
                .map(NamedTypeExpression.NameBinding::target).toList());
        assertNull(type.getNameBindings().getLast().target());
        assertTrue(before.stream().allMatch(binding -> binding.target() == null));
        assertEquals(1, errors.getErrors().size());
        assertEquals(NameResolver.Result.ERROR, resolver.resolve(errors));
        assertEquals(resolved, resolver.getResolvedNames());
        assertEquals(1, errors.getErrors().size());
    }

    @Test
    void resumingAClonedTypeCreatesAResolverOwnedByTheClone() {
        var owner = new ResolvingScope();
        var original = owner.type("Outer", "Inner");
        var resolver = original.getNameResolver();
        var errors = new ErrorList();
        assertEquals(NameResolver.Result.DEFERRED, resolver.resolve(errors));
        var copy = original.copyTree();
        var copiedResolver = copy.getNameResolver();
        assertNotSame(resolver, copiedResolver);
        assertSame(copy, copiedResolver.getNode());
        assertSame(original, resolver.getNode());
        owner.ready = true;
        assertEquals(NameResolver.Result.RESOLVED, copiedResolver.resolve(errors));
        assertEquals(2, copiedResolver.getResolvedNames().size());
        assertTrue(resolver.getResolvedNames().isEmpty());
        assertTrue(original.getNameBindings().stream().allMatch(binding -> binding.target() == null));
        assertEquals(NameResolver.Result.RESOLVED, resolver.resolve(errors));
        assertEquals(resolver.getResolvedNames(), copiedResolver.getResolvedNames());
        assertTrue(errors.getErrors().isEmpty());
    }

    @Test
    void parameterClonesRetainKnownTargetsButRebindingDoesNotRewriteTheOriginalSlot() {
        var pool = new FileStructure("Provenance").getConstantPool();
        var type = pool.getFileStructure().getModule().getIdentityConstant().getType();
        var parameter = new Parameter(null, new Token(0, 5, Id.IDENTIFIER, "value"));
        assertNull(parameter.getResolvedTarget());
        var original = new Register(type, "value", 0);
        parameter.setResolvedTarget(original);
        for (Parameter copy : List.of(parameter.copyTree(), (Parameter) parameter.clone())) {
            // Tree copies retain known bindings; this is not a fresh parse.
            assertSame(original, copy.getResolvedTarget());
            var replacement = new Register(type, "value", 1);
            copy.setResolvedTarget(replacement);
            assertSame(replacement, copy.getResolvedTarget());
            assertSame(original, parameter.getResolvedTarget());
        }
    }

    private static class ResolvingScope extends AstNode {
        private final FileStructure file = new FileStructure("Provenance");
        private final Source source = new Source("Outer.Inner.Missing");
        private boolean ready;

        ResolvingScope() {
            file.getModule().createClass(Access.PUBLIC, Component.Format.CLASS, "Outer", null)
                    .createClass(Access.PUBLIC, Component.Format.CLASS, "Inner", null);
        }

        NamedTypeExpression type(String... names) {
            var tokens = IntStream.range(0, names.length).mapToObj(index -> {
                int start = IntStream.range(0, index).map(previous -> names[previous].length() + 1).sum();
                return new Token(start, start + names[index].length(), Id.IDENTIFIER, names[index]);
            }).toList();
            return adopt(new NamedTypeExpression(null, tokens, null, null, null, tokens.getLast().getEndPosition()));
        }

        @Override
        public boolean isComponentNode() { return true; }
        @Override
        protected boolean canResolveNames() { return ready; }
        @Override
        public ComponentResolver getComponentResolver() { return file.getModule(); }
        @Override
        public ConstantPool pool() { return file.getConstantPool(); }
        @Override
        public Source getSource() { return source; }
        @Override
        public long getStartPosition() { return 0; }
        @Override
        public long getEndPosition() { return source.toRawString().length(); }
        @Override
        public String toString() { return "declaration provenance test scope"; }
    }
}
