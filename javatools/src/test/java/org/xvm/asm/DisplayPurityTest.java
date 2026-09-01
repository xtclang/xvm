package org.xvm.asm;


import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ast.BinaryAST;

import org.xvm.asm.constants.ParamInfo;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Gate for the purity of the ASM display methods - {@code toString()}, {@code getValueString()} and
 * {@code getDescription()}.
 *
 * <p>These methods are called IMPLICITLY: by string concatenation, by {@code Throwable.toString()}
 * while a stack trace is printed, and by an IDE debugger rendering a row in the Variables view.
 * A display method that grows the {@link ConstantPool}, writes a resolution back into a field, or
 * touches process-global state therefore makes OBSERVING the program CHANGE the program - a
 * breakpoint alters behaviour and the debugger session stops being trustworthy.</p>
 *
 * <p>Each test here runs against a COLD pool ({@code new FileStructure(...)}), because a warmed pool
 * hides interning: the canonical {@code Object}/{@code Function}/{@code Op} constants a display path
 * reaches for are already in a long-lived pool, so the growth only shows on a pool that has not yet
 * paid for them. That is not an artificial situation - it is what any freshly loaded module's pool
 * looks like while it is being compiled.</p>
 */
public class DisplayPurityTest {
    private static final String SOURCE = """
            void hello() {
                @Inject Console console;
                console.print("hello");
            }
            """;

    private static ConstantPool coldPool() {
        return new FileStructure("PurityTest").getConstantPool();
    }

