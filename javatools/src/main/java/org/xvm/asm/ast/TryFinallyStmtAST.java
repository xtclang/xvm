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
public class TryFinallyStmtAST
        extends TryCatchStmtAST {

    private RegAllocAST exception; // optional
    private BinaryAST   catchAll;

    TryFinallyStmtAST() {}

    public TryFinallyStmtAST(ExprAST[] resources, BinaryAST body, BinaryAST[] catches,
                             RegAllocAST exception, BinaryAST catchAll) {
        super(resources, body, catches);

        assert catchAll != null;

        this.exception = exception;
        this.catchAll  = catchAll;
    }

    public RegAllocAST getException() {
        return exception;
        }

    public BinaryAST getCatchAll() {
        return catchAll;
    }

    @Override
    public NodeType nodeType() {
        return TryFinallyStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        if (readPackedInt(in) != 0) {
            exception = (RegAllocAST) readExprAST(in, res);
        }
        catchAll = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        res.enter();
        if (exception != null) {
            exception.prepareWrite(res);
        }
        catchAll.prepareWrite(res);
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
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
    public String toString() {
        return super.toString()
            + " finally {\n"
            + indentLines(catchAll.toString(), "  ")
            + "\n}";
    }
}