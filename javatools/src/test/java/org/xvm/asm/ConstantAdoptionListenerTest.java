package org.xvm.asm;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * The diagnostics design leans on one non-obvious invariant, so it is worth a test of its own.
 *
 * <p>{@code TypeConstant.ensureTypeInfo()} resolves its listener from the pool that owns the
 * constant. That is only correct because {@link ConstantPool#register} ADOPTS a foreign constant
 * into the registering pool, which makes "who owns it" and "who is asking" the same pool for
 * everything a compile actually touches.
 *
 * <p>If that ever stopped being true, parallel compiles would start reporting into each other's
 * listener - or into none - and nothing else in the codebase would fail first. Hence this.
 */
public class ConstantAdoptionListenerTest {
    @Test
    public void registeringAForeignConstantAdoptsItIntoTheRegisteringPool() {
        var library  = new FileStructure("library");
        var compiling = new FileStructure("compiling");

        ErrorListener libraryListener   = err -> false;
        ErrorListener compilingListener = err -> false;
        library.getConstantPool().setErrorListener(libraryListener);
        compiling.getConstantPool().setErrorListener(compilingListener);

        // a constant that originates in the library's pool
        Constant fromLibrary = library.getConstantPool().ensureStringConstant("shared");
        assertSame(library.getConstantPool(), fromLibrary.getConstantPool(),
                "it starts out owned by the pool that created it");

        // the compiling pool registers it - this is what a compile referencing a library type does
        Constant adopted = compiling.getConstantPool().register(fromLibrary);

        assertSame(compiling.getConstantPool(), adopted.getConstantPool(),
                "registering adopts it: the compiling pool now owns it");
        assertSame(compilingListener, adopted.getConstantPool().getErrorListener(),
                "so its diagnostics resolve to the COMPILE's listener, not the library's");
        assertNotSame(libraryListener, adopted.getConstantPool().getErrorListener(),
                "which is what keeps two parallel compiles from sharing a sink");
    }

    @Test
    public void aPoolNobodyConfiguredAnswersRuntime() {
        var file = new FileStructure("unconfigured");

        assertSame(ErrorListener.RUNTIME, file.getConstantPool().getErrorListener(),
                "the default is the runtime listener, not null and not a silent one");
    }
}
