package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.xvm.util.Handy;

import static org.xvm.asm.ast.LanguageAST.NodeType.FOR_STMT;


/**
 * A "for(init;cond;update){...}" statement.
 */
public class ForStmtAST<C>
        extends LoopStmtAST<C> {

    protected StmtAST<C>[]    init;
    protected ConditionAST<C> cond;
    protected StmtAST<C>[]    update;

    ForStmtAST() {}

    public ForStmtAST(StmtAST<C>[] init, ConditionAST<C> cond, StmtAST<C>[] update, StmtAST<C> body) {
        assert init != null && cond != null && update != null && body != null;
        this.init   = init;
        this.cond   = cond;
        this.update = update;
        this.body   = body;
    }

    @Override
    public NodeType nodeType() {
        return FOR_STMT;
    }

    public StmtAST<C>[] getInit() {
        return init;
    }

    public ConditionAST<C> getCond() {
        return cond;
    }

    public StmtAST<C>[] getUpdate() {
        return update;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        init   = readStmtArray(in, res);
        cond   = new ConditionAST<C>();
        cond.read(in, res);
        update = readStmtArray(in, res);
        body   = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        for (StmtAST stmt : init) {
            stmt.prepareWrite(res);
        }
        cond.prepareWrite(res);
        for (StmtAST stmt : update) {
            stmt.prepareWrite(res);
        }
        body.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeASTArray(out, res, init);
        cond.write(out, res);
        writeASTArray(out, res, update);
        body.write(out, res);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        if (init.length <= 1 && update.length <= 1) {
            buf.append('(')
               .append(init.length == 0 ? "" : init[0])
               .append("; ")
               .append(cond.dump())
               .append("; ")
               .append(update.length == 0 ? "" : init[0])
               .append(')');
        } else {
            buf.append("\ninit");
            for (StmtAST stmt : init) {
                buf.append('\n').append(Handy.indentLines(stmt.dump(), "  "));
            }
            buf.append("\ncond");
            buf.append('\n').append(Handy.indentLines(cond.dump(), "  "));
            buf.append("\nupdate");
            for (StmtAST stmt : update) {
                buf.append('\n').append(Handy.indentLines(stmt.dump(), "  "));
            }
        }
        buf.append("\n{").append(Handy.indentLines(body.dump(), "  ")).append("\n}");
        return buf.toString();
    }
}
