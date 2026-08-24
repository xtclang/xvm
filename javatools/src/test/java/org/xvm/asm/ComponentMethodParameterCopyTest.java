package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the short-hand property method creation path (must-fix row 152, graduated from the
 * array element exposure audit). {@code MethodDeclarationStatement} builds a short-hand property
 * override from {@code methodSuper.getReturn(i)} {@code Parameter} objects that are usually owned
 * by the loaded ecstasy/library {@code FileStructure}. Creating the new method through the
 * aliasing {@code createMethod} shares those elements, and {@code Parameter.registerConstants}
 * then rewrites the shared element's constants into the user module's pool at assembly - a
 * single-threaded-reachable corruption of the library module. The fix routes the path through
 * {@link Component#createMethodCopyingParameters}, mirroring the delegated-method factory fix
 * (see {@code AsmConstructorEscapeTest.delegatedMethodFactoryCopiesParameterElementsForNewOwner}).
 */
public class ComponentMethodParameterCopyTest {
    /**
     * Borrowed parameter elements must be copied for the new owner, leaving the library module's
     * elements attached to - and only to - the library method.
     */
    @Test
    public void componentFactoryCopiesBorrowedParameterElements() {
        var fileLib   = new FileStructure("lib");
        var poolLib   = fileLib.getConstantPool();
        var clzLib    = fileLib.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Super", null);
        var returnLib = new Parameter(poolLib, poolLib.typeString(), "result", null, true, 0, false);
        var methodLib = clzLib.createMethod(false, Constants.Access.PUBLIC, null,
                new Parameter[] {returnLib}, "name", Parameter.NO_PARAMS, true, false);

        var fileUser = new FileStructure("user");
        var clzUser  = fileUser.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Sub", null);

        var methodUser = clzUser.createMethodCopyingParameters(false, Constants.Access.PUBLIC,
                null, new Parameter[] {methodLib.getReturn(0)}, "name", Parameter.NO_PARAMS,
                true, true);

        assertNotSame(methodLib.getReturn(0), methodUser.getReturn(0),
                "a short-hand override must not share the library method's Parameter elements");
        assertSame(methodUser, methodUser.getReturn(0).getContaining());
        assertSame(poolLib, returnLib.getContaining(),
                "the library method's element must remain parented in the library module");
        assertSame(returnLib, methodLib.getReturn(0));
    }

    /**
     * The mechanism the copy defuses: registering the new method's return into the user pool must
     * adopt constants for the copy only. With a shared element - master's shape at the short-hand
     * property site - this same registration rewrote the library element's constants into the
     * user module's pool.
     */
    @Test
    public void registeringTheCopyLeavesTheLibraryElementInItsOwnPool() {
        var fileLib   = new FileStructure("lib");
        var poolLib   = fileLib.getConstantPool();
        var clzLib    = fileLib.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Super", null);
        var returnLib = new Parameter(poolLib, poolLib.typeString(), "result", null, true, 0, false);
        var methodLib = clzLib.createMethod(false, Constants.Access.PUBLIC, null,
                new Parameter[] {returnLib}, "name", Parameter.NO_PARAMS, true, false);

        var fileUser = new FileStructure("user");
        var poolUser = fileUser.getConstantPool();
        var clzUser  = fileUser.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Sub", null);
        var methodUser = clzUser.createMethodCopyingParameters(false, Constants.Access.PUBLIC,
                null, new Parameter[] {methodLib.getReturn(0)}, "name", Parameter.NO_PARAMS,
                true, true);

        methodUser.getReturn(0).registerConstants(poolUser);

        assertSame(poolLib, returnLib.getType().getConstantPool(),
                "assembling the user module must not rewrite the library element's constants"
                        + " into the user pool");
        assertSame(poolUser, methodUser.getReturn(0).getType().getConstantPool());
    }

    /**
     * Pins the compiler call site. Red on master: the short-hand property block created the
     * override through the aliasing {@code createMethod}, sharing the super method's elements.
     */
    @Test
    public void shortHandPropertyPathUsesCopyingFactory() throws IOException {
        var source = Files.readString(
                sourceFor("org/xvm/compiler/ast/MethodDeclarationStatement.java"));

        assertTrue(source.contains("container.createMethodCopyingParameters("),
                "the short-hand property override must copy the super method's Parameter"
                        + " elements for the new owner");

        int ofShortHand = source.indexOf("methodSuper.getReturn(");
        assertTrue(ofShortHand >= 0);
        int ofCreate = source.indexOf("container.createMethod(", ofShortHand);
        int ofBlockEnd = source.indexOf("setComponent(method);", ofShortHand);
        assertFalse(ofCreate >= 0 && ofCreate < ofBlockEnd,
                "the short-hand property block must not fall back to the aliasing createMethod");
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static Path sourceFor(String relativePath) {
        var local = Path.of("src/main/java").resolve(relativePath);
        if (Files.isRegularFile(local)) {
            return local;
        }

        var dir = Path.of(".").toAbsolutePath().normalize();
        while (dir != null) {
            var candidate = dir.resolve("javatools/src/main/java").resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate main source file: " + relativePath);
    }
}
