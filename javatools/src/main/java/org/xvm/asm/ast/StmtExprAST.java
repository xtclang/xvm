package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.StmtExpr;

import static org.xvm.util.Handy.indentLines;



/**
 * A statement expression.
 */
public class StmtExprAST
        extends ExprAST {

    private BinaryAST      stmt;
    private TypeConstant[] types;

    StmtExprAST() {}

    public StmtExprAST(BinaryAST stmt, TypeConstant[] types) {
        assert stmt != null;
        assert types != null && Arrays.stream(types).allMatch(Objects::nonNull);

        this.stmt  = stmt;
        this.types = types;
    }

    public BinaryAST getStatement() {
        return stmt;
    }

    @Override
    public NodeType nodeType() {
        return StmtExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        return types[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        stmt  = readAST(in, res);
        types = readTypeArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        stmt.prepareWrite(res);
        prepareConstArray(types, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        stmt.write(out, res);
        writeConstArray(types, out, res);
    }

    @Override
    public String toString() {
        String text = stmt.toString();
        return text.indexOf('\n') < 0
                ? '{' + text + '}'
                : "{\n" + indentLines(text, "  ") + "\n}";
    }
}