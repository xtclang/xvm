package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.AnnoNamedRegAlloc;
import static org.xvm.asm.ast.BinaryAST.NodeType.AnnoRegAlloc;
import static org.xvm.asm.ast.BinaryAST.NodeType.NamedRegAlloc;
import static org.xvm.asm.ast.BinaryAST.NodeType.RegAlloc;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Allocate a register, i.e. declare a local variable. This AST node is only an "expression" in the
 * sense that the variable (the register itself) can be used as an expression.
 */
public class RegAllocAST<C>
        extends ExprAST<C> {

    private final NodeType nodeType;

    /**
     * Registers are numbered sequentially from zero, and are scoped. The register ID is not stored
     * persistently (as it can be calculated). The responsibility for assigning the register number
     * (either during compilation or after loading from disk) is not visible to this class.
     */
    private transient RegisterAST<C> reg;

    RegAllocAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct a register.
     *
     * @param type  the type that the register can hold
     * @param name  the name of the local variable that the register is being created for, or null
     */
    public RegAllocAST(C type, C name) {
        assert type != null;
        this.reg      = new RegisterAST<>(type, name);
        this.nodeType = name == null ? RegAlloc : NamedRegAlloc;
    }

    /**
     * Construct an annotated register.
     *
     * @param refType  the type of the reference to the register
     * @param type     the type that the register can hold
     * @param name     the name of the local variable that the register is being created for, or null
     */
    public RegAllocAST(C refType, C type, C name) {
        assert refType != null && type != null;
        this.reg      = new RegisterAST<>(refType, type, name);
        this.nodeType = name == null ? AnnoRegAlloc : AnnoNamedRegAlloc;
    }

    public RegisterAST<C> getRegister() {
        return reg;
    }

    public C getRefType() {
        return reg.getRefType();
    }

    public C getType() {
        return reg.getType();
    }

    public C getName() {
        return reg.getName();
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return getType();
    }

    @Override
    public boolean isAssignable() {
        return true;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        C refType = null;
        if (nodeType == AnnoRegAlloc || nodeType == AnnoNamedRegAlloc) {
            refType = res.getConstant(readPackedInt(in));
        }
        C type = res.getConstant(readPackedInt(in));
        C name = null;
        if (nodeType == NamedRegAlloc || nodeType == AnnoNamedRegAlloc) {
            name = res.getConstant(readPackedInt(in));
        }

        reg = refType == null
                ? new RegisterAST<>(type, name)
                : new RegisterAST<>(refType, type, name);
        res.register(reg);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        // while the data is on the RegisterAST instance, it's technically "owned by" this; all
        // other use sites for the RegisterAST rely solely on the register's id
        reg.refType = res.register(reg.refType);
        reg.type    = res.register(reg.type);
        reg.name    = res.register(reg.name);
        res.register(reg);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        // what is notable about the serialization format is that it does *not* include the register
        // id (number); register ids are required to be gap-less and ascending, so the id can be
        // calculated by the resolver when the AST is read back into its object form from binary
        if (nodeType == AnnoRegAlloc || nodeType == AnnoNamedRegAlloc) {
            writePackedLong(out, res.indexOf(reg.refType));
        }
        writePackedLong(out, res.indexOf(reg.type));
        if (nodeType == NamedRegAlloc || nodeType == AnnoNamedRegAlloc) {
            writePackedLong(out, res.indexOf(reg.name));
        }
    }

    @Override
    public String toString() {
        return reg.toString();
    }
}