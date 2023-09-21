package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.TryCatchStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try..catch" or "using" (with optional catches) statement.
 */
public class TryCatchStmtAST
        extends BinaryAST {

    private ExprAST[]   resources;
    private BinaryAST   body;
    private BinaryAST[] catches;

    TryCatchStmtAST() {}

    public TryCatchStmtAST(ExprAST[] resources, BinaryAST body, BinaryAST[] catches) {
        assert resources == null || Arrays.stream(resources).allMatch(Objects::nonNull);
        assert body != null;
        assert catches == null || Arrays.stream(catches).allMatch(Objects::nonNull);

        this.resources = resources == null ? NO_EXPRS : resources;
        this.body      = body;
        this.catches   = catches == null   ? NO_ASTS : catches;
    }

    public BinaryAST[] getResources() {
        return resources;
    }

    public BinaryAST getBody() {
        return body;
    }

    public BinaryAST[] getCatches() {
        return catches;
    }

    @Override
    public NodeType nodeType() {
        return TryCatchStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        resources = readExprArray(in, res);
        body      = readAST(in, res);
        catches   = readASTArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareASTArray(resources, res);
        body.prepareWrite(res);
        prepareASTArray(catches, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(resources, out, res);
        body.write(out, res);
        writeASTArray(catches, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("try");

        if (resources.length > 0) {
            buf.append(" (");
            for (int i = 0, c = resources.length; i < c; ++i) {
                if (i > 0) {
                    buf.append(", ");
                }
                buf.append(resources[i]);
            }
            buf.append(')');
        }

        buf.append(" {\n")
           .append(indentLines(body.toString(), "  "))
           .append("\n}");

        if (catches.length > 0) {
            for (BinaryAST catch_ : catches) {
                buf.append(" catch (???) {\n")
                   .append(indentLines(catch_.toString(), "  "))
                   .append("\n}");
            }
        }
        return buf.toString();
    }
}