package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.LanguageAST.NodeType.TRY_FINALLY_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try..finally" statement (with optional catches).
 */
public class TryFinallyStmtAST<C>
        extends TryCatchStmtAST<C> {

    private StmtAST<C> catchAll;

    TryFinallyStmtAST() {}

    public TryFinallyStmtAST(StmtAST<C>[] resources, StmtAST<C> body, StmtAST<C>[] catches, StmtAST<C> catchAll) {
        super(resources, body, catches);

        assert catchAll != null;

        this.catchAll  = catchAll;
    }

    public StmtAST<C> getCatchAll() {
        return catchAll;
    }

    @Override
    public NodeType nodeType() {
        return TRY_FINALLY_STMT;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        catchAll  = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        catchAll.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        catchAll.write(out, res);
    }

    @Override
    public String dump() {
        return super.dump() +
               "\nfinally\n"  + indentLines(catchAll.dump(), "  ");
    }

    @Override
    public String toString() {
        return super.toString() + "\nfinally\n" + indentLines(catchAll.toString(), "  ");
    }
}