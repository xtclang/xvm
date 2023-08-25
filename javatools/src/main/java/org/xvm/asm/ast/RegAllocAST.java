package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.NamedRegAlloc;
import static org.xvm.asm.ast.LanguageAST.NodeType.RegAlloc;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Allocate a register, i.e. declare a local variable. This AST node is only an "expression" in the
 * sense that the variable (the register itself) can be used as an expression.
 */
public class RegAllocAST<C>
        extends ExprAST<C> {

    private C type;
    private C name;

    /**
     * Registers are numbered sequentially from zero, and are scoped. The register ID is not stored
     * persistently (as it can be calculated). The responsibility for assigning the register number
     * (either during compilation or after loading from disk) is not visible to this class.
     */
    private transient int reg;

    RegAllocAST(boolean named) {
        if (named) {
            // use an invalid register id as an indicator to the subsequent read() operation that
            // there is a name
            reg = -1;
        }
    }

    /**
     * @param type  the type of the register
     * @param name  the name of the register (can be null if the register is unnamed
     * @param reg   the register id (which may be a temporary id if the final register id has not
     *              yet been calculated)
     */
    public RegAllocAST(C type, C name, int reg) {
        assert type != null;
        this.type = type;
        this.name = name;
        this.reg  = reg;
    }

    public C getType() {
        return type;
    }

    public C getName() {
        return name;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return name != null || reg < 0 ? NamedRegAlloc : RegAlloc;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        type = res.getConstant(readMagnitude(in));
        name = reg < 0 ? res.getConstant(readMagnitude(in)) : null;
        reg  = 0;
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type = res.register(type);
        name = res.register(name);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writePackedLong(out, res.indexOf(type));
        if (name != null) {
            writePackedLong(out, res.indexOf(name));
        }
    }

    @Override
    public String toString() {
        return type + " " + (name == null ? "_" : name.toString());
    }
}