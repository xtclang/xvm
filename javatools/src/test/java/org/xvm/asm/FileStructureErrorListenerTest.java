package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.Site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.xvm.util.Severity.WARNING;

/** Structure diagnostics belong to the calling operation, independently of ambient pool state. */
public class FileStructureErrorListenerTest {
    @Test
    public void reportingUsesTheSuppliedListenerWithoutAnAmbientPool() {
        var file = new FileStructure("test");
        var errors = new ErrorList();
        try (var scope = ConstantPool.withPool(null)) {
            file.log(errors, WARNING, "VERIFY-75", "x", "Atomic");
        }
        assertEquals(1, errors.getErrors().size());
        assertSame(file, ((Site.At) errors.getErrors().getFirst().site()).xs());
    }

    @Test
    public void reusingAStructureDoesNotReuseThePreviousRequestsListener() {
        var file = new FileStructure("test");
        var first = new ErrorList();
        var second = new ErrorList();
        file.log(first, WARNING, "VERIFY-75", "first", "Atomic");
        file.log(second, WARNING, "VERIFY-75", "second", "Atomic");
        assertEquals(1, first.getErrors().size());
        assertEquals(1, second.getErrors().size());
        assertEquals("first", first.getErrors().getFirst().getParams()[0]);
        assertEquals("second", second.getErrors().getFirst().getParams()[0]);
    }
}
