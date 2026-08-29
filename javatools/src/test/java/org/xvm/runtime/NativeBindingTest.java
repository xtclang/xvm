package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.runtime.template.text.xString.StringHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Covers the typed native binding in isolation: the conversion each arity performs before calling
 * a handler, without needing a running container.
 *
 * <p>The point of the binding is that a native method body is typed by the compiler rather than by
 * a hand-written cast, and that the one remaining conversion is <b>checked</b>. These tests pin
 * both halves: the handler receives the declared handle types, and a handle that does not match
 * the declaration fails immediately, at the binding, naming the mismatch - rather than reaching a
 * handler body and failing somewhere less obvious.</p>
 *
 * <p>They exist because the integration path is only exercised for the arities that some Ecstasy
 * code happens to call. {@code invokeNativeN}'s zero-argument form is bound the same way as the
 * others, but the only caller of the one native using it - {@code OSStorage.instance()} - is the
 * runtime compiler's unknown-implementation branch, so no simple probe reaches it.</p>
 */
public class NativeBindingTest {
    /**
     * A stand-in handle. {@link ObjectHandle} is abstract and its real subclasses need a
     * TypeComposition, so these tests use a minimal concrete handle of their own; the binding only
     * ever calls {@link Class#cast} on it.
     */
    private static class TestHandle
            extends ObjectHandle {
        protected TestHandle() {
            super(null);
        }
    }

    /** A second handle type, to prove a mismatch is actually detected rather than waved through. */
    private static class OtherHandle
            extends ObjectHandle {
        protected OtherHandle() {
            super(null);
        }
    }

    private static final NativeType<TestHandle>  SELF  =
            NativeType.of("test.Self", TestHandle.class);
    private static final NativeType<OtherHandle> OTHER =
            NativeType.of("test.Other", OtherHandle.class);

    @Test
    public void aTypeCarriesBothItsEcstasyNameAndItsHandleClass() {
        assertEquals("test.Self", SELF.typeName());
        assertSame(TestHandle.class, SELF.handleClass());
        assertEquals(1, SELF.asParamTypes().length);
        assertEquals("test.Self", SELF.asParamTypes()[0]);
    }

    @Test
    public void namesRendersSeveralTypesInDeclarationOrder() {
        String[] asName = NativeType.names(SELF, OTHER);

        assertEquals(2, asName.length);
        assertEquals("test.Self", asName[0]);
        assertEquals("test.Other", asName[1]);
    }

    /** The zero-argument arity, whose integration path no probe program reaches. */
    @Test
    public void zeroArityPassesTheReceiverTyped() {
        var hThis = new TestHandle();
        var seen  = new TestHandle[1];

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, (frame, hTarget, iReturn) -> {
            seen[0] = hTarget;                       // typed: no cast in this body
            return iReturn;
        });

        assertEquals(42, bound.dispatch(null, hThis, new ObjectHandle[0], 42));
        assertSame(hThis, seen[0]);
    }

    /**
     * A zero-argument native may be called with a null receiver - {@code OSStorage.instance()} is,
     * being static - and {@link Class#cast} passes null through, so that must keep working.
     */
    @Test
    public void zeroArityToleratesANullReceiver() {
        var seen = new TestHandle[] {new TestHandle()};

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, (frame, hTarget, iReturn) -> {
            seen[0] = hTarget;
            return iReturn;
        });

        assertEquals(7, bound.dispatch(null, null, new ObjectHandle[0], 7));
        assertNull(seen[0], "a null receiver must reach the handler as null, not throw");
    }

    @Test
    public void twoArityPassesBothArgumentsTyped() {
        var hThis = new TestHandle();
        var hOne  = new TestHandle();
        var hTwo  = new OtherHandle();
        var seen  = new ObjectHandle[3];

        NativeMethod.BoundN bound = NativeMethod.bind(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, iReturn) -> {
                    seen[0] = hTarget;
                    seen[1] = hArg1;
                    seen[2] = hArg2;                 // all three typed, no casts
                    return iReturn;
                });

        bound.dispatch(null, hThis, new ObjectHandle[] {hOne, hTwo}, 1);

        assertSame(hThis, seen[0]);
        assertSame(hOne,  seen[1]);
        assertSame(hTwo,  seen[2]);
    }

    @Test
    public void multiReturnArityPassesArgumentsTypedAndTheReturnRegisters() {
        var    hThis     = new TestHandle();
        var    hOther    = new OtherHandle();
        int[]  aiReturn  = {3, 4};
        var    seenRegs  = new int[1][];

        NativeMethod.BoundNN bound = NativeMethod.bindNN(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, aiRet) -> {
                    seenRegs[0] = aiRet;
                    return 0;
                });

        bound.dispatch(null, hThis, new ObjectHandle[] {new TestHandle(), hOther}, aiReturn);

        assertSame(aiReturn, seenRegs[0]);
    }

    /**
     * The conversion is checked. A handle that does not match the declared type fails at the
     * binding, and the failure names the offending class - which is the whole reason for doing it
     * once here rather than as an unchecked cast in each of the 744 native bodies.
     */
    @Test
    public void aMismatchedArgumentFailsAtTheBindingAndNamesTheType() {
        NativeMethod.BoundN bound = NativeMethod.bind(SELF, SELF, OTHER,
                (frame, hTarget, hArg1, hArg2, iReturn) ->
                        fail_shouldNotReachHandler());

        var e = assertThrows(ClassCastException.class,
                () -> bound.dispatch(null, new TestHandle(), 
                        new ObjectHandle[] {new TestHandle(), new TestHandle()}, 0),
                "the second argument is declared test.Other but a TestHandle was passed");

        assertTrue(e.getMessage().contains("OtherHandle"),
                () -> "the failure must name the declared type, but was: " + e.getMessage());
    }

    @Test
    public void aMismatchedReceiverFailsAtTheBinding() {
        NativeMethod.BoundN bound = NativeMethod.bind(SELF,
                (frame, hTarget, iReturn) -> fail_shouldNotReachHandler());

        assertThrows(ClassCastException.class,
                () -> bound.dispatch(null, new OtherHandle(), new ObjectHandle[0], 0),
                "the receiver is declared test.Self but an OtherHandle was passed");
    }

    private static int fail_shouldNotReachHandler() {
        throw new AssertionError("the handler must not be reached when a handle mismatches");
    }
}
