package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;

import static org.xvm.asm.ast.LanguageAST.NodeType.MAP_EXPR;


/**
 * A Map expression that is not a constant.
 */
public class MapExprAST<C>
        extends ExprAST<C> {

    private C            type;
    private ExprAST<C>[] keys;
    private ExprAST<C>[] values;

    MapExprAST() {}

    public MapExprAST(C type, ExprAST<C>[] keys, ExprAST<C>[] values) {
        assert type != null;
        assert keys   != null && Arrays.stream(keys  ).allMatch(Objects::nonNull);
        assert values != null && Arrays.stream(values).allMatch(Objects::nonNull);
        assert  keys.length == values.length;
        this.type   = type;
        this.keys   = keys;
        this.values = values;
    }

    public C getType() {
        return type;
    }

    public ExprAST<C>[] getKeys() {
        return keys; // note: caller must not modify returned array in any way
    }

    public ExprAST<C>[] getValues() {
        return values; // note: caller must not modify returned array in any way
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return MAP_EXPR;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        type = res.getConstant(readMagnitude(in));

        int          keyCount = readMagnitude(in);
        ExprAST<C>[] keys     = keyCount == 0 ? NO_EXPRS : new ExprAST[keyCount];
        for (int i = 0; i < keyCount; i++) {
            keys[i] = deserialize(in, res);
        }
        this.keys = keys;

        int          valueCount = readMagnitude(in);
        ExprAST<C>[] values     = valueCount == 0 ? NO_EXPRS : new ExprAST[valueCount];
        for (int i = 0; i < valueCount; i++) {
            values[i] = deserialize(in, res);
        }
        this.values = values;
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type = res.register(type);
        for (ExprAST key : keys) {
            key.prepareWrite(res);
        }
        for (ExprAST value : values) {
            value.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writePackedLong(out, res.indexOf(type));

        writePackedLong(out, keys.length);
        for (ExprAST<C> key : keys) {
            key.write(out, res);
        }
        writePackedLong(out, values.length);
        for (ExprAST<C> value : values) {
            value.write(out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(type).append(":[");
        for (int i = 0, c = keys.length; i < c; i++) {
            ExprAST key   = keys[i];
            ExprAST value = values[i];
            buf.append(key.dump())  .append("=")
               .append(value.dump()).append(", ");
        }
        buf.append(']');
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(type).append(":[");
        for (int i = 0, c = keys.length; i < c; i++) {
            ExprAST key   = keys[i];
            ExprAST value = values[i];
            buf.append(key.toString())  .append("=")
               .append(value.toString()).append(", ");
        }
        buf.append(']');
        return buf.toString();
    }
}