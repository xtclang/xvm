package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.xvm.asm.op.Return_0;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Verifies that method records contain executable code followed immediately by source information.
 * Round trips exercise both assembly paths; obsolete file versions fail at the header boundary.
 */
public class MethodCodePersistenceTest {
    @Test
    public void readingMethodStopsAfterSourceInformation()
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        bytes.write(methodBody());
        bytes.write(123); // the next record must remain unread
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        var method = createMethod(new FileStructure("MethodCodePersistence"));

        method.disassemble(in);

        assertEquals(1, method.getOps().length);
        assertInstanceOf(Return_0.class, method.getOps()[0]);
        assertEquals(123, in.readUnsignedByte());
        assertEquals(0, in.available());
    }

    @Test
    public void writingMethodEmitsOnlyCodeAndSourceInformation()
            throws IOException {
        var method = createMethod(new FileStructure("MethodCodePersistence"));
        method.disassemble(new DataInputStream(new ByteArrayInputStream(methodBody())));
        method.registerConstants(method.getConstantPool());

        var bytes = new ByteArrayOutputStream();
        method.assemble(new DataOutputStream(bytes));
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.readUnsignedShort(); // component flags precede the method body
        readMagnitude(in);     // method identity

        assertArrayEquals(methodBody(), in.readAllBytes());
    }

    @Test
    public void sourceAndCodeSurviveNormalAndForcedAssembly()
            throws IOException {
        for (boolean force : new boolean[] {false, true}) {
            var file = new FileStructure("MethodCodePersistence");
            var method = createMethod(file);
            method.createCode().add(new Return_0());
            method.configureSource("void run() {\n    return;\n}", 7);
            if (force) {
                method.forceAssembly(file.getConstantPool());
            }

            var bytes = new ByteArrayOutputStream();
            file.writeTo(bytes);
            var reread = new FileStructure(new ByteArrayInputStream(bytes.toByteArray()));
            var clz = (ClassStructure) reread.getModule().getChild("Sample");
            var methods = (MultiMethodStructure) clz.getChild("run");
            var restored = methods.methods().iterator().next();

            assertEquals(1, restored.getOps().length);
            assertInstanceOf(Return_0.class, restored.getOps()[0]);
            assertArrayEquals(new String[] {"void run() {", "    return;", "}"},
                    restored.getSourceLines(7, 3, false));
        }
    }

    @Test
    public void obsoleteFileVersionIsRejectedBeforeReadingRecords()
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(Constants.FILE_MAGIC);
        out.writeInt(0);
        out.writeInt(20260818);

        var error = assertThrows(IOException.class,
                () -> new FileStructure(new ByteArrayInputStream(bytes.toByteArray())));
        assertTrue(error.getMessage().startsWith("Unsupported .xtc version: 0.20260818"));
        assertTrue(error.getMessage().contains("supported version is " + Constants.VERSION_MAJOR_CUR
                + "." + Constants.VERSION_MINOR_CUR));
    }

    /** Encode a parameterless method containing one return instruction and no source lines. */
    private static byte[] methodBody()
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        writePackedLong(out, 0);  // contributions
        writePackedLong(out, 0);  // annotations
        writePackedLong(out, -1); // finally method
        writePackedLong(out, 0);  // type parameters
        writePackedLong(out, 0);  // default parameters
        writePackedLong(out, -1); // super method
        writePackedLong(out, 0);  // local constants
        writePackedLong(out, 2);  // encoded instruction stream length
        writePackedLong(out, 1);  // instruction count
        out.writeByte(Op.OP_RETURN_0);
        writePackedLong(out, 0);  // source line count
        return bytes.toByteArray();
    }

    private static MethodStructure createMethod(FileStructure file) {
        var clz = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Sample", null);
        return clz.createMethod(true, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "run", Parameter.NO_PARAMS, true, false);
    }
}
