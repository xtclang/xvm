package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An "assert" statement.
 */
public class AssertStmtAST<C>
        extends StmtAST<C>
    {
    private LanguageAST<C>[] tests;
    private ExprAST<C>       interval; // could be null
    private ExprAST<C>       message;  // could be null

    AssertStmtAST() {}

    public AssertStmtAST(LanguageAST<C>[] tests, ExprAST<C> interval, ExprAST<C> message) {
        assert tests != null && Arrays.stream(tests).allMatch(Objects::nonNull);

        this.tests    = tests;
        this.interval = interval;
        this.message  = message;
    }

    public LanguageAST<C>[] getTests()
        {
        return tests;
        }

    public ExprAST<C> getInterval()
        {
        return interval;
        }

    public ExprAST<C> getMessage()
        {
        return message;
        }

    @Override
    public NodeType nodeType() {
        return NodeType.AssertStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        tests = readASTArray(in, res);

        int flags = readMagnitude(in);
        if ((flags & 1) != 0) {
            interval = deserialize(in, res);
        }
        if ((flags & 2) != 0) {
            message = deserialize(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareWriteASTArray(res, tests);
        if (interval != null) {
            interval.prepareWrite(res);
        }
        if (message != null) {
            message.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeASTArray(out, res, tests);

        int flags = (interval == null ? 0 : 1)
                  | (message  == null ? 0 : 2);
        writePackedLong(out, flags);

        if (interval != null) {
            interval.write(out, res);
        }
        if (message != null) {
            message.write(out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder("assert ");
        for (LanguageAST<C> test : tests) {
            buf.append('\n').append(indentLines(test.dump(), "  "));
        }
        if (interval != null) {
            buf.append("\n:rnd").append(interval.dump());
        }
        if (message != null) {
            buf.append("\nas ").append(message.dump());
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("assert ");
        for (LanguageAST<C> test : tests) {
            buf.append('\n').append(indentLines(test.toString(), "  "));
        }
        if (interval != null) {
            buf.append("\n:rnd").append(interval);
        }
        if (message != null) {
            buf.append("\nas ").append(message);
        }
        return buf.toString();
    }
}