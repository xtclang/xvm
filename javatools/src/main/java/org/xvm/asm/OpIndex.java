package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Set;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.InPlaceSupport;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitParamDesc.JitParams;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;

import org.xvm.javajit.registers.MultiSlot;
import org.xvm.javajit.registers.SingleSlot;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.Utils;

import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.loadFromContext;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for I_ (index based) and IIP_ (index based in-place) op codes.
 */
public abstract class OpIndex
        extends OpOptimized
        implements InPlaceSupport, NumberSupport {
    /**
     * Construct an "index based" op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     */
    protected OpIndex(Argument argTarget, Argument argIndex) {
        assert(!isAssignOp());

        m_argTarget = argTarget;
        m_argIndex  = argIndex;
    }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpIndex(Argument argTarget, Argument argIndex, Argument argReturn) {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argIndex  = argIndex;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpIndex(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget = readPackedInt(in);
        m_nIndex  = readPackedInt(in);
        if (isAssignOp()) {
            m_nRetValue = readPackedInt(in);
        }
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nIndex  = encodeArgument(m_argIndex, registry);
            if (isAssignOp()) {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
            }
        }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nIndex);
        if (isAssignOp()) {
            writePackedLong(out, m_nRetValue);
        }
    }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp() {
        // majority of the ops are assigning; let's default to that
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nIndex}, 2);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return complete(frame, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    /**
     * Complete the op processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieve cached call chain.
     */
    protected CallChain getOpChain(Frame frame, TypeConstant typeTarget) {
        ServiceContext ctx   = frame.f_context;
        CallChain      chain = (CallChain) ctx.getOpInfo(this, Category.Chain);
        if (chain != null) {
            TypeConstant typePrevTarget = (TypeConstant) ctx.getOpInfo(this, Category.Type);
            if (typeTarget.equals(typePrevTarget)) {
                return chain;
            }
        }
        return null;
    }

    /**
     * Cache the specified call chain for the given target.
     */
    protected void saveOpChain(Frame frame, TypeConstant typeTarget, CallChain chain) {
        ServiceContext ctx = frame.f_context;
        ctx.setOpInfo(this, Category.Chain, chain);
        ctx.setOpInfo(this, Category.Type, typeTarget);
    }

    @Override
    public void resetSimulation() {
        if (isAssignOp()) {
            resetRegister(m_argReturn);
        }
    }

    @Override
    public void simulate(Scope scope) {
        if (isAssignOp()) {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argIndex = registerArgument(m_argIndex, registry);
        if (isAssignOp()) {
            m_argReturn = registerArgument(m_argReturn, registry);
        }
    }

    @Override
    public String toString() {
        return super.toString()
                + ' '  + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argIndex,  m_nIndex)
                + ", " + Argument.toIdString(m_argReturn, m_nRetValue);
    }


    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        if (isAssignOp()) {
            TypeConstant typeFrom = bctx.getArgumentType(m_nTarget);
            bctx.typeMatrix.assign(getAddress(), m_nRetValue,
                    computeElementType(bctx.getTypeInfo(typeFrom)));
        } else {
            super.computeTypes(bctx);
        }
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        ConstantPool pool       = bctx.pool();
        RegisterInfo reg        = bctx.loadArgument(code, m_nTarget);
        TypeConstant typeTarget = reg.type();
        TypeInfo     infoTarget = bctx.getTypeInfo(typeTarget);
        TypeConstant typeEl     = computeElementType(infoTarget);
        boolean      fPrimitive = typeEl.isJitPrimitive();

        if (typeTarget.isArray()) {
            ClassDesc cdArray = bctx.builder.ensureClassDesc(typeTarget);
            if (fPrimitive) {
                buildPrimitiveArrayOp(bctx, code, reg, typeEl);
            } else {
                bctx.loadCtx(code);
                bctx.loadArgument(code, m_nIndex);
                switch (getOpCode()) {
                    case OP_I_GET -> {
                        code.invokevirtual(cdArray, "getElement$p",
                            MethodTypeDesc.of(CD_nObj, CD_Ctx, CD_long));
                        if (!typeEl.equals(pool.typeObject())) {
                            code.checkcast(bctx.builder.ensureClassDesc(typeEl));
                        }
                    }

                    case OP_I_SET -> {
                        bctx.loadArgument(code, getValueId());
                        code.invokevirtual(cdArray, "setElement$p",
                            MethodTypeDesc.of(CD_void, CD_Ctx, CD_long, CD_nObj));
                    }

                    default -> throw new UnsupportedOperationException(toName(getOpCode()));
                }
            }
        } else {
            String sName;
            String sOp;
            switch (getOpCode()) {
                case OP_I_GET    -> {sName = "getElement";    sOp = "[]"; }
                case OP_I_SET    -> {sName = "setElement";    sOp = "[]=";}
                case OP_IIP_INC  -> {sName = "nextValue";     sOp = "";   }
                case OP_IIP_DEC  -> {sName = "prevValue";     sOp = "";   }
                case OP_IIP_INCA -> {sName = "";              sOp = "x++";}
                case OP_IIP_DECA -> {sName = "";              sOp = "x--";}
                case OP_IIP_INCB -> {sName = "";              sOp = "++x";}
                case OP_IIP_DECB -> {sName = "";              sOp = "--x";}
                case OP_IIP_ADD  -> {sName = "add";           sOp = "+";  }
                case OP_IIP_SUB  -> {sName = "sub";           sOp = "-";  }
                case OP_IIP_MUL  -> {sName = "mul";           sOp = "*";  }
                case OP_IIP_DIV  -> {sName = "div";           sOp = "/";  }
                case OP_IIP_MOD  -> {sName = "mod";           sOp = "%";  }
                case OP_IIP_SHL  -> {sName = "shiftLeft";     sOp = "<<"; }
                case OP_IIP_SHR  -> {sName = "shiftRight";    sOp = ">>"; }
                case OP_IIP_USHR -> {sName = "shiftAllRight"; sOp = ">>"; }
                case OP_IIP_AND  -> {sName = "and";           sOp = "&";  }
                case OP_IIP_OR   -> {sName = "or";            sOp = "|";  }
                case OP_IIP_XOR  -> {sName = "xor";           sOp = "^";  }
                default          -> throw new UnsupportedOperationException(toName(getOpCode()));
            }

            TypeConstant  typeIndex = bctx.getArgumentType(m_nIndex);
            MethodInfo    method;
            boolean       fSet = getOpCode() == OP_I_SET;
            if (fSet) {
                Set<MethodConstant> set = infoTarget.findOpMethods(sName, sOp, 2);
                if (set.size() != 1) {
                    throw new UnsupportedOperationException(
                        "Cannot resolve the method: " + sName + " on " + typeTarget.getValueString());
                }
                method = infoTarget.getMethodById(set.iterator().next());

            } else {
                method = infoTarget.findOpMethod(sName, sOp, typeIndex);
            }

            JitMethodDesc jmd      = method.getJitDesc(bctx.builder, typeTarget);
            String        sJitName = method.ensureJitMethodName(bctx.typeSystem);

            assert jmd.isOptimized;
            sJitName += Builder.OPT;

            bctx.loadCtx(code);
            if (fSet) {
                bctx.loadCallArguments(code, jmd, new int[] {m_nIndex, getValueId()});
            } else {
                bctx.loadCallArguments(code, jmd, new int[] {m_nIndex});
            }
            if (typeTarget.isJitInterface()) {
                code.invokeinterface(reg.cd(), sJitName, jmd.optimizedMD);
            } else {
                code.invokevirtual(reg.cd(), sJitName, jmd.optimizedMD);
            }
        }

        if (isAssignOp()) {
            // the Op returns a value which needs to be stored in the return register
            if (fPrimitive) {
                // the generated code will leave the result on the stack, so just store it to the
                // return register
                bctx.storeValue(code, m_nRetValue, typeEl);
            } else {
                // the generated code was a method invocation, so process the return values the
                // same we would for an Invoke Op; the only difference is that if the container type
                // is parameterized, we need to generate a cast. For example, if the container
                // is "List<Person>", the "getElement" signature would be "nObj getElement()".
                if (typeTarget.isParameterizedDeep()) {
                    bctx.builder.generateCheckCast(code, typeEl);
                }
                JitParams     params = JitParamDesc.computeJitParams(bctx.builder, typeEl);
                JitMethodDesc jmd    = new JitMethodDesc(
                    params.apdStdParam(), JitParamDesc.NONE,
                    params.apdOptParam(), params.isOptimized() ? JitParamDesc.NONE : null);

                bctx.assignReturns(code, jmd, 1, new int[] {m_nRetValue});
            }
        }
        return -1;
    }

    /**
     * Compute the return type for the "getElement" op on the specified type.
     */
    public static TypeConstant computeElementType(TypeInfo infoFrom) {
        Set<MethodConstant> setMethods = infoFrom.findOpMethods("getElement", "[]", 1);
        if (setMethods.size() != 1) {
            throw new RuntimeException("Cannot find the element type for " +
                infoFrom.getType().removeAccess());
        }

        return setMethods.iterator().next().getSignature().getRawReturns()[0];
    }


    /**
     * @return the id of the argument value for corresponding ops
     */
    protected int getValueId() {
        throw new UnsupportedOperationException("TODO " + getClass().getName());
    }

    /**
     * Build the operation to execute on an array element.
     * <p>
     * The array reference is already loaded onto the stack.
     *
     * @param bctx      the current {@link BuildContext}
     * @param code      the {@link CodeBuilder} to use to generate byte codes
     * @param regArray  the {@link RegisterInfo} for the array reference
     * @param typeEl    the {@link TypeConstant} of the array element
     */
    protected void buildPrimitiveArrayOp(BuildContext bctx, CodeBuilder code, RegisterInfo regArray,
                                         TypeConstant typeEl) {

        RegisterInfo regElement = loadArrayElement(bctx, code, regArray);
        regElement.load(code);
        bctx.loadArgument(code, m_nIndex);

        if (typeEl.isJavaPrimitive()) {
            assert regElement.isSingle();
            buildPrimitiveLocal(bctx, code, regElement);
        } else if (typeEl.isXvmPrimitive()) {
            buildXvmPrimitiveLocal(bctx, code, regElement);
        }
        // regElement now has the result stored in it, so store it back to the array
        storeArrayElement(bctx, code, regArray, regElement);
        if (isAssignOp()) {
            // a return is required, so load regElement to the stack
            //regElement.load(code);
        } else {
            Builder.pop(code, bctx.builder, regElement.type());
        }
    }

    /**
     * Load the array element this operation is for to the stack.
     *
     * @param bctx      the current {@link BuildContext}
     * @param code      the {@link CodeBuilder} to use to generate byte codes
     * @param regArray  the {@link RegisterInfo} for the array reference
     */
    protected void loadArrayElementToStack(BuildContext bctx, CodeBuilder code,
            RegisterInfo regArray) {
        loadArrayElement(bctx, code, regArray, true);
    }

    /**
     * Load the array element this operation is for into temporary slots and create a
     * {@link RegisterInfo} representing the array element.
     *
     * @param bctx      the current {@link BuildContext}
     * @param code      the {@link CodeBuilder} to use to generate byte codes
     * @param regArray  the {@link RegisterInfo} for the array reference
     *
     * @return the {@link RegisterInfo} representing the array element
     */
    protected RegisterInfo loadArrayElement(BuildContext bctx, CodeBuilder code,
            RegisterInfo regArray) {
        return loadArrayElement(bctx, code, regArray, false);
    }


    /**
     * Load the primitive array element this operation refers to, either onto the stack if the
     * {@code onStack} parameter is {@code true} or into temporary slots if the {@code onStack}
     * parameter is {@code false}.
     *
     * @param bctx      the current {@link BuildContext}
     * @param code      the {@link CodeBuilder} to use to generate byte codes
     * @param regArray  the {@link RegisterInfo} for the array reference
     *
     * @return {@code null} if the {@code onStack} parameter is {@code true} otherwise the
     *         {@link RegisterInfo} representing the array element
     */
    private RegisterInfo loadArrayElement(BuildContext bctx, CodeBuilder code,
            RegisterInfo regArray, boolean onStack) {

        TypeConstant type    = regArray.type();
        TypeConstant typeEl  = type.resolveGenericType("Element");
        ClassDesc    cdArray = bctx.builder.ensureClassDesc(type);

        boolean javaPrimitive = typeEl.isJavaPrimitive();
        boolean xvmPrimitive  = typeEl.isXvmPrimitive();

        ClassDesc[] cds;
        ClassDesc   cdEl;
        if (javaPrimitive) {
            cdEl = JitTypeDesc.getPrimitiveClass(typeEl);
            cds  = new ClassDesc[]{cdEl};
        } else if (xvmPrimitive) {
            cds  = JitTypeDesc.getXvmPrimitiveClasses(typeEl);
            cdEl = cds[0];
        } else {
            cdEl = CD_nObj;
            cds  = new ClassDesc[]{cdEl};
        }
        assert cdEl != null;

        // get the element from the array
        bctx.loadCtx(code);
        bctx.loadArgument(code, m_nIndex);
        code.invokevirtual(cdArray, "getElement$pi", MethodTypeDesc.of(cdEl, CD_Ctx, CD_long));

        RegisterInfo regElement = null;

        if (onStack) {
            // load any remaining values from the context to the stack
            for (int i = 1 ; i < cds.length; i++) {
                loadFromContext(code, cds[i], i - 1);
            }
        } else {
            if (typeEl.isXvmPrimitive()) {
                // must be XVM primitive
                ClassDesc cd    = JitTypeDesc.getJitClass(bctx.builder, typeEl);
                int[]     slots = new int[cds.length];

                slots[0] = bctx.storeTempValue(code, cds[0]);
                for (int i = 1 ; i < cds.length; i++) {
                    loadFromContext(code, cds[i], i - 1);
                    slots[i] = bctx.storeTempValue(code, cds[i]);
                }
                regElement = new MultiSlot(bctx, 0, slots, JitFlavor.XvmPrimitive, typeEl,
                        cd, cds, "");
            } else {
                int slot = bctx.storeTempValue(code, cdEl);
                regElement = new SingleSlot(0, slot, JitFlavor.Primitive, typeEl, cdEl, "");
            }
        }

        return regElement;
    }

    /**
     * Update the element in the array with the primitive value(s) stored in {@code regElement}
     * register's slots.
     *
     * @param bctx        the current {@link BuildContext}
     * @param code        the {@link CodeBuilder} to use to generate byte codes
     * @param regArray    the {@link RegisterInfo} for the array reference
     * @param regElement  the {@link RegisterInfo} for the array element
     */
    protected void storeArrayElement(BuildContext bctx, CodeBuilder code, RegisterInfo regArray,
                                     RegisterInfo regElement) {
        ClassDesc   cdArray = bctx.builder.ensureClassDesc(regArray.type());
        ClassDesc[] cdArgs  = prependArgs(regElement.slotCds(), CD_Ctx, CD_long);

        regArray.load(code);
        bctx.loadCtx(code);
        bctx.loadArgument(code, m_nIndex);
        regElement.load(code);
        code.invokevirtual(cdArray, "setElement$pi", MethodTypeDesc.of(CD_void, cdArgs));
    }

    /**
     * Create a {@link ClassDesc} array by prepending the {@code prepend} array to the
     * {@code cds} array.
     *
     * @param cds      the {@link ClassDesc}s to prepend to
     * @param prepend  the {@link ClassDesc}s to prepend
     *
     * @return an array of {@link ClassDesc}s with the {@code prepend} values followed by the
     *         {@code cds} values
     */
    protected ClassDesc[] prependArgs(ClassDesc[] cds, ClassDesc... prepend) {
        ClassDesc[] cdArgs = new ClassDesc[cds.length + prepend.length];
        System.arraycopy(prepend, 0, cdArgs, 0, prepend.length);
        System.arraycopy(cds, 0, cdArgs, prepend.length, cds.length);
        return cdArgs;
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nIndex;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argIndex;
    private Argument m_argReturn;

    // categories for cached info
    enum Category {Chain, Type}
}