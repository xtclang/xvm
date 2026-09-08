package org.xvm.asm;


import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * The diagnostics design leans on one non-obvious invariant, so it is worth a test of its own.
 *
 * <p>Work reaches a listener by OWNERSHIP: a constant's diagnostics go to the pool that owns it.
 * That is only correct because {@link ConstantPool#register} ADOPTS a foreign constant into the
 * registering pool, which makes "who owns it" and "who is asking" the same pool for everything a
 * compile actually touches.
 *
 * <p>The consumer of this invariant has changed and the invariant has not. It used to be a
 * no-argument {@code ensureTypeInfo()} that resolved the pool's listener implicitly; that overload
 * is deleted (E35 step E). What still rests on it are the sites that log through the owning pool
 * directly - {@code log(pool.getErrorListener(), ...)} in {@code calculateRelation},
 * {@code resolveTypedefs}, {@code chooseBest} and their neighbours. Those are soft asserts about
 * the pool's own contents, so the owning pool is the right sink; adoption is what makes "the owning
 * pool" and "the asking compile" the same pool.
 *
 * <p>If that ever stopped being true, parallel compiles would start reporting into each other's
 * listener - or into none - and nothing else in the codebase would fail first. Hence this.
 */
public class ConstantAdoptionListenerTest {
    @Test
    public void registeringAForeignConstantAdoptsItIntoTheRegisteringPool() {
        ErrorListener libraryListener   = err -> {};
        ErrorListener compilingListener = err -> {};

        var library   = new FileStructure("library", libraryListener);
        var compiling = new FileStructure("compiling", compilingListener);

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

    /**
     * The invariant pinned for the case that actually matters: a TYPE, not a leaf constant.
     *
     * <p>The sibling test above uses a {@code StringConstant}, which never reaches the gate that
     * decides this and never has a {@code TypeInfo} either. For a {@code TypeConstant},
     * {@code register} adopts CONDITIONALLY:
     *
     * <pre>{@code
     * if (constant instanceof TypeConstant type && !type.isShared(this)) {
     *     return constant;                       // unchanged: still the other pool's
     * }
     * }</pre>
     *
     * <p>and {@code isShared} bottoms out in {@code IdentityConstant.isShared}, which asks whether
     * the OTHER pool's file structure has a child for this type's module. That child is the
     * fingerprint a compiling module declares for every module it depends on - so a library type is
     * adopted exactly when it is NAMEABLE, and a compile can only name types from modules it
     * depends on. The guarantee is therefore not "adoption always happens"; it is "adoption happens
     * wherever a compile could have referred to the type", which is what makes the overload safe.
     */
    @Test
    public void aLibraryTypeIsAdoptedPreciselyWhenTheCompileCanNameIt() {
        ErrorListener libraryListener   = err -> {};
        ErrorListener compilingListener = err -> {};

        var library   = new FileStructure("library", libraryListener);
        var compiling = new FileStructure("compiling", compilingListener);

        ConstantPool poolLibrary   = library.getConstantPool();
        ConstantPool poolCompiling = compiling.getConstantPool();

        TypeConstant typeLibrary = library.getModule().getIdentityConstant().getType();
        assertSame(poolLibrary, typeLibrary.getConstantPool(),
                "it starts out owned by the pool that created it");

        // BEFORE the dependency is declared, the compile could not name this type at all
        assertFalse(typeLibrary.isShared(poolCompiling),
                "a module the compile does not depend on is not shared with it");

        // declaring the dependency is what TypeCompositionStatement does for every imported module
        compiling.ensureModule("library").fingerprintRequired();

        assertTrue(typeLibrary.isShared(poolCompiling),
                "the fingerprint is what makes the library's types nameable - and shared");

        TypeConstant adopted = (TypeConstant) poolCompiling.register(typeLibrary);

        assertSame(poolCompiling, adopted.getConstantPool(),
                "so registering adopts it into the compiling pool");
        assertSame(compilingListener, adopted.getConstantPool().getErrorListener(),
                "which is why typeInfo() resolves the COMPILE's listener for a library type");
        assertNotSame(libraryListener, adopted.getConstantPool().getErrorListener(),
                "and why two parallel compiles do not report into each other's sink");
    }

    @Test
    public void aPoolNobodyConfiguredAnswersRuntime() {
        var file = new FileStructure("unconfigured");

        assertSame(ErrorListener.RUNTIME, file.getConstantPool().getErrorListener(),
                "the default is the runtime listener, not null and not a silent one");
    }
}
