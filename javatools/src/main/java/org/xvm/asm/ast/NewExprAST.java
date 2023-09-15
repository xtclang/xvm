package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.NewChildExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.NewExpr;

import static org.xvm.asm.ast.BinaryAST.NodeType.NewVirtualExpr;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "new ..." expression.
 */
public class NewExprAST<C>
        extends ExprAST<C> {

    private final NodeType nodeType;

    private ExprAST<C>     parent; // can be null
    private C              type;
    private C              constr;
    private ExprAST<C>[]   args;

    NewExprAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct a NewExprAST for a non-generic top-level class or a virtual type.
     *
     * @param type     the type
     * @param constr   the constructor
     * @param args     the arguments
     * @param virtual  specifies a virtual constructor
     */
    public NewExprAST(C type, C constr, ExprAST<C>[] args, boolean virtual) {
        assert type != null && constr != null;
        assert args != null && Arrays.stream(args).allMatch(Objects::nonNull);

        this.type   = type;
        this.constr = constr;
        this.args   = args;
        nodeType    = virtual ? NewVirtualExpr : NewExpr;
    }

    /**
     * Construct a NewExprAST for a non-generic child class.
     *
     * @param parent  the parent
     * @param type    the type
     * @param constr  the constructor
     * @param args    the arguments
     */
    public NewExprAST(ExprAST<C> parent, C type, C constr, ExprAST<C>[] args) {
        assert parent != null && type != null && constr != null;
        assert args != null && Arrays.stream(args).allMatch(Objects::nonNull);

        this.parent = parent;
        this.type   = type;
        this.constr = constr;
        this.args   = args;
        nodeType    = NewChildExpr;
    }

    public C getType() {
        return type;
    }

    public C getConstructor() {
        return constr;
    }

    public ExprAST<C>[] getArgs() {
        return args;
    }

    public boolean isVirtual() {
        return nodeType == NewVirtualExpr;
    }

    public boolean isChild() {
        return nodeType == NewChildExpr;
    }

    public ExprAST<C> getParent() {
        return parent;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        if (isChild()) {
            parent = readExprAST(in, res);
        }
        type   = res.getConstant(readMagnitude(in));
        constr = res.getConstant(readMagnitude(in));
        args   = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        if (isChild()) {
            parent.prepareWrite(res);
        }
        type   = res.register(type);
        constr = res.register(constr);
        prepareASTArray(args, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        if (isChild()) {
            parent.writeExpr(out, res);
        }
        writePackedLong(out, res.indexOf(type));
        writePackedLong(out, res.indexOf(constr));
        writeExprArray(args, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        if (isChild()) {
            buf.append(parent)
               .append('.');
        }
        buf.append("new ")
            .append(type)
            .append('(');
        for (int i = 0, c = args.length; i < c; i++) {
            buf.append(args[i])
                .append(", ");
        }
        return buf.toString();
    }
}