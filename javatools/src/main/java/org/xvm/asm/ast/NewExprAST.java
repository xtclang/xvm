package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.NewChildExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.NewExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.NewVirtualExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "new ..." expression.
 */
public class NewExprAST
        extends ExprAST {

    private final NodeType nodeType;

    private ExprAST      parent; // for NewVirtualExpr node contains the type target;
                                 // for NewChildExpr node contains the child's parent;
                                 // for NewExpr node is always null
    private TypeConstant type;
    private Constant     constr;
    private ExprAST[]    args;

    NewExprAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct a NewExprAST for a non-generic top-level class.
     *
     * @param type     the type
     * @param constr   the constructor
     * @param args     the arguments
     */
    public NewExprAST(TypeConstant type, Constant constr, ExprAST[] args) {
        assert type != null && constr != null;
        assert args != null && Arrays.stream(args).allMatch(Objects::nonNull);

        this.type   = type;
        this.constr = constr;
        this.args   = args;
        nodeType    = NewExpr;
    }

    /**
     * Construct a NewExprAST for a non-generic child class.
     *
     * @param parent  the parent
     * @param type    the type
     * @param constr  the constructor
     * @param args    the arguments
     */
    public NewExprAST(ExprAST parent, TypeConstant type, Constant constr, ExprAST[] args) {
        assert parent != null && type != null && constr != null;
        assert args != null && Arrays.stream(args).allMatch(Objects::nonNull);

        this.parent = parent;
        this.type   = type;
        this.constr = constr;
        this.args   = args;
        nodeType    = NewChildExpr;
    }

    /**
     * Construct a NewExprAST for a virtual construction.
     *
     * @param target   the target that should be used to compute the resulting type
     * @param constr   the constructor
     * @param args     the arguments
     */
    public NewExprAST(ExprAST target, Constant constr, ExprAST[] args) {
        assert target != null && constr != null;
        assert args != null && Arrays.stream(args).allMatch(Objects::nonNull);

        this.parent = target;
        this.constr = constr;
        this.args   = args;
        nodeType    = NewVirtualExpr;
    }

    public Constant getType() {
        return type;
    }

    public Constant getConstructor() {
        return constr;
    }

    public ExprAST[] getArgs() {
        return args;
    }

    public boolean isVirtual() {
        return nodeType == NewVirtualExpr;
    }

    public boolean isChild() {
        return nodeType == NewChildExpr;
    }

    public ExprAST getParent() {
        return parent;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return nodeType == NewVirtualExpr ? parent.getType(0) : type;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        if (isChild() || isVirtual()) {
            parent = readExprAST(in, res);
        }
        if (!isVirtual()) {
            type = (TypeConstant) res.getConstant(readMagnitude(in));
        }
        constr = res.getConstant(readMagnitude(in));
        args   = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        if (isChild()) {
            parent.prepareWrite(res);
        }
        if (!isVirtual()) {
            type = res.register(type);
        }
        constr = res.register(constr);
        prepareASTArray(args, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        if (isChild() || isVirtual()) {
            parent.writeExpr(out, res);
        }
        if (!isVirtual()) {
            writePackedLong(out, res.indexOf(type));
        }
        writePackedLong(out, res.indexOf(constr));
        writeExprArray(args, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        if (isChild() || isVirtual()) {
            buf.append(parent)
               .append('.');
        }
        buf.append("new");
        if (!isVirtual()) {
            buf.append(' ')
               .append(type.getValueString());
        }
        buf.append('(');
        for (int i = 0, c = args.length; i < c; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(args[i]);
        }
        buf.append(')');
        return buf.toString();
    }
}