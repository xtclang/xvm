package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.TryFinallyStmt;

import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "try..finally" statement (with optional catches).
 */
public class TryFinallyStmtAST<C>
        extends TryCatchStmtAST<C> {

    private RegAllocAST<C> exception; // optional
    private BinaryAST<C>   catchAll;

    TryFinallyStmtAST() {}

    public TryFinallyStmtAST(ExprAST<C>[] resources, BinaryAST<C> body, BinaryAST<C>[] catches,
                             RegAllocAST<C> exception, BinaryAST<C> catchAll) {
        super(resources, body, catches);

        assert catchAll != null;

        this.exception = exception;
        this.catchAll  = catchAll;
    }

    public RegAllocAST<C> getException() {
        return exception;
        }

    public BinaryAST<C> getCatchAll() {
        return catchAll;
    }

    @Override
    public NodeType nodeType() {
        return TryFinallyStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        if (readPackedInt(in) != 0) {
            exception = readExprAST(in, res);
        }
        catchAll = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        res.enter();
        if (exception != null) {
            exception.prepareWrite(res);
        }
        catchAll.prepareWrite(res);
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.writeBody(out, res);

        res.enter();
        if (exception == null) {
            writePackedLong(out, 0);
        } else {
            writePackedLong(out, 1);
            exception.writeExpr(out, res);
        }
        catchAll.write(out, res);
        res.exit();
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