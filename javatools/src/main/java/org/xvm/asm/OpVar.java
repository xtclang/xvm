package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;

import org.xvm.javajit.RegisterInfo;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_ArrayObj;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_TypeConstant;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for all "VAR" ops.
 */
public abstract class OpVar
        extends Op {
    /**
     * Construct a variable that corresponds to the specified register.
     *
     * @param reg  the register for the variable
     */
    protected OpVar(Register reg) {
        assert reg != null;
        m_reg = reg;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpVar(DataInput in, Constant[] aconst)
            throws IOException {
        this(in, aconst, true);
    }

    /**
     * Deserialization constructor with explicit opcode shape.
     *
     * @param in         the DataInput to read from
     * @param aconst     an array of constants used within the method
     * @param typeAware  true iff this opcode encodes type information
     */
    protected OpVar(DataInput in, Constant[] aconst, boolean typeAware)
            throws IOException {
        // Type-awareness controls the byte-stream layout and is static opcode
        // metadata; do not call isTypeAware() from a base constructor.
        // Subclasses that pass false are the same untyped opcodes that
        // previously overrode isTypeAware() to false.
        if (typeAware) {
            m_nType = readPackedInt(in);
        }
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (isTypeAware()) {
            m_nType = encodeArgument(getRegisterType(), registry);

            writePackedLong(out, m_nType);
        }
    }

    /**
     * @param aconst  (optional) an array of constants to retrieve constants by index from
     *
     * @return the variable name, iff the variable has a name (otherwise null)
     */
    protected String getName(Constant[] aconst) {
        return null;
    }

    /**
     * @return the variable name based on any of the present information
     */
    protected String getName(Constant[] aconst, StringConstant constName, int nNameId) {
        if (constName != null) {
            return constName.getValue();
        }

        if (aconst != null) {
            return ((StringConstant) aconst[convertId(nNameId)]).getValue();
        }

        // PURE: do NOT read the ambient ServiceContext/fiber - under a debugger that is whatever
        // frame is current on the observing thread, usually NOT this op's frame, so it indexed an
        // unrelated constant array (AIOOBE silently swallowed) or returned a misleading name. A name
        // referenced only by index needs a frame; render a marker. Frame-owning dumps resolve it via
        // getName(Frame, ...) below.
        return nNameId <= Op.CONSTANT_OFFSET ? "name:#" + convertId(nNameId) : "?";
    }

    /**
     * Forced display: resolve a name id against an EXPLICITLY supplied frame, never the ambient
     * service context.
     *
     * @param frame     the frame that owns the op, or null
     * @param aconst    (optional) an array of constants to retrieve constants by index from
     * @param constName (optional) the name constant
     * @param nNameId   the name-constant index
     *
     * @return the variable name
     */
    protected String getName(Frame frame, Constant[] aconst, StringConstant constName, int nNameId) {
        if (constName == null && aconst == null && nNameId <= Op.CONSTANT_OFFSET && frame != null) {
            return ((StringConstant) frame.localConstants()[convertId(nNameId)]).getValue();
        }
        return getName(aconst, constName, nNameId);
    }

    /**
     * @param aconst  (optional) an array of constants to retrieve constants by index from
     *
     * @return the variable type
     */
    protected TypeConstant getType(Constant[] aconst) {
        return m_reg == null
                ? (TypeConstant) aconst[convertId(m_nType)]
                : m_reg.getType();
    }

    /**
     * @return true iff this op carries the type information
     */
    protected boolean isTypeAware() {
        // majority of Var_* op-codes carry the type; only Var_C and Var_CN don't
        return true;
    }

    /**
     * Helper method to calculate a TypeComposition for a sequence array.
     *
     * @param frame     the current frame
     * @param typeList  the sequence type
     *
     * @return the corresponding array class composition
     */
    protected TypeComposition getArrayClass(Frame frame, TypeConstant typeList) {
        ServiceContext  context  = frame.f_context;
        TypeComposition clzArray = (TypeComposition) context.getOpInfo(this, Category.Composition);
        TypeConstant    typePrev = (TypeConstant)    context.getOpInfo(this, Category.Type);

        if (clzArray == null || !typeList.equals(typePrev)) {
            TypeConstant typeEl = typeList.resolveGenericType("Element");

            clzArray = xArray.getInstance(context.f_container).
                    ensureParameterizedClass(context.f_container, typeEl);

            context.setOpInfo(this, Category.Composition, clzArray);
            context.setOpInfo(this, Category.Type, typeList);
        }

        return clzArray;
    }

    /**
     * Note: Used only during compilation.
     *
     * @return the type of the register
     */
    public TypeConstant getRegisterType() {
        return m_reg.isVar()
                ? m_reg.ensureRegType(!m_reg.isWritable())
                : m_reg.getType();
    }

    /**
     * Note: Used only during compilation.
     *
     * @return the Register that holds the variable's value
     */
    public Register getRegister() {
        return m_reg;
    }

    @Override
    public void resetSimulation() {
        resetRegister(m_reg);
    }

    @Override
    public void simulate(Scope scope) {
        m_nVar = m_reg == null
                ? scope.allocVar()
                : m_reg.assignIndex(scope.allocVar());
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_reg.registerConstants(registry);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());

        String sName = getName(null);
        if (sName != null) {
            sb.append(' ')
              .append(sName)
              .append(',');
        }

        if (isTypeAware()) {
            sb.append(' ')
              .append(Argument.toIdString(null, m_nType))
              .append(',');
        }

        sb.append(' ');
        if (m_reg == null) {
            sb.append('#').append(m_nVar);
        } else {
            sb.append(m_reg);
        }

        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        if (isTypeAware()) {
            TypeConstant typeVar = switch (getOpCode()) {
                case OP_VAR, OP_VAR_I, OP_VAR_N, OP_VAR_IN, OP_VAR_S, OP_VAR_SN ->
                    bctx.getTypeConstant(m_nType);

                case OP_VAR_D, OP_VAR_DN ->
                    bctx.getTypeConstant(m_nType).getParamType(0);

                default -> throw new UnsupportedOperationException(Op.toName(getOpCode()));
            };

            bctx.typeMatrix.declare(getAddress(), m_nVar, typeVar);
        } else {
            throw new UnsupportedOperationException(Op.toName(getOpCode()));
        }
    }

    /**
     * Build an array variable (for VAR_S, VAR_SN).
     *
     * @param bctx        the current build context
     * @param code        the {@link CodeBuilder} to use to generate op codes
     * @param anArgValue  the array of values to add to the new array
     * @param sName       the name of the variable, or empty string for unnamed
     */
    protected int buildArray(BuildContext bctx, CodeBuilder code, int[] anArgValue, String sName) {
        TypeConstant type = bctx.getTypeConstant(m_nType);
        RegisterInfo reg  = bctx.introduceRegister(code, m_nVar, type, sName);

        bctx.loadCtx(code);
        bctx.loadTypeConstant(code, type);
        code.loadConstant((long) anArgValue.length)
                .iconst_0()
                .invokestatic(CD_ArrayObj, "$new$p",
                    MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_TypeConstant, CD_long, CD_boolean));

        for (int nArg : anArgValue) {
            code.dup();
            bctx.loadCtx(code);
            bctx.loadArgument(code, nArg);
            code.invokevirtual(CD_ArrayObj, "add", MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_Object))
                    .pop();
        }
        reg.store(bctx, code, type);
        return -1;
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register that the VAR op is responsible for creating.
     */
    protected transient Register m_reg;

    /**
     * The var index.
     */
    protected transient int m_nVar = -1;

    /**
     * The type constant id.
     */
    protected int m_nType;

    // categories for cached info
    enum Category {Composition, Type}
}
