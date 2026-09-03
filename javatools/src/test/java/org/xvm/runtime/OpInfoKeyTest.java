package org.xvm.runtime;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Nop;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The per-op info cache answers with the type its key was declared with, or not at all.
 *
 * <p>The cache holds several unrelated kinds of value against one op and used to return
 * {@code Object}, so every caller cast on the strength of the category's name. Nothing checked
 * that the name matched what was stored: writing a constructor under {@code TargetClass} and
 * reading it as an {@code IdentityConstant} compiled, and failed later as a
 * {@link ClassCastException} on a hot path in whichever service reused the entry.</p>
 *
 * <p>Most of that is now a compile error, which no test can observe. What a test can observe is
 * the boundary underneath: a read through a key whose type does not match what is stored throws
 * where the mistake is, rather than handing back a reference of the wrong type.</p>
 */
public class OpInfoKeyTest {
    private enum Probe {Alpha, Beta}

    @Test
    public void aValueComesBackAsTheTypeItsKeyDeclares() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            ServiceContext context = contextOf(runtime);
            var            op      = new Nop();

            var keyType = OpInfoKey.of(Probe.Alpha, TypeConstant.class);
            assertNull(context.getOpInfo(op, keyType), "nothing has been cached yet");

            TypeConstant type = context.f_container.getConstantPool().typeObject();
            context.setOpInfo(op, keyType, type);
            assertSame(type, context.getOpInfo(op, keyType),
                    "a cached value must come back from its own key");
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void readingThroughAKeyOfAnotherTypeThrowsAtTheCache() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            ServiceContext context = contextOf(runtime);
            var            op      = new Nop();

            // two keys naming the SAME category with different types - the shape the old
            // Object-valued cache could not tell apart
            var keyType     = OpInfoKey.of(Probe.Alpha, TypeConstant.class);
            var keyIdentity = OpInfoKey.of(Probe.Alpha, IdentityConstant.class);
            var keyMethod   = OpInfoKey.of(Probe.Alpha, MethodStructure.class);

            context.setOpInfo(op, keyType, context.f_container.getConstantPool().typeObject());

            assertThrows(ClassCastException.class, () -> context.getOpInfo(op, keyIdentity),
                    "a TypeConstant must not be served through an IdentityConstant key");
            assertThrows(ClassCastException.class, () -> context.getOpInfo(op, keyMethod),
                    "a TypeConstant must not be served through a MethodStructure key");
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static ServiceContext contextOf(Runtime runtime) {
        var container = NativeContainer.create(runtime, XdkOutputs.systemRepository(),
                ErrorListener.RUNTIME);
        return container.ensureServiceContext();
    }
}
