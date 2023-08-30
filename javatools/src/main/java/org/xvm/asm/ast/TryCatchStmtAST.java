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
public class TryCatchStmtAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C>[] resources;
    private BinaryAST<C>   body;
    private BinaryAST<C>[] catches;

    TryCatchStmtAST() {}

    public TryCatchStmtAST(BinaryAST<C>[] resources, BinaryAST<C> body, BinaryAST<C>[] catches) {
        assert resources == null || Arrays.stream(resources).allMatch(Objects::nonNull);
        assert body != null;
        assert catches == null || Arrays.stream(catches).allMatch(Objects::nonNull);

        this.resources = resources == null ? NO_ASTS : resources;
        this.body      = body;
        this.catches   = catches == null   ? NO_ASTS : catches;
    }

    public BinaryAST<C>[] getResources() {
        return resources;
    }

    public BinaryAST<C> getBosy() {
        return body;
    }

    public BinaryAST<C>[] getCatches() {
        return catches;
    }


    @Override
    public NodeType nodeType() {
        return TryCatchStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        resources = readASTArray(in, res);
        body      = readAST(in, res);
        catches   = readASTArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareASTArray(resources, res);
        body.prepareWrite(res);
        prepareASTArray(catches, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeASTArray(resources, out, res);
        body.write(out, res);
        writeASTArray(catches, out, res);
    }

    @Override
    public String dump() {
        return "try\n" + indentLines(body.dump(), "  ") +
               "\ncatch\n"  + indentLines(body.dump(), "  ");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("try");
        if (resources.length > 0) {
            sb.append(" (");
            for (BinaryAST<C> resource : resources) {
                sb.append(resource)
                  .append(", ");
            sb.append(')');
            }
        }
        if (catches.length > 0) {
            for (BinaryAST catch_ : catches) {
                sb.append("\n")
                  .append(indentLines(catch_.toString(), "  "));
            }
        }
        return sb.toString();
    }
}