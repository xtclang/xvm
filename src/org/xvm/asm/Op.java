package org.xvm.asm;


import com.sun.org.apache.xpath.internal.Arg;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.*;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.template.types.xProperty;


import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for all XVM machine code instructions, each of which is called an "op".
 */
public abstract class Op
    {
    // ----- Op interface --------------------------------------------------------------------------

    /**
     * Write the op-code.
     *
     * @param out       the DataOutput to write to
     * @param registry  the ConstantRegistry for the method
     *
     * @throws IOException  if an error occurs while writing the op
     */
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(getOpCode());
        }

    /**
     * @return the byte value that identifies the op-code, in the range 0-255
     */
    public int getOpCode()
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Process this op.
     *
     * @param frame  current execution frame
     * @param iPC    instruction pointer
     *
     * @return a positive iPC or a negative {@code R_}* value
     */
    public abstract int process(Frame frame, int iPC);

    /**
     * Simulate the effects of the op on the passed scope.
     *
     * @param scope  the variable scope
     */
    public void simulate(Scope scope)
        {
        }

    /**
     * Invoked as part of the assembly process to register all of the constants being used by the
     * ops.
     *
     * @param registry  the ConstantRegistry to use to register any constants used by this op
     */
    public void registerConstants(ConstantRegistry registry)
        {
        }

    /**
     * Allows each op to know its location within the code.
     *
     * @param code  the code containing the op
     * @param iPC   the program counter (address)
     */
    public void resolveAddress(Code code, int iPC)
        {
        }

    /**
     * Determine if the specified op is contained within this op, or is this op.
     *
     * @param that  the op to search for
     *
     * @return true iff this op is the op being searched for, or contains the op being searched for
     */
    public boolean contains(Op that)
        {
        return this == that;
        }

    /**
     * Represents any argument for an op, including constants, registers, and pre-defined
     * references like "this".
     */
    public interface Argument
        {
        /**
         * @return the type of the argument
         */
        TypeConstant getType();
        }

    /**
     * Determine if the specified Argument is a Constant.
     *
     * @param arg  the argument
     *
     * @return true iff the argument is a Constant
     */
    protected static boolean isConstant(Argument arg)
        {
        return arg instanceof Constant;
        }

    /**
     * Determine if the specified Argument is a register (variable).
     *
     * @param arg  the argument
     *
     * @return true iff the argument is a register
     */
    protected static boolean isRegister(Argument arg)
        {
        return arg instanceof Register;
        }

    /**
     * Check if the specified Argument represents "the next available" register and if
     * so, assign its index to the next allocated variable.
     *
     * @param scope  the scope
     * @param arg    the argument
     */
    public void checkNextRegister(Scope scope, Argument arg)
        {
        if (arg instanceof Register && ((Register) arg).isUnknown())
            {
            ((Register) arg).assignIndex(scope.allocVar());
            }
        }

    /**
     * Check if any of the specified Arguments represent "the next available" register and if
     * so, assign its index to the next allocated variable.
     *
     * @param scope  the scope
     * @param aArg   the argument array
     */
    public void checkNextRegisters(Scope scope, Argument[] aArg)
        {
        if (aArg != null)
            {
            for (Argument arg : aArg)
                {
                if (arg instanceof Register && ((Register) arg).isUnknown())
                    {
                    ((Register) arg).assignIndex(scope.allocVar());
                    }
                }
            }
        }

    /**
     * Determine if the specified Argument is readable. This is equivalent to the Ref for the
     * argument supporting the get() operation.
     *
     * @param arg  the argument
     *
     * @return true iff the argument is readable
     */
    protected static boolean isReadable(Argument arg)
        {
        return !(arg instanceof Register) || ((Register) arg).isReadable();
        }

    /**
     * Determine if the specified Argument is readable. This is equivalent to the Ref for the
     * argument supporting the set() operation.
     *
     * @param arg  the argument
     *
     * @return true iff the argument is writable
     */
    protected static boolean isWritable(Argument arg)
        {
        return arg instanceof Register && ((Register) arg).isWritable();
        }

    /**
     * Register the specified argument if it's a constant.
     *
     * @param arg       the argument or null
     * @param registry  the ConstantRegistry to use to register any constants used by this op
     */
    protected static void registerArgument(Argument arg, ConstantRegistry registry)
        {
        if (arg instanceof Constant)
            {
            registry.register((Constant) arg);
            }
        }

    /**
     * Register the specified arguments if they are constants.
     *
     * @param aArg      the argument array
     * @param registry  the ConstantRegistry that represents all of the constants used by the code
     *                  containing the op
     */
    protected static void registerArguments(Argument[] aArg, ConstantRegistry registry)
        {
        if (aArg != null)
            {
            for (Argument arg : aArg)
                {
                registerArgument(arg, registry);
                }
            }
        }

    /**
     * Convert the specified argument to an index that will be used in the persistent form of the op
     * to identify the argument.
     *
     * @param arg       the argument
     * @param registry  the ConstantRegistry that represents all of the constants used by the code
     *                  containing the op
     *
     * @return the index of the argument
     */
    protected static int encodeArgument(Argument arg, ConstantRegistry registry)
        {
        return arg instanceof Constant
                ? Op.CONSTANT_OFFSET - ((Constant) arg).getPosition()   // TODO eventually: registry.indexOf((Constant) arg)
                : ((Register) arg).getIndex();
        }

    /**
     * Convert the specified argument array to an an array if indexes that will be used in the
     * persistent form of the op to identify the arguments.
     *
     * @param aArg      the argument array
     * @param registry  the ConstantRegistry that represents all of the constants used by the code
     *                  containing the op
     *
     * @return the array of the arguments' indexes
     */
    protected static int[] encodeArguments(Argument[] aArg, ConstantRegistry registry)
        {
        int c = aArg.length;
        int[] anArg = new int[c];
        for (int i = 0; i < c; i++)
            {
            anArg[i] = encodeArgument(aArg[i], registry);
            }
        return anArg;
        }

    /**
     * Determine if the specified ObjectHandle represents a PropertyHandle.
     *
     * @param handle  the argument
     *
     * @return true iff the argument is a PropertyHandle
     */
    protected static boolean isProperty(ObjectHandle handle)
        {
        return handle instanceof xProperty.PropertyHandle;
        }

    /**
     * Determine if any of the handles is a PropertyHandle.
     *
     * @param aHandle  an array of handles
     *
     * @return true iff any of the handles is a PropertyHandle
     */
    protected static boolean anyProperty(ObjectHandle[] aHandle)
        {
        for (ObjectHandle h : aHandle)
            {
            if (h instanceof xProperty.PropertyHandle)
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Calculate a relative address from the specified program counter (iPC) to the
     * specified destination op-code.
     *
     * @param code    the Code
     * @param iPC     the "current" program counter
     * @param opDest  the destination Op
     *
     * @return the offset from the current PC to the destination Op
     */
    protected static int resolveAddress(MethodStructure.Code code, int iPC, Op opDest)
        {
        assert (opDest != null);

        int iPCThat = code.addressOf(opDest);
        if (iPCThat < 0)
            {
            throw new IllegalStateException("cannot find op: " + opDest);
            }

        // calculate relative offset
        int ofJmp = iPCThat - iPC;
        if (ofJmp == 0)
            {
            throw new IllegalStateException("infinite loop: code=" + code + "; PC=" + iPC);
            }
        return ofJmp;
        }

    /**
     * Convert a relative const id into an absolute one.
     */
    protected static int convertId(int id)
        {
        assert id < 0;
        return CONSTANT_OFFSET - id;
        }

    // ----- inner class: Prefix Op ----------------------------------------------------------------

    /**
     * An Op that can act as a Prefix to another op must implement the Prefix interface.
     */
    public static abstract class Prefix
            extends Op
        {
        // ----- Prefix methods ---------------------------------------------------------------

        /**
         * @return the op that this op is a prefix for, which may itself be a prefix op, or may be
         *         null if no op has been appended
         */
        public Op getNextOp()
            {
            return m_op;
            }

        /**
         * @return the op that this op is a prefix for, or throw an exception if there is none
         */
        public Op ensureNextOp()
            {
            final Op opNext = m_op;
            if (opNext == null)
                {
                throw new IllegalStateException("prefix requires a suffix");
                }
            return opNext;
            }

        /**
         * @return the non-prefix op that is at the end of the linked list of prefix ops, or null
         *         if there is no non-prefix op
         */
        public Op getSuffix()
            {
            Op op = m_op;
            return op instanceof Prefix
                    ? ((Prefix) op).getSuffix()
                    : op;
            }

        /**
         * Append an op to this prefix op.
         *
         * @param op  the op to append to this op
         */
        public void append(Op op)
            {
            if (m_op == null)
                {
                m_op = op;
                }
            else if (op instanceof Prefix)
                {
                ((Prefix) op).append(op);
                }
            else
                {
                throw new IllegalStateException("");
                }
            }

        // ----- Op methods -------------------------------------------------------------------

        @Override
        public void write(DataOutput out, ConstantRegistry registry)
                throws IOException
            {
            ensureNextOp().write(out, registry);
            }

        @Override
        public int getOpCode()
            {
            return ensureNextOp().getOpCode();
            }

        @Override
        public int process(Frame frame, int iPC)
            {
            return ensureNextOp().process(frame, iPC);
            }

        @Override
        public void simulate(Scope scope)
            {
            ensureNextOp().simulate(scope);
            }

        @Override
        public void registerConstants(ConstantRegistry registry)
            {
            ensureNextOp().registerConstants(registry);
            }

        @Override
        public void resolveAddress(Code code, int iPC)
            {
            ensureNextOp().resolveAddress(code, iPC);
            }

        @Override
        public boolean contains(Op that)
            {
            return this == that || m_op != null && m_op.contains(that);
            }

        // ----- fields -----------------------------------------------------------------------

        /**
         * The "next" op that this op acts as a prefix for. Note that the next op may itself be a
         * prefix op, so this can act as a linked list.
         */
        private Op m_op;
        }


    // ----- inner class: ConstantRegistry ---------------------------------------------------------

    /**
     * Kind of like a ConstantPool, but this is local to a MethodStructure, and just collects
     * together all of the constants used by the ops.
     */
    public static class ConstantRegistry
        {
        /**
         * Construct a ConstantRegistry.
         *
         * @param pool  the underlying ConstantPool
         */
        public ConstantRegistry(ConstantPool pool)
            {
            m_pool = pool;
            }

        /**
         * Register the specified constant. This is called once by each op for each constant that
         * the op refers to.
         *
         * @param constant  the constant to register
         *
         * @return the constant reference to use (may be different from the passed constant)
         */
        public Constant register(Constant constant)
            {
            ensureRegistering();

            if (constant == null)
                {
                return null;
                }

            constant = m_pool.register(constant);

            // keep track of how many times each constant is registered
            m_mapConstants.compute(constant, (k, count) -> count == null ? 1 : 1 + count);

            return constant;
            }

        /**
         * @return the array of constants, in optimized order
         */
        public Constant[] getConstantArray()
            {
            ensureOptimized();

            return m_aconst;
            }

        /**
         * Obtain the index of the specified constant, as it appears in the optimized order.
         *
         * @param constant  the constant to get the index of
         *
         * @return the index of the constant in the array returned by {@link #getConstantArray()}
         */
        public int indexOf(Constant constant)
            {
            ensureOptimized();

            Integer index = m_mapConstants.get(constant);
            if (index == null)
                {
                throw new IllegalArgumentException("missing constant: " + constant);
                }
            return index;
            }

        /**
         * Internal: Make sure this ConstantRegistry is still being used to register constants.
         */
        private void ensureRegistering()
            {
            if (m_aconst != null)
                {
                throw new IllegalStateException("constants are no longer being registered");
                }
            }

        /**
         * Internal: Make sure this ConstantRegistry is no longer being used to register constants,
         * and has settled on an optimized order for the constants that were registered.
         */
        private void ensureOptimized()
            {
            if (m_aconst == null)
                {
                // first, create an array of all of the constants, sorted by how often they are used
                // (backwards order, such that the most often used come first, since the variable
                // length index encoding uses less bytes for lower indexes, the result is more
                // compact)
                Map<Constant, Integer> mapConstants = m_mapConstants;
                Constant[] aconst = mapConstants.keySet().toArray(new Constant[mapConstants.size()]);
                Arrays.sort(aconst, Constants.DEBUG
                        ? Comparator.<Constant>naturalOrder()
                        : (o1, o2) -> mapConstants.get(o2) - mapConstants.get(o1));
                m_aconst = aconst;

                // now re-use the map of constants to point to the constant indexes, for when we
                // need to look up index by constant
                for (int i = 0, c = aconst.length; i < c; ++i)
                    {
                    mapConstants.put(aconst[i], i);
                    }
                }
            }

        /**
         * The underlying ConstantPool.
         */
        private ConstantPool m_pool;

        /**
         * The constants registered in the ConstantRegistry. While the registry is still
         * registering, the map tracks counts of how many times each constant is referenced. Once
         * the registry optimizes its constant ordering, the map holds the index of each constant
         * (in the local "constant pool", not the real constant pool.)
         */
        private Map<Constant, Integer> m_mapConstants = new HashMap<>();

        /**
         * The array of constants in their optimized order.
         */
        private Constant[] m_aconst;
        }


    // ----- static helpers ------------------------------------------------------------------------

    /**
     * Read the ops for a particular method.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     *
     * @return an array of ops
     *
     * @throws IOException  if an error occurs reading the ops
     */
    public static Op[] readOps(DataInput in, Constant[] aconst)
            throws IOException
        {
        int cOps = readMagnitude(in);
        Op[] aop = new Op[cOps];
        for (int i = 0; i < cOps; ++i)
            {
            aop[i] = instantiate(in.readUnsignedByte(), in, aconst);
            ;
            }

        return aop;
        }

    /**
     * Instantiate an Op object for the specified op code.
     *
     * @param nOp     the op-code
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     *
     * @return the new op instance
     */
    public static Op instantiate(int nOp, DataInput in, Constant[] aconst)
            throws IOException
        {
        switch (nOp)
            {
            case OP_NOP:         return new Nop         ();
            case OP_LINE_1:      return new Nop         (1);
            case OP_LINE_2:      return new Nop         (2);
            case OP_LINE_3:      return new Nop         (3);
            case OP_LINE_N:      return new Nop         (in, aconst);
            case OP_BREAK:       return new Break       ();
            case OP_ENTER:       return new Enter       ();
            case OP_EXIT:        return new Exit        ();
            case OP_GUARD:       return new GuardStart  (in, aconst);
            case OP_GUARD_END:   return new GuardEnd    (in, aconst);
            case OP_CATCH:       return new CatchStart  ();
            case OP_CATCH_END:   return new CatchEnd    (in, aconst);
            case OP_GUARD_ALL:   return new GuardAll    (in, aconst);
            case OP_FINALLY:     return new FinallyStart();
            case OP_FINALLY_END: return new FinallyEnd  ();
            case OP_THROW:       return new Throw       (in, aconst);

            case OP_ASSERT:      return new Assert      (in, aconst);
            case OP_ASSERT_M:    return new AssertM     (in, aconst);
            case OP_ASSERT_V:    return new AssertV     (in, aconst);

            case OP_RETURN_0:    return new Return_0    (in, aconst);
            case OP_RETURN_1:    return new Return_1    (in, aconst);
            case OP_RETURN_N:    return new Return_N    (in, aconst);
            case OP_RETURN_T:    return new Return_T    (in, aconst);

            case OP_MBIND:       return new MBind       (in, aconst);
            case OP_FBIND:       return new FBind       (in, aconst);

            case OP_JMP:         return new Jump        (in, aconst);
            case OP_JMP_TRUE:    return new JumpTrue    (in, aconst);
            case OP_JMP_FALSE:   return new JumpFalse   (in, aconst);
            case OP_JMP_ZERO:    return new JumpZero    (in, aconst);
            case OP_JMP_NZERO:   return new JumpNotZero (in, aconst);
            case OP_JMP_NULL:    return new JumpNull    (in, aconst);
            case OP_JMP_NNULL:   return new JumpNotNull (in, aconst);
            case OP_JMP_EQ:      return new JumpEq      (in, aconst);
            case OP_JMP_NEQ:     return new JumpNotEq   (in, aconst);
            case OP_JMP_LT:      return new JumpLt      (in, aconst);
            case OP_JMP_LTE:     return new JumpLte     (in, aconst);
            case OP_JMP_GT:      return new JumpGt      (in, aconst);
            case OP_JMP_GTE:     return new JumpGte     (in, aconst);

            case OP_IS_ZERO:     return new IsZero      (in, aconst);
            case OP_IS_NZERO:    return new IsNotZero   (in, aconst);
            case OP_IS_NULL:     return new IsNull      (in, aconst);
            case OP_IS_NNULL:    return new IsNotNull   (in, aconst);
            case OP_IS_EQ:       return new IsEq        (in, aconst);
            case OP_IS_NEQ:      return new IsNotEq     (in, aconst);
            case OP_IS_LT:       return new IsLt        (in, aconst);
            case OP_IS_LTE:      return new IsLte       (in, aconst);
            case OP_IS_GT:       return new IsGt        (in, aconst);
            case OP_IS_GTE:      return new IsGte       (in, aconst);
            case OP_IS_NOT:      return new IsNot       (in, aconst);

            case OP_VAR:         return new Var         (in, aconst);
            case OP_VAR_I:       return new Var_I       (in, aconst);
            case OP_VAR_N:       return new Var_N       (in, aconst);
            case OP_VAR_IN:      return new Var_IN      (in, aconst);
            case OP_VAR_D:       return new Var_D       (in, aconst);
            case OP_VAR_DN:      return new Var_DN      (in, aconst);
            case OP_VAR_S:       return new Var_S       (in, aconst);
            case OP_VAR_SN:      return new Var_SN      (in, aconst);
            case OP_VAR_T:       return new Var_T       (in, aconst);
            case OP_VAR_TN:      return new Var_TN      (in, aconst);

            case OP_MOV:         return new Move        (in, aconst);
            case OP_REF:         return new MoveRef     (in, aconst);
            case OP_CAST:        return new MoveCast    (in, aconst);

            case OP_GP_ADD:      return new GP_Add      (in, aconst);
            case OP_GP_NEG:      return new GP_Neg      (in, aconst);

            case OP_IP_INC:      return new IP_Inc      (in, aconst);
            case OP_IP_INCA:     return new IP_PostInc  (in, aconst);
            case OP_IP_INCB:     return new IP_PreInc   (in, aconst);

            case OP_L_GET:       return new L_Get       (in, aconst);
            case OP_L_SET:       return new L_Set       (in, aconst);
            case OP_P_GET:       return new P_Get       (in, aconst);
            case OP_P_SET:       return new P_Set       (in, aconst);

            case OP_PIP_INCA:    return new PIP_PostInc (in, aconst);
            case OP_PIP_INCB:    return new PIP_PreInc  (in, aconst);

            case OP_I_GET:       return new I_Get       (in, aconst);
            case OP_I_SET:       return new I_Set       (in, aconst);
            case OP_I_REF:       return new I_Ref       (in, aconst);

            case OP_CALL_00:     return new Call_00     (in, aconst);
            case OP_CALL_01:     return new Call_01     (in, aconst);
            case OP_CALL_0N:     return new Call_0N     (in, aconst);
            case OP_CALL_0T:     return new Call_0T     (in, aconst);
            case OP_CALL_10:     return new Call_10     (in, aconst);
            case OP_CALL_11:     return new Call_11     (in, aconst);
            case OP_CALL_1N:     return new Call_1N     (in, aconst);
            case OP_CALL_1T:     return new Call_1T     (in, aconst);
            case OP_CALL_N0:     return new Call_N0     (in, aconst);
            case OP_CALL_N1:     return new Call_N1     (in, aconst);
            case OP_CALL_NN:     return new Call_NN     (in, aconst);
            case OP_CALL_NT:     return new Call_NT     (in, aconst);
            case OP_CALL_T0:     return new Call_T0     (in, aconst);
            case OP_CALL_T1:     return new Call_T1     (in, aconst);
            case OP_CALL_TN:     return new Call_TN     (in, aconst);
            case OP_CALL_TT:     return new Call_TT     (in, aconst);

            case OP_NVOK_00:     return new Invoke_00   (in, aconst);
            case OP_NVOK_01:     return new Invoke_01   (in, aconst);
            case OP_NVOK_0N:     return new Invoke_0N   (in, aconst);
            case OP_NVOK_0T:     return new Invoke_0T   (in, aconst);
            case OP_NVOK_10:     return new Invoke_10   (in, aconst);
            case OP_NVOK_11:     return new Invoke_11   (in, aconst);
            case OP_NVOK_1N:     return new Invoke_1N   (in, aconst);
            case OP_NVOK_1T:     return new Invoke_1T   (in, aconst);
            case OP_NVOK_N0:     return new Invoke_N0   (in, aconst);
            case OP_NVOK_N1:     return new Invoke_N1   (in, aconst);
            case OP_NVOK_NN:     return new Invoke_NN   (in, aconst);
            case OP_NVOK_NT:     return new Invoke_NT   (in, aconst);
            case OP_NVOK_T0:     return new Invoke_T0   (in, aconst);
            case OP_NVOK_T1:     return new Invoke_T1   (in, aconst);
            case OP_NVOK_TN:     return new Invoke_TN   (in, aconst);
            case OP_NVOK_TT:     return new Invoke_TT   (in, aconst);

            case OP_NEW_1:       return new New_1       (in, aconst);
            case OP_NEW_N:       return new New_N       (in, aconst);
            case OP_NEWG_0:      return new NewG_0      (in, aconst);
            case OP_NEWG_1:      return new NewG_1      (in, aconst);
            case OP_NEWG_N:      return new NewG_N      (in, aconst);

            case OP_CONSTR_0:    return new Construct_0 (in, aconst);
            case OP_CONSTR_1:    return new Construct_1 (in, aconst);
            case OP_CONSTR_N:    return new Construct_N (in, aconst);
            case OP_CONSTR_T:    return new Construct_T (in, aconst);

            default:
                throw new IllegalStateException("op=" + byteToHexString(nOp));
            }
        }

    /**
     * Read an array of packed integers from the provided stream.
     *
     * @param in  the DataInput to read from
     *
     * @return an array of integers
     *
     * @throws IOException  if an error occurs reading the array
     */
    public static int[] readIntArray(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);

        int[] ai = new int[c];
        for (int i = 0; i < c; ++i)
            {
            ai[i] = readPackedInt(in);
            }
        return ai;
        }

    /**
     * Write an array of integers to the provided stream in a packed format.
     *
     * @param out  the DataOutput to write to
     * @param ai   the array of integers to write
     *
     * @throws IOException  if an error occurs writing the array
     */
    public static void writeIntArray(DataOutput out, int[] ai)
            throws IOException
        {
        int c = ai.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, ai[i]);
            }
        }


    // ----- op-codes ------------------------------------------------------------------------------

    public static final int OP_NOP          = 0x00;
    public static final int OP_LINE_1       = 0x01;
    public static final int OP_LINE_2       = 0x02;
    public static final int OP_LINE_3       = 0x03;
    public static final int OP_LINE_N       = 0x04;
    public static final int OP_BREAK        = 0x05;
    public static final int OP_ENTER        = 0x06;
    public static final int OP_EXIT         = 0x07;
    public static final int OP_GUARD        = 0x08;
    public static final int OP_GUARD_END    = 0x09;
    public static final int OP_CATCH        = 0x0A;
    public static final int OP_CATCH_END    = 0x0B;
    public static final int OP_GUARD_ALL    = 0x0C;
    public static final int OP_FINALLY      = 0x0D;
    public static final int OP_FINALLY_END  = 0x0E;
    public static final int OP_THROW        = 0x0F;

    public static final int OP_ASSERT       = 0x10;
    public static final int OP_ASSERT_M     = 0x11;
    public static final int OP_ASSERT_V     = 0x12;

    public static final int OP_RETURN_0     = 0x13;
    public static final int OP_RETURN_1     = 0x14;
    public static final int OP_RETURN_N     = 0x15;
    public static final int OP_RETURN_T     = 0x16;

    public static final int OP_MBIND        = 0x17;
    public static final int OP_FBIND        = 0x18;

    public static final int OP_RTYPE_1      = 0x19;
    public static final int OP_RTYPE_N      = 0x1A;

    public static final int OP_JMP          = 0x1B;
    public static final int OP_JMP_TRUE     = 0x1C;
    public static final int OP_JMP_FALSE    = 0x1D;
    public static final int OP_JMP_ZERO     = 0x1E;
    public static final int OP_JMP_NZERO    = 0x1F;
    public static final int OP_JMP_NULL     = 0x20;
    public static final int OP_JMP_NNULL    = 0x21;
    public static final int OP_JMP_EQ       = 0x22;
    public static final int OP_JMP_NEQ      = 0x23;
    public static final int OP_JMP_LT       = 0x24;
    public static final int OP_JMP_LTE      = 0x25;
    public static final int OP_JMP_GT       = 0x26;
    public static final int OP_JMP_GTE      = 0x27;
    public static final int OP_JMP_TYPE     = 0x28;
    public static final int OP_JMP_NTYPE    = 0x29;
    public static final int OP_JMP_COND     = 0x2A;
    public static final int OP_JMP_NCOND    = 0x2B;
    public static final int OP_JMP_INT      = 0x2C;
    public static final int OP_JMP_VAL      = 0x2D;

    public static final int OP_IS_ZERO      = 0x30;
    public static final int OP_IS_NZERO     = 0x31;
    public static final int OP_IS_NULL      = 0x32;
    public static final int OP_IS_NNULL     = 0x33;
    public static final int OP_IS_EQ        = 0x34;
    public static final int OP_IS_NEQ       = 0x35;
    public static final int OP_IS_LT        = 0x36;
    public static final int OP_IS_LTE       = 0x37;
    public static final int OP_IS_GT        = 0x38;
    public static final int OP_IS_GTE       = 0x39;
    public static final int OP_IS_NOT       = 0x3A;
    public static final int OP_IS_TYPE      = 0x3B;
    public static final int OP_IS_NTYPE     = 0x3C;
    public static final int OP_IS_SVC       = 0x3D;
    public static final int OP_IS_CONST     = 0x3E;
    public static final int OP_IS_IMMT      = 0x3F;

    public static final int OP_VAR          = 0x40;
    public static final int OP_VAR_I        = 0x41;
    public static final int OP_VAR_N        = 0x42;
    public static final int OP_VAR_IN       = 0x43;
    public static final int OP_VAR_D        = 0x44;
    public static final int OP_VAR_DN       = 0x45;
    public static final int OP_VAR_S        = 0x46;
    public static final int OP_VAR_SN       = 0x47;
    public static final int OP_VAR_T        = 0x48;
    public static final int OP_VAR_TN       = 0x49;
    public static final int OP_MOV          = 0x4A;
    public static final int OP_REF          = 0x4B;
    public static final int OP_CAST         = 0x4C;

    public static final int OP_GP_ADD       = 0x50;
    public static final int OP_GP_SUB       = 0x51;
    public static final int OP_GP_MUL       = 0x52;
    public static final int OP_GP_DIV       = 0x53;
    public static final int OP_GP_MOD       = 0x54;
    public static final int OP_GP_SHL       = 0x55;
    public static final int OP_GP_SHR       = 0x56;
    public static final int OP_GP_USHR      = 0x57;
    public static final int OP_GP_AND       = 0x58;
    public static final int OP_GP_OR        = 0x59;
    public static final int OP_GP_XOR       = 0x5A;
    public static final int OP_GP_DIVMOD    = 0x5B;
    public static final int OP_GP_NEG       = 0x5C;
    public static final int OP_GP_COMPL     = 0x5E;

    public static final int OP_L_GET        = 0x60;
    public static final int OP_L_SET        = 0x61;
    public static final int OP_P_GET        = 0x62;
    public static final int OP_P_SET        = 0x63;
    public static final int OP_P_REF        = 0x64;

    public static final int OP_IP_INC       = 0x65;
    public static final int OP_IP_DEC       = 0x66;
    public static final int OP_IP_INCA      = 0x67;
    public static final int OP_IP_DECA      = 0x68;
    public static final int OP_IP_INCB      = 0x69;
    public static final int OP_IP_DECB      = 0x6A;
    public static final int OP_IP_ADD       = 0x6B;
    public static final int OP_IP_SUB       = 0x6C;
    public static final int OP_IP_MUL       = 0x6D;
    public static final int OP_IP_DIV       = 0x6E;
    public static final int OP_IP_MOD       = 0x6F;
    public static final int OP_IP_SHL       = 0x70;
    public static final int OP_IP_SHR       = 0x71;
    public static final int OP_IP_USHR      = 0x72;
    public static final int OP_IP_AND       = 0x73;
    public static final int OP_IP_OR        = 0x74;
    public static final int OP_IP_XOR       = 0x75;

    public static final int OP_PIP_INC      = 0x76;
    public static final int OP_PIP_DEC      = 0x77;
    public static final int OP_PIP_INCA     = 0x78;
    public static final int OP_PIP_DECA     = 0x79;
    public static final int OP_PIP_INCB     = 0x7A;
    public static final int OP_PIP_DECB     = 0x7B;
    public static final int OP_PIP_ADD      = 0x7C;
    public static final int OP_PIP_SUB      = 0x7D;
    public static final int OP_PIP_MUL      = 0x7E;
    public static final int OP_PIP_DIV      = 0x7F;
    public static final int OP_PIP_MOD      = 0x80;
    public static final int OP_PIP_SHL      = 0x81;
    public static final int OP_PIP_SHR      = 0x82;
    public static final int OP_PIP_USHR     = 0x83;
    public static final int OP_PIP_AND      = 0x84;
    public static final int OP_PIP_OR       = 0x85;
    public static final int OP_PIP_XOR      = 0x86;

    public static final int OP_I_GET        = 0x87;
    public static final int OP_I_SET        = 0x88;
    public static final int OP_I_REF        = 0x89;
    public static final int OP_IIP_INC      = 0x8A;
    public static final int OP_IIP_DEC      = 0x8B;
    public static final int OP_IIP_INCA     = 0x8C;
    public static final int OP_IIP_DECA     = 0x8D;
    public static final int OP_IIP_INCB     = 0x8E;
    public static final int OP_IIP_DECB     = 0x8F;
    public static final int OP_IIP_ADD      = 0x90;
    public static final int OP_IIP_SUB      = 0x91;
    public static final int OP_IIP_MUL      = 0x92;
    public static final int OP_IIP_DIV      = 0x93;
    public static final int OP_IIP_MOD      = 0x94;
    public static final int OP_IIP_SHL      = 0x95;
    public static final int OP_IIP_SHR      = 0x96;
    public static final int OP_IIP_USHR     = 0x97;
    public static final int OP_IIP_AND      = 0x98;
    public static final int OP_IIP_OR       = 0x99;
    public static final int OP_IIP_XOR      = 0x9A;

    public static final int OP_M_GET        = 0x9B;
    public static final int OP_M_SET        = 0x9C;
    public static final int OP_M_REF        = 0x9D;
    public static final int OP_MIP_INC      = 0x9E;
    public static final int OP_MIP_DEC      = 0x9F;
    public static final int OP_MIP_INCA     = 0xA0;
    public static final int OP_MIP_DECA     = 0xA1;
    public static final int OP_MIP_INCB     = 0xA2;
    public static final int OP_MIP_DECB     = 0xA3;
    public static final int OP_MIP_ADD      = 0xA4;
    public static final int OP_MIP_SUB      = 0xA5;
    public static final int OP_MIP_MUL      = 0xA6;
    public static final int OP_MIP_DIV      = 0xA7;
    public static final int OP_MIP_MOD      = 0xA8;
    public static final int OP_MIP_SHL      = 0xA9;
    public static final int OP_MIP_SHR      = 0xAA;
    public static final int OP_MIP_USHR     = 0xAB;
    public static final int OP_MIP_AND      = 0xAC;
    public static final int OP_MIP_OR       = 0xAD;
    public static final int OP_MIP_XOR      = 0xAE;

    public static final int OP_CALL_00      = 0xB0;
    public static final int OP_CALL_01      = 0xB1;
    public static final int OP_CALL_0N      = 0xB2;
    public static final int OP_CALL_0T      = 0xB3;
    public static final int OP_CALL_10      = 0xB4;
    public static final int OP_CALL_11      = 0xB5;
    public static final int OP_CALL_1N      = 0xB6;
    public static final int OP_CALL_1T      = 0xB7;
    public static final int OP_CALL_N0      = 0xB8;
    public static final int OP_CALL_N1      = 0xB9;
    public static final int OP_CALL_NN      = 0xBA;
    public static final int OP_CALL_NT      = 0xBB;
    public static final int OP_CALL_T0      = 0xBC;
    public static final int OP_CALL_T1      = 0xBD;
    public static final int OP_CALL_TN      = 0xBE;
    public static final int OP_CALL_TT      = 0xBF;

    public static final int OP_NVOK_00      = 0xC0;
    public static final int OP_NVOK_01      = 0xC1;
    public static final int OP_NVOK_0N      = 0xC2;
    public static final int OP_NVOK_0T      = 0xC3;
    public static final int OP_NVOK_10      = 0xC4;
    public static final int OP_NVOK_11      = 0xC5;
    public static final int OP_NVOK_1N      = 0xC6;
    public static final int OP_NVOK_1T      = 0xC7;
    public static final int OP_NVOK_N0      = 0xC8;
    public static final int OP_NVOK_N1      = 0xC9;
    public static final int OP_NVOK_NN      = 0xCA;
    public static final int OP_NVOK_NT      = 0xCB;
    public static final int OP_NVOK_T0      = 0xCC;
    public static final int OP_NVOK_T1      = 0xCD;
    public static final int OP_NVOK_TN      = 0xCE;
    public static final int OP_NVOK_TT      = 0xCF;

    public static final int OP_NEW_0        = 0xD0;
    public static final int OP_NEW_1        = 0xD1;
    public static final int OP_NEW_N        = 0xD2;
    public static final int OP_NEW_T        = 0xD3;
    public static final int OP_NEWG_0       = 0xD4;
    public static final int OP_NEWG_1       = 0xD5;
    public static final int OP_NEWG_N       = 0xD6;
    public static final int OP_NEWG_T       = 0xD7;
    public static final int OP_NEWC_0       = 0xD8;
    public static final int OP_NEWC_1       = 0xD9;
    public static final int OP_NEWC_N       = 0xDA;
    public static final int OP_NEWC_T       = 0xDB;
    public static final int OP_NEWCG_0      = 0xDC;
    public static final int OP_NEWCG_1      = 0xDD;
    public static final int OP_NEWCG_N      = 0xDE;
    public static final int OP_NEWCG_T      = 0xDF;

    public static final int OP_CONSTR_0     = 0xE0;
    public static final int OP_CONSTR_1     = 0xE1;
    public static final int OP_CONSTR_N     = 0xE2;
    public static final int OP_CONSTR_T     = 0xE3;

    public static final int OP_X_PRINT      = 0xFF;


    // ----- pre-defined arguments -----------------------------------------------------------------

    /**
     * Pre-defined argument: a write-only "black hole" register, akin to {@code /dev/null}
     */
    public static final int A_IGNORE = -1;

    /**
     * Pre-defined argument: {@code this:public}
     */
    public static final int A_PUBLIC = -2;

    /**
     * Pre-defined argument: {@code this:protected}
     */
    public static final int A_PROTECTED = -3;

    /**
     * Pre-defined argument: {@code this:private}
     */
    public static final int A_PRIVATE = -4;

    /**
     * Pre-defined argument: {@code this:target}
     */
    public static final int A_TARGET = -5;

    /**
     * Pre-defined argument: {@code this:struct}
     */
    public static final int A_STRUCT = -6;

    /**
     * Pre-defined argument: {@code this:frame}
     */
    public static final int A_FRAME = -7;

    /**
     * Pre-defined argument: {@code this:service}
     */
    public static final int A_SERVICE = -8;

    /**
     * Pre-defined argument: {@code this:module}
     */
    public static final int A_MODULE = -9;

    /**
     * Pre-defined argument: {@code this:type}
     */
    public static final int A_TYPE = -10;

    /**
     * Pre-defined argument: {@code super} (function).
     */
    public static final int A_SUPER = -11;


    // ----- return values from the Op.process() method --------------------------------------------

    /**
     * Result from process() method: execute the next op-code.
     */
    public static final int R_NEXT = -1;

    /**
     * Result from process() method: resume the previous frame execution.
     */
    public static final int R_RETURN = -2;

    /**
     * Result from process() method: process the exception placed in frame.m_hException.
     */
    public static final int R_EXCEPTION = -3;

    /**
     * Result from process() method: process the exception raised during return.
     */
    public static final int R_RETURN_EXCEPTION = -4;

    /**
     * Result from process() method: call the frame placed in frame.m_frameNext.
     */
    public static final int R_CALL = -5;

    /**
     * Result from process() method: some registers are not ready for a read; yield and repeat the
     * same op-code.
     */
    public static final int R_REPEAT = -6;

    /**
     * Result from process() method: some assignments were deferred; yield and check the "waiting"
     * registers before executing the next op-code.
     */
    public static final int R_BLOCK = -7;

    /**
     * Result from process() method: some assignments were deferred; yield and check the "waiting"
     * registers before returning.
     */
    public static final int R_BLOCK_RETURN = -8;

    /**
     * Result from process() method: yield before executing the next op-code.
     */
    public static final int R_YIELD = -9;


    // ----- other constants -----------------------------------------------------------------------

    /**
     * An empty array of ops.
     */
    public static final Op[] NO_OPS = new Op[0];

    /**
     * The first constant, constant #0, is at this index (which is a negative). For a constant whose
     * index is {@code i}, it is encoded as: {@code CONSTANT_OFFSET - i}
     */
    public static final int CONSTANT_OFFSET = -17;

    /**
     * A stub for an op-code.
     */
    public static final Op[] STUB = new Op[] {Return_0.INSTANCE};
    }