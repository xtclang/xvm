package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;

import static org.xvm.asm.ast.BinaryAST.NodeType.ListExpr;


/**
 * A List expression that is not a constant.
 */
public class ListExprAST
        extends ExprAST {

    private TypeConstant  type;
    private ExprAST[]     values;

    ListExprAST() {}

    public ListExprAST(TypeConstant type, ExprAST[] values) {
        assert type != null;
        assert values != null && Arrays.stream(values).allMatch(Objects::nonNull);
        this.type   = type;
        this.values = values;
    }

    public ExprAST[] getValues() {
        return values; // note: caller must not modify returned array in any way
    }

    @Override
    public NodeType nodeType() {
        return ListExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        type   = (TypeConstant) res.getConstant(readMagnitude(in));
        values = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        type = (TypeConstant) res.register(type);
        prepareASTArray(values, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writePackedLong(out, res.indexOf(type));
        writeExprArray(values, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(type).append(":[");
        for (ExprAST value : values) {
            buf.append(value.toString()).append(", ");
        }
        buf.delete(buf.length()-2, buf.length())
           .append(']');
        return buf.toString();
    }
}