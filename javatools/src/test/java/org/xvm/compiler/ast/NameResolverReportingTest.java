package org.xvm.compiler.ast;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ComponentResolver;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A resolver borrows its caller's listener only while making resolution callbacks. */
class NameResolverReportingTest {
    @Test
    void callbackReportsToCallerAndReleasesIt() {
        var errors = new ErrorList();
        var resolver = new NameResolver(new ResolvingNode((name, access, collector) -> {
            assertSame(errors, collector.getErrorListener());
            collector.getErrorListener().error("TEST", ErrorListener.NOWHERE);
            return ResolutionResult.ERROR;
        }), "missing");
        assertNull(resolver.getErrorListener());
        assertEquals(NameResolver.Result.ERROR, resolver.resolve(errors));
        assertEquals(1, errors.getErrors().size());
        assertNull(resolver.getErrorListener());
    }

    @Test
    void exceptionalCallbackRestoresInactiveStateAndCanBeRetriedWithAnotherListener() {
        var first = new ErrorList();
        var second = new ErrorList();
        var failure = new IllegalStateException("callback failed");
        var resolver = new NameResolver(new ResolvingNode((name, access, collector) -> {
            var errors = collector.getErrorListener();
            errors.warn("TEST", ErrorListener.NOWHERE);
            if (errors == first) {
                throw failure;
            }
            assertSame(second, errors);
            return ResolutionResult.ERROR;
        }), "missing");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> resolver.resolve(first)));
        assertNull(resolver.getErrorListener());
        assertEquals(NameResolver.Result.ERROR, resolver.resolve(second));
        assertNull(resolver.getErrorListener());
        assertEquals(1, first.getErrors().size());
        assertEquals(1, second.getErrors().size());
    }

    @Test
    void deferredResolutionAndRejectedNullDoNotRetainAListener() {
        var resolver = new NameResolver(new ResolvingNode(null), "later");
        assertEquals(NameResolver.Result.DEFERRED, resolver.resolve(new ErrorList()));
        assertNull(resolver.getErrorListener());
        assertThrows(NullPointerException.class, () -> resolver.resolve(null));
        assertNull(resolver.getErrorListener());
    }

    @Test
    void nestedResolutionRestoresTheOuterCallbacksListener() {
        var outer = new ErrorList();
        var inner = new ErrorList();
        var resolver = new NameResolver(new ResolvingNode((name, access, collector) -> {
            if (collector.getErrorListener() == outer) {
                assertEquals(NameResolver.Result.ERROR, ((NameResolver) collector).resolve(inner));
                assertSame(outer, collector.getErrorListener());
            } else {
                assertSame(inner, collector.getErrorListener());
            }
            collector.getErrorListener().error("TEST", ErrorListener.NOWHERE);
            return ResolutionResult.ERROR;
        }), "missing");
        assertEquals(NameResolver.Result.ERROR, resolver.resolve(outer));
        assertNull(resolver.getErrorListener());
        assertEquals(1, outer.getErrors().size());
        assertEquals(1, inner.getErrors().size());
    }

    private static class ResolvingNode extends AstNode {
        ResolvingNode(ComponentResolver resolver) {
            f_resolver = resolver;
        }

        @Override
        public boolean isComponentNode() { return true; }
        @Override
        protected boolean canResolveNames() { return true; }
        @Override
        public ComponentResolver getComponentResolver() { return f_resolver; }
        @Override
        public long getStartPosition() { return 0; }
        @Override
        public long getEndPosition() { return 1; }
        @Override
        public String toString() { return "test component"; }

        private final ComponentResolver f_resolver;
    }
}
