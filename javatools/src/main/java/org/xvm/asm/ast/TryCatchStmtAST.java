package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.TRY_CATCH_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try..catch" or "using" (with optional catches) statement.
 */
public class TryCatchStmtAST<C>
        extends StmtAST<C> {

    private StmtAST<C>[] resources;
    private StmtAST<C>   body;
    private StmtAST<C>[] catches;

    TryCatchStmtAST() {}

    public TryCatchStmtAST(StmtAST<C>[] resources, StmtAST<C> body, StmtAST<C>[] catches) {
        assert resources == null || Arrays.stream(resources).allMatch(Objects::nonNull);
        assert body != null;
        assert catches == null || Arrays.stream(catches).allMatch(Objects::nonNull);

        this.resources = resources == null ? NO_STMTS : resources;
        this.body      = body;
        this.catches   = catches == null   ? NO_STMTS : catches;
    }

    public StmtAST<C>[] getResources() {
        return resources;
    }

    public StmtAST<C> getBosy() {
        return body;
    }
    public StmtAST<C>[] getCatches() {
        return catches;
    }


    @Override
    public NodeType nodeType() {
        return TRY_CATCH_STMT;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        resources = readStmtArray(in, res);
        body      = deserialize(in, res);
        catches   = readStmtArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareWriteASTArray(res, resources);
        body.prepareWrite(res);
        prepareWriteASTArray(res, catches);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeASTArray(out, res, resources);
        body.write(out, res);
        writeASTArray(out, res, catches);
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
            for (StmtAST resource : resources) {
                sb.append(resource)
                  .append(", ");
            sb.append(')');
            }
        }
        if (catches.length > 0) {
            for (StmtAST catch_ : catches) {
                sb.append("\n")
                  .append(indentLines(catch_.toString(), "  "));
            }
        }
        return sb.toString();
    }
}