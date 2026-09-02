package org.xvm.asm.constants;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;

import org.xvm.test.XdkOutputs;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.Token.Id;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The first unit tests the compile-time constant folding in {@code StringConstant.apply} and
 * {@code CharConstant.apply} ever had. The old implementations dispatched on
 * {@code op.TEXT + that.getFormat().name()} - string concatenation of the operator text and the
 * operand's format name - and blind-cast the operand inside every arm; the only verification
 * they ever received was whole-compiler runs. The rewrite dispatches on the operator enum and
 * pattern-matches the operand over the sealed value-constant tree; these tests pin the folding
 * behavior directly, including the fallback contract for operand kinds no arm claims.
 */
public class ValueConstantFoldingTest {
    @Test
    public void stringFolding() {
        var pool  = new FileStructure("test").getConstantPool();
        var hello = pool.ensureStringConstant("hello");

        assertSame(pool.ensureStringConstant("hello world"),
                hello.apply(Id.ADD, pool.ensureStringConstant(" world")));
        assertSame(pool.ensureStringConstant("hello!"),
                hello.apply(Id.ADD, pool.ensureCharConstant('!')));
        assertSame(pool.ensureStringConstant("hello42"),
                hello.apply(Id.ADD, pool.ensureLiteralConstant(Format.IntLiteral, "42")));

        assertSame(pool.ensureStringConstant("hellohello"),
                hello.apply(Id.MUL, pool.ensureLiteralConstant(Format.IntLiteral, "2")));
        assertSame(pool.ensureStringConstant("hellohellohello"),
                hello.apply(Id.MUL, pool.ensureIntConstant(3)));

        var range = hello.apply(Id.I_RANGE_I, pool.ensureStringConstant("hellp"));
        assertInstanceOf(RangeConstant.class, range);
        assertSame(hello, ((RangeConstant) range).getFirst());
    }

    @Test
    public void charFolding() {
        var pool = new FileStructure("test").getConstantPool();
        var a    = pool.ensureCharConstant('a');
        var b    = pool.ensureCharConstant('b');

        assertSame(pool.ensureStringConstant("ab"), a.apply(Id.ADD, b));
        assertSame(pool.ensureStringConstant("ahello"),
                a.apply(Id.ADD, pool.ensureStringConstant("hello")));
        assertEquals(1, ((IntConstant) b.apply(Id.SUB, a)).getValue().getInt());
        assertSame(pool.ensureStringConstant("aaa"),
                a.apply(Id.MUL, pool.ensureLiteralConstant(Format.IntLiteral, "3")));

        // the "fake" compile-time-only char +/- int-literal arithmetic used by range iteration
        assertSame(b, a.apply(Id.ADD, pool.ensureLiteralConstant(Format.IntLiteral, "1")));
        assertSame(a, b.apply(Id.SUB, pool.ensureLiteralConstant(Format.IntLiteral, "1")));
    }

    /**
     * Comparison folding produces Boolean/Ordered enum constants, which need the loaded
     * ecstasy module, so this part runs against a booted native container's pool.
     */
    @Test
    public void comparisonFolding() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var pool = NativeContainer.create(runtime, XdkOutputs.systemRepository(), ErrorListener.RUNTIME).getConstantPool();
            var abc  = pool.ensureStringConstant("abc");
            var abd  = pool.ensureStringConstant("abd");

            assertSame(pool.valOf(true),  abc.apply(Id.COMP_LT,   abd));
            assertSame(pool.valOf(false), abc.apply(Id.COMP_GT,   abd));
            assertSame(pool.valOf(true),  abc.apply(Id.COMP_LTEQ, abc));
            assertSame(pool.valOf(true),  abc.apply(Id.COMP_GTEQ, abc));
            assertSame(pool.valOf(true),
                    abc.apply(Id.COMP_EQ, pool.ensureStringConstant("abc")));
            assertSame(pool.valOf(true),  abc.apply(Id.COMP_NEQ,  abd));

            var a = pool.ensureCharConstant('a');
            var b = pool.ensureCharConstant('b');
            assertSame(pool.valOf(true),  a.apply(Id.COMP_LT, b));
            assertSame(pool.valOf(false), a.apply(Id.COMP_EQ, b));
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The fallback contract: an operand kind no arm claims still routes to the base
     * implementation, which refuses loudly. On the old shape this was whatever string failed
     * to match; now it is the nested switches' explicit default arms.
     */
    @Test
    public void unclaimedOperandsStillRefuseLoudly() {
        var pool  = new FileStructure("test").getConstantPool();
        var hello = pool.ensureStringConstant("hello");

        assertThrows(UnsupportedOperationException.class,
                () -> hello.apply(Id.SUB, pool.ensureStringConstant("x")),
                "subtraction is not a string operation");
        assertThrows(UnsupportedOperationException.class,
                () -> hello.apply(Id.ADD, pool.ensureIntConstant(7)),
                "string + integer constant has no folding");
        assertThrows(UnsupportedOperationException.class,
                () -> pool.ensureCharConstant('a').apply(Id.MUL, pool.ensureCharConstant('b')),
                "char * char has no folding");
    }

    // ----- helpers (same discovery as ArrayViewGuardTest) ---------------------------------------





}