    /**
     * {@code ParameterizedTypeConstant.getValueString()} used to ask
     * {@code m_constType.isA(pool.typeFunction())} and then {@code pool.extractFunctionParams(this)}
     * / {@code extractFunctionReturns(this)} in order to choose the pretty {@code function R(P)}
     * spelling. {@code typeFunction()} lazily interns the canonical {@code Function} type,
     * {@code isA()} writes the type's relation cache and can call {@code pool.register(this)}, and
     * each {@code extractFunction*} runs {@code isA()} again. Rendering one variable of a
     * parameterized type therefore grew the pool.
     */
    @Test
    public void renderingAParameterizedTypeDoesNotGrowThePool() {
        ConstantPool pool = coldPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            TypeConstant type = pool.ensureParameterizedTypeConstant(pool.typeList(), pool.typeInt64());

            int cBefore = pool.size();
            assertEquals("List<Int>", type.getValueString());
            assertEquals(cBefore, pool.size(),
                    "rendering List<Int> interned into the ConstantPool");

            assertPoolDetectsInterning(pool, cBefore);
        }
    }

    /**
     * The pretty function spelling must survive the fix: it is now derived structurally from the
     * constant's own fields instead of from {@code isA()}/{@code extractFunction*}, and the text is
     * byte-for-byte what it always was.
     */
    @Test
    public void functionTypesStillRenderTheirPrettySpelling() {
        ConstantPool pool = coldPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            TypeConstant typeInt = pool.typeInt64();
            TypeConstant typeStr = pool.typeString();

            record Case(String expected, TypeConstant type) {}
            var cases = new Case[] {
                new Case("function Int(String)",
                        pool.buildFunctionType(new TypeConstant[] {typeStr}, typeInt)),
                new Case("function void()",
                        pool.buildFunctionType(TypeConstant.NO_TYPES)),
                new Case("function void(Int, String)",
                        pool.buildFunctionType(new TypeConstant[] {typeInt, typeStr})),
                new Case("function (Int, String)(Int)",
                        pool.buildFunctionType(new TypeConstant[] {typeInt}, typeInt, typeStr)),
            };

            int cBefore = pool.size();
            for (Case c : cases) {
                assertEquals(c.expected(), c.type().getValueString());
            }
            assertEquals(cBefore, pool.size(), "rendering function types interned into the pool");
        }
    }

    /**
     * {@code ParamInfo.toString()} used to compare its constraint against
     * {@code typeConstraint.getConstantPool().typeObject()} - which lazily interns the canonical
     * {@code Object} type - and then call {@code isTuple()}, which on a terminal type interns
     * {@code clzTuple()}, forces the class structure to load, writes the resolved constant back, and
     * throws {@code IllegalStateException} when the structure is not there.
     */
    @Test
    public void renderingATypeParameterDoesNotGrowThePool() {
        ConstantPool pool = coldPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            var paramConstrained = new ParamInfo("Element", pool.typeInt64(), null);
            var paramUnbounded   = new ParamInfo("Element", pool.typeObject(),  null);

            int cBefore = pool.size();
            assertEquals("<Element extends Int>", paramConstrained.toString());
            assertEquals("<Element>",             paramUnbounded.toString());
            assertEquals(cBefore, pool.size(),
                    "rendering a type parameter interned into the ConstantPool");

            assertPoolDetectsInterning(pool, cBefore);
        }
    }

    /**
     * {@code TerminalTypeConstant.getValueString()} used to go through
     * {@code ensureResolvedConstant()}, which STORES the resolution into {@code m_constId}.
     * Rendering a type mid-compilation therefore collapsed an unresolved constant - the display path
     * advanced name resolution.
     *
     * <p>Observed here by counting {@code resolve()} calls on the unresolved constant: if rendering
     * stored the resolution, the type no longer holds the unresolved constant and the second render
     * never asks it again.</p>
     */
    @Test
    public void renderingATypeDoesNotConsumeItsUnresolvedConstant() {
        ConstantPool pool = coldPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            var counter = new ResolveCounter(pool, "Object");
            counter.resolve(pool.clzObject());

            // constructed directly, NOT via ensureTerminalTypeConstant: registering a constant
            // resolves it on purpose (registerConstants stores ensureResolvedConstant()). The
            // interesting state - an unresolved terminal type - is what the compiler holds before
            // registration, and it is exactly then that a debugger renders it.
            var type = new TerminalTypeConstant(pool, counter);
            counter.reset();

            assertEquals("Object", type.getValueString());
            assertEquals("Object", type.getValueString());
            assertEquals(2, counter.count(),
                    "rendering stored the resolution back into the type constant, so the second "
                    + "render no longer saw the unresolved constant the first one did");
        }
    }

    /**
     * Same defect one level up: {@code Annotation.getValueString()}/{@code getDescription()} went
     * through {@code getAnnotationClass()}, which stores the resolution into {@code m_constClass}.
     */
    @Test
    public void renderingAnAnnotationDoesNotConsumeItsUnresolvedConstant() {
        ConstantPool pool = coldPool();
        try (var ignore = ConstantPool.withPool(pool)) {
            var counter = new ResolveCounter(pool, "Object");
            counter.resolve(pool.clzObject());

            var annotation = new Annotation(pool, counter, null);
            counter.reset();

            assertEquals("@Object", annotation.getValueString());
            assertEquals("class=Object, params=0", annotation.getDescription());
            assertEquals(2, counter.count(),
                    "rendering stored the resolution back into the annotation, so the second render "
                    + "no longer saw the unresolved constant the first one did");
        }
    }

    /**
     * {@code BinaryAST.toString()} used to nag "TODO implement toString() for ..." through a
     * process-global {@code Set} that it ADDED to, and print to {@code System.err}. Rendering an AST
     * node in a debugger therefore mutated shared, unsynchronized process state - and the very fact
     * that the message appears only once means the rendering was observably order-dependent.
     */
    @Test
    public void renderingAnAstNodeWritesNothingGlobal() {
        var    node     = new UnrenderedAST();
        var    captured = new ByteArrayOutputStream();
        var    stderr   = System.err;
        String rendered;
        try {
            System.setErr(new PrintStream(captured, true));
            rendered = node.toString();
            node.toString();
        } finally {
            System.setErr(stderr);
        }

        assertEquals("StmtBlock", rendered, "a node with no richer rendering names its node type");
        assertEquals("", captured.toString(),
                "rendering an AST node wrote to System.err and added to a process-global Set");
    }

    /**
     * {@code MethodStructure.getDescription()} is the funnel every method rendering goes through
     * ({@code XvmStructure.toString()} delegates to it). It reported {@code line-count=} from
     * {@code Source.getLineCount()}, which calls {@code Source.normalize()}: the source text is
     * chopped into lines, ONE {@code StringConstant} PER LINE is interned, and
     * {@code m_aconstSrc}/{@code m_anIndents} are published unsynchronized. Expanding a method node
     * in a debugger grew the pool by the size of that method's own source.
     */
    @Test
    public void renderingAMethodDoesNotNormalizeItsSourceIntoThePool() {
        var file   = new FileStructure("PurityTest");
        var pool   = file.getConstantPool();
        var method = file.getModule().createMethod(true, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "hello", Parameter.NO_PARAMS, true, false);
        method.configureSource(SOURCE, 1);

        int    cBefore     = pool.size();
        String description = method.getDescription();

        assertTrue(description.contains("hasSource=true"), description);
        assertTrue(description.contains("line-count=<deferred>"),
                "an un-normalized source must report a deferred line count rather than chopping "
                + "itself up to answer: " + description);
        assertEquals(cBefore, pool.size(),
                "rendering a method interned its source lines into the ConstantPool");

        // the forced path is still there and still works when someone asks for it deliberately
        assertEquals(5, method.getSourceLineCount());
        assertTrue(pool.size() > cBefore,
                "negative control failed: normalizing the source did not grow the pool, so the "
                + "purity assertion above proves nothing");

        // and once it IS normalized, the description reports the real count
        assertTrue(method.getDescription().contains("line-count=5"), method.getDescription());
    }

    /** Prove {@code pool.size()} really does move when something interns; otherwise the assertions
     *  above would pass against a dead instrument. */
    private static void assertPoolDetectsInterning(ConstantPool pool, int cBefore) {
        pool.ensureParameterizedTypeConstant(pool.typeTuple(),
                pool.typeInt64(), pool.typeString(), pool.typeObject());
        assertTrue(pool.size() > cBefore,
                "negative control failed: interning a fresh type did not grow the pool, so the "
                + "purity assertion above proves nothing");
    }

    /**
     * A {@link BinaryAST} node with no {@code toString()} of its own, so that it exercises the base
     * class rendering - the one that used to nag through a process-global Set.
     */
    private static final class UnrenderedAST
            extends BinaryAST {
        @Override
        protected NodeType nodeType() {
            return NodeType.StmtBlock;
        }

        @Override
        protected void readBody(DataInput in, ConstantResolver res) {
        }

        @Override
        public void prepareWrite(ConstantResolver res) {
        }

        @Override
        protected void writeBody(DataOutput out, ConstantResolver res) {
        }
    }

    /**
     * An {@link UnresolvedNameConstant} that counts how many times it is asked to resolve, so a
     * test can tell whether a display path consumed it (stored the resolution) or merely read it.
     */
    private static final class ResolveCounter
            extends UnresolvedNameConstant {
        ResolveCounter(ConstantPool pool, String sName) {
            super(pool, sName);
        }

        @Override
        public Constant resolve() {
            m_cResolves++;
            return super.resolve();
        }

        void reset() {
            m_cResolves = 0;
        }

        int count() {
            return m_cResolves;
        }

        private int m_cResolves;
    }
}
