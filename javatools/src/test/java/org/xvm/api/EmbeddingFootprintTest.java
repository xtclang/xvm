package org.xvm.api;

import org.junit.jupiter.api.Test;

import org.xvm.asm.FileStructure;
import org.xvm.compiler.BuildRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmbeddingFootprintTest {
    @Test
    public void snapshotDoesNotBootstrapTheRuntime() {
        // An empty repository cannot start an interpreter. Measuring it must still work.
        var support = new EmbeddingSupport().configure(new BuildRepository(), null);
        var footprint = support.footprint();
        assertEquals(0, footprint.modules());
        assertEquals(0, footprint.constants());
        assertEquals(0, footprint.invalidations());
        assertTrue(footprint.heapBytes() > 0);
    }

    @Test
    public void snapshotMeasuresTheSuppliedCompilation() {
        var support = new EmbeddingSupport();
        var file = new FileStructure("Measured");
        var compilation = EmbeddingSupport.Compilation.forFile(file);
        file.getConstantPool().ensureStringConstant("from this compilation");

        var footprint = support.footprint(compilation);
        assertEquals(file.getConstantPool().size(), footprint.constants());
        assertEquals(file.getConstantPool().getInvalidationCount(), footprint.invalidations());
        assertEquals(0, support.footprint().constants());
    }
}
