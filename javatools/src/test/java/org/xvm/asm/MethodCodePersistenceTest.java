package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import java.util.Arrays;

import org.xvm.asm.op.Return_0;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Verifies the method record's legacy metadata slot without depending on the removed metadata
 * implementation. Existing files can contain a payload; new files retain an empty slot.
 */
public class MethodCodePersistenceTest {
    @Test
    public void legacyPayloadDoesNotConsumeFollowingRecordData()
            throws IOException {
        byte[] body = methodBody(new byte[] {12, 34, 56, 78});
        for (int skipLimit : new int[] {0, 1, Integer.MAX_VALUE}) {
            var method = createMethod();
            var bytes = new ByteArrayInputStream(body) {
                @Override
                public long skip(long count) {
                    return super.skip(Math.min(count, skipLimit));
                }
            };
            var in = new DataInputStream(bytes);

            method.disassemble(in);

            assertEquals(0, in.available());
            assertEquals(1, method.getOps().length);
            assertInstanceOf(Return_0.class, method.getOps()[0]);
        }
    }

    @Test
    public void truncatedLegacyPayloadIsRejected()
            throws IOException {
        byte[] body = methodBody(new byte[] {12, 34, 56, 78});
        byte[] truncated = Arrays.copyOf(body, body.length - 3);
        var method = createMethod();

        assertThrows(EOFException.class, () -> method.disassemble(
                new DataInputStream(new ByteArrayInputStream(truncated))));
    }

    @Test
    public void rewritingLegacyMethodKeepsCodeAndEmitsEmptyMetadataSlot()
            throws IOException {
        var method = createMethod();
        method.disassemble(new DataInputStream(new ByteArrayInputStream(
                methodBody(new byte[] {12, 34, 56, 78}))));
        method.registerConstants(method.getConstantPool());

        var bytes = new ByteArrayOutputStream();
        method.assemble(new DataOutputStream(bytes));
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.readUnsignedShort(); // component flags precede the method body
        readMagnitude(in);     // method identity

        assertArrayEquals(methodBody(new byte[0]), in.readAllBytes());
    }

    /**
     * Encode the existing on-disk body of a parameterless method with one return instruction.
     * The deliberately opaque payload is never interpreted by the compiler or runtime.
     */
    private static byte[] methodBody(byte[] payload)
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
        writePackedLong(out, payload.length);
        out.write(payload);
        writePackedLong(out, 0);  // source line count
        return bytes.toByteArray();
    }

    private static MethodStructure createMethod() {
        var file = new FileStructure("MethodCodePersistence");
        var clz = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Sample", null);
        return clz.createMethod(true, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "run", Parameter.NO_PARAMS, true, false);
    }
}
