package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.StmtExpr;



/**
 * A statement expression.
 */
public class StmtExprAST<C>
        extends ExprAST<C> {

    private BinaryAST<C> stmt;
    private Object[]     types;

    StmtExprAST() {}

    public StmtExprAST(BinaryAST<C> stmt, Object[] types) {
        assert stmt != null;
        assert types != null && Arrays.stream(types).allMatch(Objects::nonNull);

        this.stmt  = stmt;
        this.types = types;
    }

    public BinaryAST<C> getStatement() {
        return stmt;
    }

    @Override
    public C getType(int i) {
        return (C) types[i];
    }

    @Override
    public NodeType nodeType() {
        return StmtExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        stmt  = readAST(in, res);
        types = readConstArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        stmt.prepareWrite(res);
        res.registerAll(types);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        stmt.write(out, res);
        writeConstArray(types, out, res);
    }

    @Override
    public String dump() {
        return stmt.dump();
    }

    @Override
    public String toString() {
        return stmt.toString();
    }
}