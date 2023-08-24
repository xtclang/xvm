package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.TRY_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while..do" statement.
 */
public class TryStmtAST<C>
        extends StmtAST<C> {

    private StmtAST<C>[] resources;
    private StmtAST<C>   body;
    private StmtAST<C>[] catches;
    private StmtAST<C>   catchAll;

    TryStmtAST() {}

    public TryStmtAST(StmtAST<C>[] resources, StmtAST<C> body, StmtAST<C>[] catches, StmtAST<C> catchAll) {
        assert resources == null || Arrays.stream(resources).allMatch(Objects::nonNull);
        assert body != null;
        assert catches == null || Arrays.stream(catches).allMatch(Objects::nonNull);
        assert catchAll != null;

        this.resources = resources == null ? NO_STMTS : resources;
        this.body      = body;
        this.catches   = catches == null   ? NO_STMTS : resources;
        this.catchAll  = catchAll;
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

    public StmtAST<C> getCatchAll() {
        return catchAll;
    }

    @Override
    public NodeType nodeType() {
        return TRY_STMT;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        resources = readStmtArray(in, res);
        body      = deserialize(in, res);
        catches   = readStmtArray(in, res);
        catchAll  = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareWriteASTArray(res, resources);
        body.prepareWrite(res);
        prepareWriteASTArray(res, catches);
        catchAll.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeASTArray(out, res, resources);
        body.write(out, res);
        writeASTArray(out, res, catches);
        catchAll.write(out, res);
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
        if (resources != null) {
            sb.append(" (");
            for (StmtAST resource : resources) {
                sb.append(resource.toString())
                  .append(", ");
            sb.append(')');
            }
        }
        sb.append("\n")
          .append(indentLines(body.toString(), "  "));
        if (catches != null) {
            sb.append(" (");
            for (StmtAST resource : resources) {
                sb.append(resource.toString())
                  .append(", ");
            sb.append(')');
            }
        }
        // catch{}"
        return sb.toString();
    }
}