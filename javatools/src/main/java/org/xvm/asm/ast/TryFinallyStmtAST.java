package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.TryFinallyStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try..finally" statement (with optional catches).
 */
public class TryFinallyStmtAST<C>
        extends TryCatchStmtAST<C> {

    private BinaryAST<C> catchAll;

    TryFinallyStmtAST() {}

    public TryFinallyStmtAST(BinaryAST<C>[] resources, BinaryAST<C> body, BinaryAST<C>[] catches, BinaryAST<C> catchAll) {
        super(resources, body, catches);

        assert catchAll != null;

        this.catchAll  = catchAll;
    }

    public BinaryAST<C> getCatchAll() {
        return catchAll;
    }

    @Override
    public NodeType nodeType() {
        return TryFinallyStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        catchAll  = readAST(in, res);
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