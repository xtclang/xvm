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
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nObj;

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
        if (isTypeAware()) {
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

        // we cannot use Argument.toIdString(), since it returns a quoted string
        try {
            if (nNameId <= Op.CONSTANT_OFFSET) {
                ServiceContext context = ServiceContext.getCurrentContext();
                if (context != null) {
                    return ((StringConstant) context.getCurrentFrame().
                            localConstants()[convertId(nNameId)]).getValue();
                }
            }
        } catch (Throwable ignore) {}

        return "?";
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

            clzArray = xArray.INSTANCE.ensureParameterizedClass(context.f_container, typeEl);

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
                    bctx.getConstant(m_nType, TypeConstant.class).getParamType(0);

                default -> throw new UnsupportedOperationException(Op.toName(getOpCode()));
            };

            if (typeVar.containsGenericType(true)) {
                typeVar = typeVar.resolveGenerics(bctx.pool(), bctx.typeInfo.getType());
            }
            bctx.typeMatrix.declare(getAddress(), m_nVar, typeVar);
        } else {
            super.computeTypes(bctx);
        }
    }

    /**
     * Build an array variable.
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
                .invokestatic(CD_ArrayObj, "$new$p", MD_newArray);

        for (int nArg : anArgValue) {
            code.dup()
                    .aload(code.parameterSlot(0));
            bctx.loadArgument(code, nArg);
            code.invokevirtual(CD_ArrayObj, "add", MD_add)
                    .pop();
        }
        reg.store(bctx, code, type);
        return -1;
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The method description for Array.$new$p().
     */
    private static final MethodTypeDesc MD_newArray
            = MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_TypeConstant, CD_long, CD_boolean);

    /**
     * The method description for Array.add().
     */
    private static final MethodTypeDesc MD_add = MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_nObj);

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