package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

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
public class RegAllocAST
        extends ExprAST {

    private final NodeType nodeType;

    /**
     * Registers are numbered sequentially from zero, and are scoped. The register ID is not stored
     * persistently (as it can be calculated). The responsibility for assigning the register number
     * (either during compilation or after loading from disk) is not visible to this class.
     */
    private transient RegisterAST reg;

    RegAllocAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct a register.
     *
     * @param type  the type that the register can hold
     * @param name  the name of the local variable that the register is being created for, or null
     */
    public RegAllocAST(TypeConstant type, StringConstant name) {
        assert type != null;
        this.reg      = new RegisterAST(type, name);
        this.nodeType = name == null ? RegAlloc : NamedRegAlloc;
    }

    /**
     * Construct an annotated register.
     *
     * @param refType  the type of the reference to the register
     * @param type     the type that the register can hold
     * @param name     the name of the local variable that the register is being created for, or null
     */
    public RegAllocAST(TypeConstant refType, TypeConstant type, StringConstant name) {
        assert refType != null && type != null;
        this.reg      = new RegisterAST(refType, type, name);
        this.nodeType = name == null ? AnnoRegAlloc : AnnoNamedRegAlloc;
    }

    public RegisterAST getRegister() {
        return reg;
    }

    public Constant getRefType() {
        return reg.getRefType();
    }

    public String getName() {
        return reg.getName();
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return reg.getType(0);
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
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        TypeConstant refType = null;
        if (nodeType == AnnoRegAlloc || nodeType == AnnoNamedRegAlloc) {
            refType = (TypeConstant) res.getConstant(readPackedInt(in));
        }
        TypeConstant   type = (TypeConstant) res.getConstant(readPackedInt(in));
        StringConstant name = null;
        if (nodeType == NamedRegAlloc || nodeType == AnnoNamedRegAlloc) {
            name = (StringConstant) res.getConstant(readPackedInt(in));
        }

        reg = refType == null
                ? new RegisterAST(type, name)
                : new RegisterAST(refType, type, name);
        res.register(reg);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        // while the data is on the RegisterAST instance, it's technically "owned by" this; all
        // other use sites for the RegisterAST rely solely on the register's id
        reg.refType =   (TypeConstant) res.register(reg.refType);
        reg.type    =   (TypeConstant) res.register(reg.type);
        reg.name    = (StringConstant) res.register(reg.name);
        res.register(reg);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
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
        TypeConstant typeRef = reg.getRefType();
        TypeConstant type    = reg.getType();

        StringBuilder buf = new StringBuilder();
        if (typeRef != null) {
            buf.append(typeRef.getValueString())
               .append(' ');
        }
        buf.append(type.getValueString())
           .append(' ')
           .append(reg);
        return buf.toString();
    }
}