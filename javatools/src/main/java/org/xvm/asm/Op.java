package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Component.Format;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.*;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

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
     * After the op has been disassembled, it is given an opportunity to finish its deserialization
     * process by resolving against the remainder of the disassembled ops.
     *
     * @param code    the code containing this op
     * @param aconst  the local constants used by the code
     */
    public void resolveCode(Code code, Constant[] aconst)
        {
        }

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
     * @return the actual "op" for this address (skipping over any labels or other prefix ops)
     */
    public Op ensureOp()
        {
        return this;
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
     * Stores the address and the scope depth; also resets the reachability and redundant flags.
     *
     * @param nAddress        the program counter ("iPC") for this op
     * @param nDepth          the scope depth for this op (one-based)
     * @param nGuardDepth     the Guard depth (zero-based)
     * @param nGuardAllDepth  the GuardAll depth (zero-based)
     */
    public void initInfo(int nAddress, int nDepth, int nGuardDepth, int nGuardAllDepth)
        {
        assert nAddress >= 0;
        assert nAddress <= POSITION_BITS;
        assert nDepth > 0;
        assert nDepth <= (SCOPE_DEPTH_BITS >>> SCOPE_DEPTH_SHIFT);
        assert nGuardDepth <= (GUARD_DEPTH_BITS >>> GUARD_DEPTH_SHIFT);
        assert nGuardAllDepth <= (GUARD_ALL_DEPTH_BITS >>> GUARD_ALL_DEPTH_SHIFT);

        m_lStruct =    (long) nAddress
                    | ((long) nDepth         << SCOPE_DEPTH_SHIFT)
                    | ((long) nGuardDepth    << GUARD_DEPTH_SHIFT)
                    | ((long) nGuardAllDepth << GUARD_ALL_DEPTH_SHIFT);
        }

    /**
     * @return the address (iPC) of the op
     */
    public int getAddress()
        {
        return (int) (m_lStruct & POSITION_BITS);
        }

    /**
     * @return the scope depth of the op (one-based)
     */
    public int getDepth()
        {
        return (int) ((m_lStruct & SCOPE_DEPTH_BITS) >>> SCOPE_DEPTH_SHIFT);
        }

    /**
     * @return the Guard depth of the op (zero-based)
     */
    public int getGuardDepth()
        {
        return (int) ((m_lStruct & GUARD_DEPTH_BITS) >>> GUARD_DEPTH_SHIFT);
        }

    /**
     * @return the GuardAll depth of the op (zero-based)
     */
    public int getGuardAllDepth()
        {
        return (int) ((m_lStruct & GUARD_ALL_DEPTH_BITS) >>> GUARD_ALL_DEPTH_SHIFT);
        }

    /**
     * @return true iff the op has been determined to be reachable
     */
    public boolean isReachable()
        {
        return (m_lStruct & REACHABLE_BIT) != 0;
        }

    /**
     * Mark the op as reachable.
     *
     * @param aop  the ops of the current method
     */
    public void markReachable(Op[] aop)
        {
        m_lStruct |= REACHABLE_BIT;
        }

    /**
     * @return true iff the op has been determined to be necessary to keep, even if it's not
     *         reachable
     */
    public boolean isNecessary()
        {
        return (m_lStruct & NECESSARY_BIT) != 0;
        }

    /**
     * Mark the op as necessary.
     */
    public void markNecessary()
        {
        m_lStruct |= NECESSARY_BIT;
        }

    /**
     * @return true iff the op cannot be reached AND should be discarded as a result
     */
    public boolean isDiscardable()
        {
        return !isReachable() && !isNecessary();
        }

    /**
     * @return true iff the op has been determined to be redundant (removable)
     */
    public boolean isRedundant()
        {
        return (m_lStruct & REDUNDANT_BIT) != 0;
        }

    public void markRedundant()
        {
        m_lStruct |= REDUNDANT_BIT;
        }

    /**
     * Determine if this op is redundant, and if it is, mark itself as such.
     *
     * @param aop  the ops of the current method
     *
     * @return true if this op determined that it is redundant
     */
    public boolean checkRedundant(Op[] aop)
        {
        return false;
        }

    /**
     * Find the destination op given a relative jump offset.
     *
     * @param aop    the ops of this method
     * @param ofJmp  the distance to jump from this op
     *
     * @return the destination op
     */
    public Op findDestinationOp(Op[] aop, int ofJmp)
        {
        int iPC = getAddress() + ofJmp;
        Op  opPrefix, opActual;
        while (true)
            {
            opPrefix = aop[iPC];
            opActual = opPrefix.ensureOp();

            assert opActual != this;

            if (opActual instanceof Jump)
                {
                iPC += ((Jump) opActual).getRelativeAddress();
                }
            else
                {
                // make sure that the jump lands on a label; this is not strictly necessary, but
                // some of the data structures were built assuming the label type
                if (!(opPrefix instanceof Label))
                    {
                    aop[iPC] = opPrefix = new Label().append(opPrefix);
                    }

                return opPrefix;
                }
            }
        }

    /**
     * Find the "closing" op that corresponds to this op.
     *
     * @param aop    the ops of this method
     * @param nThat  the "closing" op-code
     *
     * @return the "closing" op that corresponds to this op
     */
    public Op findCorrespondingOp(Op[] aop, int nThat)
        {
        return findFirstUnmatchedOp(aop, getOpCode(), nThat);
        }

    /**
     * Find the first unmatched "closing" op that corresponds to the "opening" op, ignoring
     * any matched pairs.
     *
     * @param aop     the ops of this method
     * @param nOpen   the "opening" op-code
     * @param nClose  the "closing" op-code
     *
     * @return the "closing" op that corresponds to "opening" op
     */
    public Op findFirstUnmatchedOp(Op[] aop, int nOpen, int nClose)
        {
        int iPC  = getAddress();
        int cReq = 1;
        while (true)
            {
            Op  op  = aop[++iPC];
            int nOp = op.ensureOp().getOpCode();
            if (nOp == nOpen)
                {
                ++cReq;
                }
            else if (nOp == nClose)
                {
                if (--cReq == 0)
                    {
                    return op;
                    }
                }
            }
        }

    /**
     * @return a "pass-through" op that removes this op from the resulting assembly
     */
    public Prefix convertToPrefix()
        {
        // this is used to convert a redundant op to a label of sorts, so that anything that jumps
        // to the op will still be able to find it
        assert isRedundant();

        return new Redundant(this);
        }

    /**
     * Prepare to simulate the effects of the op on the passed scope.
     */
    public void resetSimulation()
        {
        }

    /**
     * Simulate the effects of the op on the passed scope.
     *
     * @param scope  the variable scope
     */
    public void simulate(Scope scope)
        {
        }

    /**
     * Determine if the op branches.
     *
     * @param aop   the ops of this method
     * @param list  a list to put the branches (relative addresses) into, if there are any
     *
     * @return true if the op branches, and those branches have been added to the passed list
     */
    public boolean branches(Op[] aop, List<Integer> list)
        {
        return false;
        }

    /**
     * @return true iff the op can advance to the following op
     */
    public boolean advances()
        {
        return true;
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
     * Before the op is assembled, it is given an opportunity to determine relative addresses to
     * other ops.
     *
     * @param aop  the ops of the current method
     */
    public void resolveAddresses(Op[] aop)
        {
        }

    /**
     * Get an op at a relative address from this Op.
     *
     * @param aop  an array of ops
     * @param of   the relative offset
     *
     * @return the Op or the very last op if the destination op has been eliminated
     */
    protected Op calcRelativeOp(Op[] aop, int of)
        {
        try
            {
            return aop[getAddress() + of];
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            return aop[aop.length - 1];
            }
        }

    /**
     * Calculate a relative address from this Op to the specified destination Op.
     *
     * @param opDest  the destination Op
     *
     * @return the offset from this Op's address (iPC) to the destination Op
     */
    protected int calcRelativeAddress(Op opDest)
        {
        return opDest.getAddress() - this.getAddress();
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
     * @return true iff this op is using the {@link #A_SUPER} argument
     */
    public boolean usesSuper()
        {
        return false;
        }

    /**
     * @return true iff this op represents an explicit or implicit ENTER
     */
    public boolean isEnter()
        {
        return false;
        }

    /**
     * @return true iff this op represents an explicit or implicit EXIT
     */
    public boolean isExit()
        {
        return false;
        }

    /**
     * Calculate a number of scopes that must be exited by a jump from this Op to another.
     *
     * @param opDest  the op to jump to
     *
     * @return the number of scopes to exit
     */
    protected int calcExits(Op opDest)
        {
        return Math.abs(opDest.getDepth() - getDepth() - (isEnter() ? 1 : 0) + (isExit() ? 1 : 0));
        }

    @Override
    public String toString()
        {
        return toName(getOpCode());
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
        public Prefix append(Op op)
            {
            assert op != this;

            if (m_op == null)
                {
                m_op = op;
                }
            else if (m_op instanceof Prefix)
                {
                ((Prefix) m_op).append(op);
                }
            else
                {
                throw new IllegalStateException();
                }

            return this;
            }

        // ----- Op methods -------------------------------------------------------------------

        @Override
        public void resolveCode(Code code, Constant[] aconst)
            {
            m_op.resolveCode(code, aconst);
            }

        @Override
        public void write(DataOutput out, ConstantRegistry registry)
                throws IOException
            {
            m_op.write(out, registry);
            }

        @Override
        public Op ensureOp()
            {
            if (m_op == null)
                {
                throw new IllegalStateException("prefix requires a suffix: " + this.toString());
                }
            return m_op.ensureOp();
            }

        @Override
        public int getOpCode()
            {
            return m_op.getOpCode();
            }

        @Override
        public int process(Frame frame, int iPC)
            {
            return m_op.process(frame, iPC);
            }

        @Override
        public void initInfo(int nAddress, int nDepth, int nGuardDepth, int nGuardAllDepth)
            {
            super.initInfo(nAddress, nDepth, nGuardDepth, nGuardAllDepth);
            m_op .initInfo(nAddress, nDepth, nGuardDepth, nGuardAllDepth);
            }

        @Override
        public int getAddress()
            {
            return m_op.getAddress();
            }

        @Override
        public int getDepth()
            {
            return m_op.getDepth();
            }

        @Override
        public boolean isReachable()
            {
            return m_op.isReachable();
            }

        @Override
        public void markReachable(Op[] aop)
            {
            super.markReachable(aop);
            m_op.markReachable(aop);
            }

        @Override
        public boolean isNecessary()
            {
            return m_op.isNecessary();
            }

        @Override
        public void markNecessary()
            {
            super.markNecessary();
            m_op.markNecessary();
            }

        @Override
        public boolean isDiscardable()
            {
            return m_op.isDiscardable();
            }

        @Override
        public boolean isRedundant()
            {
            return m_op.isRedundant();
            }

        @Override
        public void markRedundant()
            {
            super.markRedundant();
            m_op.markRedundant();
            }

        @Override
        public boolean checkRedundant(Op[] aop)
            {
            boolean fRedundant = m_op.checkRedundant(aop);
            if (fRedundant)
                {
                super.markRedundant();
                }
            return fRedundant;
            }

        @Override
        public Prefix convertToPrefix()
            {
            if (m_op != null)
                {
                m_op = m_op.convertToPrefix();
                }
            return this;
            }

        @Override
        public void resetSimulation()
            {
            m_op.resetSimulation();
            }

        @Override
        public void simulate(Scope scope)
            {
            m_op.simulate(scope);
            }

        @Override
        public boolean branches(Op[] aop, List<Integer> list)
            {
            return m_op.branches(aop, list);
            }

        @Override
        public boolean advances()
            {
            return m_op.advances();
            }

        @Override
        public void registerConstants(ConstantRegistry registry)
            {
            m_op.registerConstants(registry);
            }

        @Override
        public void resolveAddresses(Op[] aop)
            {
            m_op.resolveAddresses(aop);
            }

        @Override
        public boolean contains(Op that)
            {
            return this == that || m_op != null && m_op.contains(that);
            }

        @Override
        public boolean usesSuper()
            {
            return m_op != null && m_op.usesSuper();
            }

        @Override
        public boolean isEnter()
            {
            return m_op != null && m_op.isEnter();
            }

        @Override
        public boolean isExit()
            {
            return m_op != null && m_op.isExit();
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

            if (constant.containsUnresolved())
                {
                throw new IllegalStateException("Unresolved constant: " + constant);
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
        private synchronized void ensureOptimized()
            {
            if (m_aconst == null)
                {
                // first, create an array of all of the constants, sorted by how often they are used
                // (backwards order, such that the most often used come first, since the variable
                // length index encoding uses less bytes for lower indexes, the result is more
                // compact)
                Map<Constant, Integer> mapConstants = m_mapConstants;
                Constant[] aconst = mapConstants.keySet().toArray(Constant.NO_CONSTS);
                Arrays.sort(aconst, Constants.DEBUG
                        ? Comparator.naturalOrder()
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
     * Check if the specified Argument is a register, and reset its index if it is.
     *
     * @param arg  the argument
     */
    public static void resetRegister(Argument arg)
        {
        if (arg instanceof Register)
            {
            ((Register) arg).resetIndex();
            }
        }

    /**
     * Check if any of the specified Arguments is a register, and for each such, reset its index.
     *
     * @param aArg   the argument array
     */
    public static void resetRegisters(Argument[] aArg)
        {
        if (aArg != null)
            {
            for (Argument arg : aArg)
                {
                if (arg instanceof Register)
                    {
                    ((Register) arg).resetIndex();
                    }
                }
            }
        }

    /**
     * Check if the specified Argument represents "the next available" register and if
     * so, assign its index to the next allocated variable.
     *
     * @param scope  the scope
     * @param arg    the argument
     * @param nArg   the argument's id
     */
    public static void checkNextRegister(Scope scope, Argument arg, int nArg)
        {
        if (arg == null)
            {
            // this means we have just loaded the ops from disk
            scope.ensureVar(nArg);
            }
        else if (arg instanceof Register && ((Register) arg).isUnknown())
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
     * @param anArg  the array of argument ids
     */
    public static void checkNextRegisters(Scope scope, Argument[] aArg, int[] anArg)
        {
        if (aArg == null)
            {
            // this means we have just loaded the ops from disk
            for (int nArg : anArg)
                {
                scope.ensureVar(nArg);
                }
            }
        else
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
    protected static Argument registerArgument(Argument arg, ConstantRegistry registry)
        {
        return arg == null ? null : arg.registerConstants(registry);
        }

    /**
     * Register the specified arguments if they are constants.
     *
     * @param aArg      the argument array
     * @param registry  the ConstantRegistry that represents all of the constants used by the code
     */
    protected static void registerArguments(Argument[] aArg, ConstantRegistry registry)
        {
        if (aArg != null)
            {
            for (int i = 0, c = aArg.length; i < c; i++)
                {
                aArg[i] = registerArgument(aArg[i], registry);
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
                ? Op.CONSTANT_OFFSET - registry.indexOf((Constant) arg)
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
     * Determine if the specified ObjectHandle represents a deferred action such as a property
     * access or a method call.
     *
     * @param handle  the argument
     *
     * @return true iff the argument represents a deferred action
     */
    public static boolean isDeferred(ObjectHandle handle)
        {
        return handle instanceof DeferredCallHandle;
        }

    /**
     * @return true iff any of the specified handles represents a deferred action
     */
    public static boolean anyDeferred(ObjectHandle[] aHandle)
        {
        for (ObjectHandle h : aHandle)
            {
            if (h == null)
                {
                // we reached the "tail"
                return false;
                }
            if (h instanceof DeferredCallHandle)
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Helper method for "jump"-ing op codes.
     *
     * @param frame   the current frame
     * @param iPCTo   the program counter to jump to
     * @param cExits  the number of scopes to exit
     *
     * @return the next program counter
     */
    protected static int jump(Frame frame, int iPCTo, int cExits)
        {
        while (cExits-- > 0)
            {
            frame.exitScope();
            }
        return iPCTo;
        }

    /**
     * Convert a relative const id into an absolute one.
     */
    protected static int convertId(int id)
        {
        assert id < 0;
        return CONSTANT_OFFSET - id;
        }

    /**
     * Given two types that should have some point of immediate commonality, select a target type.
     *
     * @param type1  the first type
     * @param type2  the second type
     * @param errs   the error listener
     *
     * @return a target type or null
     */
    public static TypeConstant selectCommonType(TypeConstant type1, TypeConstant type2, ErrorListener errs)
        {
        if (type1 == null && type2 == null)
            {
            return null;
            }

        if (type1 != null && type2 != null)
            {
            if (type2.isAssignableTo(type1))
                {
                return type1;
                }

            if (type1.isAssignableTo(type2))
                {
                return type2;
                }

            if (type1.isTypeOfType() && type2.isTypeOfType())
                {
                // types are always comparable
                return type1.getConstantPool().typeType();
                }

            TypeInfo info1 = type1.ensureTypeInfo(errs);
            if (info1.getFormat() == Format.ENUMVALUE && type2.isAssignableTo(info1.getExtends()))
                {
                return info1.getExtends();
                }

            TypeInfo info2 = type2.ensureTypeInfo(errs);
            if (info2.getFormat() == Format.ENUMVALUE && type1.isAssignableTo(info2.getExtends()))
                {
                return info2.getExtends();
                }

            return null;
            }

        TypeConstant typeResult = type1 == null ? type2 : type1;
        TypeInfo     typeinfo   = typeResult.ensureTypeInfo(errs);
        return typeinfo.getFormat() == Format.ENUMVALUE
                ? typeinfo.getExtends()
                : typeResult;
        }

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
            case OP_ENTER:       return new Enter       ();
            case OP_EXIT:        return new Exit        ();
            case OP_GUARD:       return new GuardStart  (in, aconst);
            case OP_GUARD_END:   return new GuardEnd    (in, aconst);
            case OP_CATCH:       return new CatchStart  (in, aconst);
            case OP_CATCH_END:   return new CatchEnd    (in, aconst);
            case OP_GUARD_ALL:   return new GuardAll    (in, aconst);
            case OP_FINALLY:     return new FinallyStart(in, aconst);
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
            case OP_JMP_TYPE:    return new JumpType    (in, aconst);
            case OP_JMP_NTYPE:   return new JumpNType   (in, aconst);
            case OP_JMP_COND:    return new JumpCond    (in, aconst);
            case OP_JMP_NCOND:   return new JumpNCond   (in, aconst);
            case OP_JMP_NFIRST:  return new JumpNFirst  (in, aconst);
            case OP_JMP_NSAMPLE: return new JumpNSample (in, aconst);
            case OP_JMP_INT:     return new JumpInt     (in, aconst);
            case OP_JMP_VAL:     return new JumpVal     (in, aconst);
            case OP_JMP_VAL_N:   return new JumpVal_N   (in, aconst);

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
            case OP_IS_TYPE:     return new IsType      (in, aconst);
            case OP_IS_NTYPE:    return new IsNType     (in, aconst);
            case OP_IS_SVC:      return new IsService   (in, aconst);
            case OP_IS_CONST:    return new IsConst     (in, aconst);
            case OP_IS_IMMT:     return new IsImmutable (in, aconst);
            case OP_CMP:         return new Cmp         (in, aconst);

            case OP_VAR:         return new Var         (in, aconst);
            case OP_VAR_I:       return new Var_I       (in, aconst);
            case OP_VAR_N:       return new Var_N       (in, aconst);
            case OP_VAR_IN:      return new Var_IN      (in, aconst);
            case OP_VAR_D:       return new Var_D       (in, aconst);
            case OP_VAR_DN:      return new Var_DN      (in, aconst);
            case OP_VAR_C:       return new Var_C       (in, aconst);
            case OP_VAR_CN:      return new Var_CN      (in, aconst);
            case OP_VAR_S:       return new Var_S       (in, aconst);
            case OP_VAR_SN:      return new Var_SN      (in, aconst);
            case OP_VAR_T:       return new Var_T       (in, aconst);
            case OP_VAR_TN:      return new Var_TN      (in, aconst);
            case OP_VAR_M:       return new Var_M       (in, aconst);
            case OP_VAR_MN:      return new Var_MN      (in, aconst);

            case OP_MOV:         return new Move        (in, aconst);
            case OP_MOV_VAR:     return new MoveVar     (in, aconst);
            case OP_MOV_REF:     return new MoveRef     (in, aconst);
            case OP_MOV_THIS:    return new MoveThis    (in, aconst, nOp);
            case OP_MOV_THIS_A:  return new MoveThis    (in, aconst, nOp);
            case OP_MOV_TYPE:    return new MoveType    (in, aconst);
            case OP_CAST:        return new MoveCast    (in, aconst);

            case OP_GP_ADD:      return new GP_Add      (in, aconst);
            case OP_GP_SUB:      return new GP_Sub      (in, aconst);
            case OP_GP_MUL:      return new GP_Mul      (in, aconst);
            case OP_GP_DIV:      return new GP_Div      (in, aconst);
            case OP_GP_MOD:      return new GP_Mod      (in, aconst);
            case OP_GP_SHL:      return new GP_Shl      (in, aconst);
            case OP_GP_SHR:      return new GP_Shr      (in, aconst);
            case OP_GP_USHR:     return new GP_ShrAll   (in, aconst);
            case OP_GP_AND:      return new GP_And      (in, aconst);
            case OP_GP_OR:       return new GP_Or       (in, aconst);
            case OP_GP_XOR:      return new GP_Xor      (in, aconst);
            case OP_GP_DIVREM:   return new GP_DivRem   (in, aconst);
            case OP_GP_DOTDOT:   return new GP_DotDot   (in, aconst);
            case OP_GP_DOTDOTEX: return new GP_DotDotEx (in, aconst);
            case OP_GP_NEG:      return new GP_Neg      (in, aconst);
            case OP_GP_COMPL:    return new GP_Compl    (in, aconst);

            case OP_IP_INC:      return new IP_Inc      (in, aconst);
            case OP_IP_DEC:      return new IP_Dec      (in, aconst);
            case OP_IP_INCA:     return new IP_PostInc  (in, aconst);
            case OP_IP_DECA:     return new IP_PostDec  (in, aconst);
            case OP_IP_INCB:     return new IP_PreInc   (in, aconst);
            case OP_IP_DECB:     return new IP_PreDec   (in, aconst);

            case OP_IP_ADD:      return new IP_Add      (in, aconst);
            case OP_IP_SUB:      return new IP_Sub      (in, aconst);
            case OP_IP_MUL:      return new IP_Mul      (in, aconst);
            case OP_IP_DIV:      return new IP_Div      (in, aconst);
            case OP_IP_MOD:      return new IP_Mod      (in, aconst);

            case OP_IP_SHL:      return new IP_Shl      (in, aconst);
            case OP_IP_SHR:      return new IP_Shr      (in, aconst);
            case OP_IP_USHR:     return new IP_ShrAll   (in, aconst);
            case OP_IP_AND:      return new IP_And      (in, aconst);
            case OP_IP_OR:       return new IP_Or       (in, aconst);
            case OP_IP_XOR:      return new IP_Xor      (in, aconst);

            case OP_L_GET:       return new L_Get       (in, aconst);
            case OP_L_SET:       return new L_Set       (in, aconst);
            case OP_P_GET:       return new P_Get       (in, aconst);
            case OP_P_SET:       return new P_Set       (in, aconst);
            case OP_P_VAR:       return new P_Var       (in, aconst);
            case OP_P_REF:       return new P_Ref       (in, aconst);

            case OP_PIP_INC:     return new PIP_Inc     (in, aconst);
            case OP_PIP_DEC:     return new PIP_Dec     (in, aconst);
            case OP_PIP_INCA:    return new PIP_PostInc (in, aconst);
            case OP_PIP_DECA:    return new PIP_PostDec (in, aconst);
            case OP_PIP_INCB:    return new PIP_PreInc  (in, aconst);
            case OP_PIP_DECB:    return new PIP_PreDec  (in, aconst);

            case OP_PIP_ADD:     return new PIP_Add     (in, aconst);
            case OP_PIP_SUB:     return new PIP_Sub     (in, aconst);

            case OP_I_GET:       return new I_Get       (in, aconst);
            case OP_I_SET:       return new I_Set       (in, aconst);

            case OP_IIP_INC:     return new IIP_Inc     (in, aconst);
            case OP_IIP_DEC:     return new IIP_Dec     (in, aconst);
            case OP_IIP_INCA:    return new IIP_PostInc (in, aconst);
            case OP_IIP_INCB:    return new IIP_PreInc  (in, aconst);
            case OP_IIP_DECA:    return new IIP_PostDec (in, aconst);
            case OP_IIP_DECB:    return new IIP_PreDec  (in, aconst);
            case OP_IIP_ADD:     return new IIP_Add     (in, aconst);
            case OP_IIP_SUB:     return new IIP_Sub     (in, aconst);
            case OP_IIP_MUL:     return new IIP_Mul     (in, aconst);
            case OP_IIP_DIV:     return new IIP_Div     (in, aconst);

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

            case OP_NEW_0:       return new New_0       (in, aconst);
            case OP_NEW_1:       return new New_1       (in, aconst);
            case OP_NEW_N:       return new New_N       (in, aconst);
            case OP_NEW_T:       return new New_T       (in, aconst);
            case OP_NEWG_0:      return new NewG_0      (in, aconst);
            case OP_NEWG_1:      return new NewG_1      (in, aconst);
            case OP_NEWG_N:      return new NewG_N      (in, aconst);
            case OP_NEWG_T:      return new NewG_T      (in, aconst);
            case OP_NEWC_0:      return new NewC_0      (in, aconst);
            case OP_NEWC_1:      return new NewC_1      (in, aconst);
            case OP_NEWC_N:      return new NewC_N      (in, aconst);
            case OP_NEWCG_0:     return new NewCG_0     (in, aconst);
            case OP_NEWCG_1:     return new NewCG_1     (in, aconst);
            case OP_NEWCG_N:     return new NewCG_N     (in, aconst);
            case OP_NEWV_0:      return new NewV_0      (in, aconst);
            case OP_NEWV_1:      return new NewV_1      (in, aconst);
            case OP_NEWV_N:      return new NewV_N      (in, aconst);

            case OP_CONSTR_0:    return new Construct_0 (in, aconst);
            case OP_CONSTR_1:    return new Construct_1 (in, aconst);
            case OP_CONSTR_N:    return new Construct_N (in, aconst);
            case OP_CONSTR_T:    return new Construct_T (in, aconst);
            case OP_SYN_INIT:    return new SynInit     ();

            default:
                throw new IllegalStateException("op=" + byteToHexString(nOp));
            }
        }

    /**
     * Obtain the name of a given op code.
     *
     * @param nOp  the op-code
     *
     * @return the op name
     */
    public static String toName(int nOp)
        {
        switch (nOp)
            {
            case OP_NOP:         return "NOP";
            case OP_LINE_1:      return "LINE_1";
            case OP_LINE_2:      return "LINE_2";
            case OP_LINE_3:      return "LINE_3";
            case OP_LINE_N:      return "LINE_N";
            case OP_ENTER:       return "ENTER";
            case OP_EXIT:        return "EXIT";
            case OP_GUARD:       return "GUARD";
            case OP_GUARD_END:   return "GUARD_E";
            case OP_CATCH:       return "CATCH";
            case OP_CATCH_END:   return "CATCH_E";
            case OP_GUARD_ALL:   return "GUARD_ALL";
            case OP_FINALLY:     return "FINALLY";
            case OP_FINALLY_END: return "FINALLY_E";
            case OP_THROW:       return "THROW";
            case OP_ASSERT:      return "ASSERT";
            case OP_ASSERT_M:    return "ASSERT_M";
            case OP_ASSERT_V:    return "ASSERT_V";
            case OP_RETURN_0:    return "RETURN_0";
            case OP_RETURN_1:    return "RETURN_1";
            case OP_RETURN_N:    return "RETURN_N";
            case OP_RETURN_T:    return "RETURN_T";
            case OP_MBIND:       return "BIND_M";
            case OP_FBIND:       return "BIND_F";
            case OP_JMP:         return "JMP";
            case OP_JMP_TRUE:    return "JMP_TRUE";
            case OP_JMP_FALSE:   return "JMP_FALSE";
            case OP_JMP_ZERO:    return "JMP_ZERO";
            case OP_JMP_NZERO:   return "JMP_NZERO";
            case OP_JMP_NULL:    return "JMP_NULL";
            case OP_JMP_NNULL:   return "JMP_NNULL";
            case OP_JMP_EQ:      return "JMP_EQ";
            case OP_JMP_NEQ:     return "JMP_NEQ";
            case OP_JMP_LT:      return "JMP_LT";
            case OP_JMP_LTE:     return "JMP_LTE";
            case OP_JMP_GT:      return "JMP_GT";
            case OP_JMP_GTE:     return "JMP_GTE";
            case OP_JMP_TYPE:    return "JMP_TYPE";
            case OP_JMP_NTYPE:   return "JMP_NTYPE";
            case OP_JMP_COND:    return "JMP_COND";
            case OP_JMP_NCOND:   return "JMP_NCOND";
            case OP_JMP_NFIRST:  return "JMP_NFIRST";
            case OP_JMP_NSAMPLE: return "JMP_NSAMPLE";
            case OP_JMP_INT:     return "JMP_INT";
            case OP_JMP_VAL:     return "JMP_VAL";
            case OP_JMP_VAL_N:   return "JMP_VAL_N";
            case OP_IS_ZERO:     return "IS_ZERO";
            case OP_IS_NZERO:    return "IS_NZERO";
            case OP_IS_NULL:     return "IS_NULL";
            case OP_IS_NNULL:    return "IS_NNULL";
            case OP_IS_EQ:       return "IS_EQ";
            case OP_IS_NEQ:      return "IS_NEQ";
            case OP_IS_LT:       return "IS_LT";
            case OP_IS_LTE:      return "IS_LTE";
            case OP_IS_GT:       return "IS_GT";
            case OP_IS_GTE:      return "IS_GTE";
            case OP_IS_NOT:      return "IS_NOT";
            case OP_IS_TYPE:     return "IS_TYPE";
            case OP_IS_NTYPE:    return "IS_NTYPE";
            case OP_IS_SVC:      return "IS_SVC";
            case OP_IS_CONST:    return "IS_CONST";
            case OP_IS_IMMT:     return "IS_IMMUT";
            case OP_CMP:         return "CMP";
            case OP_VAR:         return "VAR";
            case OP_VAR_I:       return "VAR_I";
            case OP_VAR_N:       return "VAR_N";
            case OP_VAR_IN:      return "VAR_IN";
            case OP_VAR_D:       return "VAR_D";
            case OP_VAR_DN:      return "VAR_DN";
            case OP_VAR_C:       return "VAR_C";
            case OP_VAR_CN:      return "VAR_CN";
            case OP_VAR_S:       return "VAR_S";
            case OP_VAR_SN:      return "VAR_SN";
            case OP_VAR_T:       return "VAR_T";
            case OP_VAR_TN:      return "VAR_TN";
            case OP_VAR_M:       return "VAR_M";
            case OP_VAR_MN:      return "VAR_MN";
            case OP_MOV:         return "MOV";
            case OP_MOV_VAR:     return "MOV_VAR";
            case OP_MOV_REF:     return "MOV_REF";
            case OP_MOV_THIS:    return "MOV_THIS";
            case OP_MOV_THIS_A:  return "MOV_THIS_A";
            case OP_MOV_TYPE:    return "MOV_TYPE";
            case OP_CAST:        return "CAST";
            case OP_GP_ADD:      return "GP_ADD";
            case OP_GP_SUB:      return "GP_SUB";
            case OP_GP_MUL:      return "GP_MUL";
            case OP_GP_DIV:      return "GP_DIV";
            case OP_GP_MOD:      return "GP_MOD";
            case OP_GP_SHL:      return "GP_SHL";
            case OP_GP_SHR:      return "GP_SHR";
            case OP_GP_USHR:     return "GP_USHR";
            case OP_GP_AND:      return "GP_AND";
            case OP_GP_OR:       return "GP_OR";
            case OP_GP_XOR:      return "GP_XOR";
            case OP_GP_DIVREM:   return "GP_DIVREM";
            case OP_GP_DOTDOT:   return "GP_DOTDOT";
            case OP_GP_DOTDOTEX: return "GP_DOTDOTEX";
            case OP_GP_NEG:      return "GP_NEG";
            case OP_GP_COMPL:    return "GP_COMPL";
            case OP_L_GET:       return "L_GET";
            case OP_L_SET:       return "L_SET";
            case OP_P_GET:       return "P_GET";
            case OP_P_SET:       return "P_SET";
            case OP_P_VAR:       return "P_VAR";
            case OP_P_REF:       return "P_REF";
            case OP_IP_INC:      return "IP_INC";
            case OP_IP_DEC:      return "IP_DEC";
            case OP_IP_INCA:     return "IP_INCA";
            case OP_IP_DECA:     return "IP_DECA";
            case OP_IP_INCB:     return "IP_INCB";
            case OP_IP_DECB:     return "IP_DECB";
            case OP_IP_ADD:      return "IP_ADD";
            case OP_IP_SUB:      return "IP_SUB";
            case OP_IP_MUL:      return "IP_MUL";
            case OP_IP_DIV:      return "IP_DIV";
            case OP_IP_MOD:      return "IP_MOD";
            case OP_IP_SHL:      return "IP_SHL";
            case OP_IP_SHR:      return "IP_SHR";
            case OP_IP_USHR:     return "IP_USHR";
            case OP_IP_AND:      return "IP_AND";
            case OP_IP_OR:       return "IP_OR";
            case OP_IP_XOR:      return "IP_XOR";
            case OP_PIP_INC:     return "PIP_INC";
            case OP_PIP_DEC:     return "PIP_DEC";
            case OP_PIP_INCA:    return "PIP_INCA";
            case OP_PIP_DECA:    return "PIP_DECA";
            case OP_PIP_INCB:    return "PIP_INCB";
            case OP_PIP_DECB:    return "PIP_DECB";
            case OP_PIP_ADD:     return "PIP_ADD";
            case OP_PIP_SUB:     return "PIP_SUB";
            case OP_I_GET:       return "I_GET";
            case OP_I_SET:       return "I_SET";
            case OP_IIP_INC:     return "IIP_INC";
            case OP_IIP_DEC:     return "IIP_DEC";
            case OP_IIP_INCB:    return "IIP_INCB";
            case OP_IIP_INCA:    return "IIP_INCA";
            case OP_IIP_DECB:    return "IIP_DECB";
            case OP_IIP_DECA:    return "IIP_DECA";
            case OP_IIP_ADD:     return "IIP_ADD";
            case OP_IIP_SUB:     return "IIP_SUB";
            case OP_IIP_MUL:     return "IIP_MUL";
            case OP_IIP_DIV:     return "IIP_DIV";
            case OP_CALL_00:     return "CALL_00";
            case OP_CALL_01:     return "CALL_01";
            case OP_CALL_0N:     return "CALL_0N";
            case OP_CALL_0T:     return "CALL_0T";
            case OP_CALL_10:     return "CALL_10";
            case OP_CALL_11:     return "CALL_11";
            case OP_CALL_1N:     return "CALL_1N";
            case OP_CALL_1T:     return "CALL_1T";
            case OP_CALL_N0:     return "CALL_N0";
            case OP_CALL_N1:     return "CALL_N1";
            case OP_CALL_NN:     return "CALL_NN";
            case OP_CALL_NT:     return "CALL_NT";
            case OP_CALL_T0:     return "CALL_T0";
            case OP_CALL_T1:     return "CALL_T1";
            case OP_CALL_TN:     return "CALL_TN";
            case OP_CALL_TT:     return "CALL_TT";
            case OP_NVOK_00:     return "NVOK_00";
            case OP_NVOK_01:     return "NVOK_01";
            case OP_NVOK_0N:     return "NVOK_0N";
            case OP_NVOK_0T:     return "NVOK_0T";
            case OP_NVOK_10:     return "NVOK_10";
            case OP_NVOK_11:     return "NVOK_11";
            case OP_NVOK_1N:     return "NVOK_1N";
            case OP_NVOK_1T:     return "NVOK_1T";
            case OP_NVOK_N0:     return "NVOK_N0";
            case OP_NVOK_N1:     return "NVOK_N1";
            case OP_NVOK_NN:     return "NVOK_NN";
            case OP_NVOK_NT:     return "NVOK_NT";
            case OP_NVOK_T0:     return "NVOK_T0";
            case OP_NVOK_T1:     return "NVOK_T1";
            case OP_NVOK_TN:     return "NVOK_TN";
            case OP_NVOK_TT:     return "NVOK_TT";
            case OP_NEW_0:       return "NEW_0";
            case OP_NEW_1:       return "NEW_1";
            case OP_NEW_N:       return "NEW_N";
            case OP_NEW_T:       return "NEW_T";
            case OP_NEWG_0:      return "NEWG_0";
            case OP_NEWG_1:      return "NEWG_1";
            case OP_NEWG_N:      return "NEWG_N";
            case OP_NEWG_T:      return "NEWG_T";
            case OP_NEWC_0:      return "NEWC_0";
            case OP_NEWC_1:      return "NEWC_1";
            case OP_NEWC_N:      return "NEWC_N";
            case OP_NEWC_T:      return "NEWC_T";
            case OP_NEWCG_0:     return "NEWCG_0";
            case OP_NEWCG_1:     return "NEWCG_1";
            case OP_NEWCG_N:     return "NEWCG_N";
            case OP_NEWCG_T:     return "NEWCG_T";
            case OP_NEWV_0:      return "NEWV_0";
            case OP_NEWV_1:      return "NEWV_1";
            case OP_NEWV_N:      return "NEWV_N";
            case OP_CONSTR_0:    return "CONSTR_0";
            case OP_CONSTR_1:    return "CONSTR_1";
            case OP_CONSTR_N:    return "CONSTR_N";
            case OP_CONSTR_T:    return "CONSTR_T";
            case OP_SYN_INIT:    return "SYN_INIT";

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
    public static final int OP_ENTER        = 0x05;
    public static final int OP_EXIT         = 0x06;
    public static final int OP_GUARD        = 0x07;
    public static final int OP_GUARD_END    = 0x08;
    public static final int OP_CATCH        = 0x09;
    public static final int OP_CATCH_END    = 0x0A;
    public static final int OP_GUARD_ALL    = 0x0B;
    public static final int OP_FINALLY      = 0x0C;
    public static final int OP_FINALLY_END  = 0x0D;
    public static final int OP_THROW        = 0x0E;
    public static final int OP_RSVD_0F      = 0x0F;

    public static final int OP_CALL_00      = 0x10;
    public static final int OP_CALL_01      = 0x11;
    public static final int OP_CALL_0N      = 0x12;
    public static final int OP_CALL_0T      = 0x13;
    public static final int OP_CALL_10      = 0x14;
    public static final int OP_CALL_11      = 0x15;
    public static final int OP_CALL_1N      = 0x16;
    public static final int OP_CALL_1T      = 0x17;
    public static final int OP_CALL_N0      = 0x18;
    public static final int OP_CALL_N1      = 0x19;
    public static final int OP_CALL_NN      = 0x1A;
    public static final int OP_CALL_NT      = 0x1B;
    public static final int OP_CALL_T0      = 0x1C;
    public static final int OP_CALL_T1      = 0x1D;
    public static final int OP_CALL_TN      = 0x1E;
    public static final int OP_CALL_TT      = 0x1F;

    public static final int OP_NVOK_00      = 0x20;
    public static final int OP_NVOK_01      = 0x21;
    public static final int OP_NVOK_0N      = 0x22;
    public static final int OP_NVOK_0T      = 0x23;
    public static final int OP_NVOK_10      = 0x24;
    public static final int OP_NVOK_11      = 0x25;
    public static final int OP_NVOK_1N      = 0x26;
    public static final int OP_NVOK_1T      = 0x27;
    public static final int OP_NVOK_N0      = 0x28;
    public static final int OP_NVOK_N1      = 0x29;
    public static final int OP_NVOK_NN      = 0x2A;
    public static final int OP_NVOK_NT      = 0x2B;
    public static final int OP_NVOK_T0      = 0x2C;
    public static final int OP_NVOK_T1      = 0x2D;
    public static final int OP_NVOK_TN      = 0x2E;
    public static final int OP_NVOK_TT      = 0x2F;

    public static final int OP_MBIND        = 0x30;
    public static final int OP_FBIND        = 0x31;
    public static final int OP_RSVD_32      = 0x32;
    public static final int OP_SYN_INIT     = 0x33;
    public static final int OP_CONSTR_0     = 0x34;
    public static final int OP_CONSTR_1     = 0x35;
    public static final int OP_CONSTR_N     = 0x36;
    public static final int OP_CONSTR_T     = 0x37;

    public static final int OP_NEW_0        = 0x38;
    public static final int OP_NEW_1        = 0x39;
    public static final int OP_NEW_N        = 0x3A;
    public static final int OP_NEW_T        = 0x3B;
    public static final int OP_NEWG_0       = 0x3C;
    public static final int OP_NEWG_1       = 0x3D;
    public static final int OP_NEWG_N       = 0x3E;
    public static final int OP_NEWG_T       = 0x3F;
    public static final int OP_NEWC_0       = 0x40;
    public static final int OP_NEWC_1       = 0x41;
    public static final int OP_NEWC_N       = 0x42;
    public static final int OP_NEWC_T       = 0x43;
    public static final int OP_NEWCG_0      = 0x44;
    public static final int OP_NEWCG_1      = 0x45;
    public static final int OP_NEWCG_N      = 0x46;
    public static final int OP_NEWCG_T      = 0x47;
    public static final int OP_NEWV_0       = 0x48;
    public static final int OP_NEWV_1       = 0x49;
    public static final int OP_NEWV_N       = 0x4A;
    public static final int OP_NEWV_T       = 0x4B;

    public static final int OP_RETURN_0     = 0x4C;
    public static final int OP_RETURN_1     = 0x4D;
    public static final int OP_RETURN_N     = 0x4E;
    public static final int OP_RETURN_T     = 0x4F;

    public static final int OP_VAR          = 0x50;
    public static final int OP_VAR_I        = 0x51;
    public static final int OP_VAR_N        = 0x52;
    public static final int OP_VAR_IN       = 0x53;
    public static final int OP_VAR_D        = 0x54;
    public static final int OP_VAR_DN       = 0x55;
    public static final int OP_VAR_C        = 0x56;
    public static final int OP_VAR_CN       = 0x57;
    public static final int OP_VAR_S        = 0x58;
    public static final int OP_VAR_SN       = 0x59;
    public static final int OP_VAR_T        = 0x5A;
    public static final int OP_VAR_TN       = 0x5B;
    public static final int OP_VAR_M        = 0x5C;
    public static final int OP_VAR_MN       = 0x5E;

    public static final int OP_MOV          = 0x60;
    public static final int OP_MOV_VAR      = 0x61;
    public static final int OP_MOV_REF      = 0x62;
    public static final int OP_MOV_THIS     = 0x63;
    public static final int OP_MOV_THIS_A   = 0x64;
    public static final int OP_MOV_TYPE     = 0x65;
    public static final int OP_CAST         = 0x67;

    public static final int OP_CMP          = 0x68;
    public static final int OP_IS_ZERO      = 0x69;
    public static final int OP_IS_NZERO     = 0x6A;
    public static final int OP_IS_NULL      = 0x6B;
    public static final int OP_IS_NNULL     = 0x6C;
    public static final int OP_IS_EQ        = 0x6D;
    public static final int OP_IS_NEQ       = 0x6E;
    public static final int OP_IS_LT        = 0x6F;
    public static final int OP_IS_LTE       = 0x70;
    public static final int OP_IS_GT        = 0x71;
    public static final int OP_IS_GTE       = 0x72;
    public static final int OP_IS_NOT       = 0x73;
    public static final int OP_IS_TYPE      = 0x74;
    public static final int OP_IS_NTYPE     = 0x75;
    public static final int OP_IS_SVC       = 0x76;
    public static final int OP_IS_CONST     = 0x77;
    public static final int OP_IS_IMMT      = 0x78;

    public static final int OP_JMP          = 0x79;
    public static final int OP_JMP_TRUE     = 0x7A;
    public static final int OP_JMP_FALSE    = 0x7B;
    public static final int OP_JMP_ZERO     = 0x7C;
    public static final int OP_JMP_NZERO    = 0x7D;
    public static final int OP_JMP_NULL     = 0x7E;
    public static final int OP_JMP_NNULL    = 0x7F;
    public static final int OP_JMP_EQ       = 0x80;
    public static final int OP_JMP_NEQ      = 0x81;
    public static final int OP_JMP_LT       = 0x82;
    public static final int OP_JMP_LTE      = 0x83;
    public static final int OP_JMP_GT       = 0x84;
    public static final int OP_JMP_GTE      = 0x85;
    public static final int OP_JMP_TYPE     = 0x86;
    public static final int OP_JMP_NTYPE    = 0x87;
    public static final int OP_JMP_COND     = 0x88;
    public static final int OP_JMP_NCOND    = 0x89;
    public static final int OP_JMP_NFIRST   = 0x8A;
    public static final int OP_JMP_NSAMPLE  = 0x8B;
    public static final int OP_JMP_INT      = 0x8C;
    public static final int OP_JMP_VAL      = 0x8D;
    public static final int OP_JMP_VAL_N    = 0x8E;

    public static final int OP_ASSERT       = 0x8F;
    public static final int OP_ASSERT_M     = 0x90;
    public static final int OP_ASSERT_V     = 0x91;

    public static final int OP_GP_ADD       = 0x92;
    public static final int OP_GP_SUB       = 0x93;
    public static final int OP_GP_MUL       = 0x94;
    public static final int OP_GP_DIV       = 0x95;
    public static final int OP_GP_MOD       = 0x96;
    public static final int OP_GP_SHL       = 0x97;
    public static final int OP_GP_SHR       = 0x98;
    public static final int OP_GP_USHR      = 0x99;
    public static final int OP_GP_AND       = 0x9A;
    public static final int OP_GP_OR        = 0x9B;
    public static final int OP_GP_XOR       = 0x9C;
    public static final int OP_GP_DIVREM    = 0x9D;
    public static final int OP_GP_DOTDOT    = 0x9E;
    public static final int OP_GP_DOTDOTEX  = 0x9F;
    public static final int OP_GP_NEG       = 0xA0;
    public static final int OP_GP_COMPL     = 0xA1;

    public static final int OP_L_GET        = 0xA2;
    public static final int OP_L_SET        = 0xA3;
    public static final int OP_P_GET        = 0xA4;
    public static final int OP_P_SET        = 0xA5;
    public static final int OP_P_VAR        = 0xA6;
    public static final int OP_P_REF        = 0xA7;

    public static final int OP_IP_INC       = 0xA8;
    public static final int OP_IP_DEC       = 0xA9;
    public static final int OP_IP_INCA      = 0xAA;
    public static final int OP_IP_DECA      = 0xAB;
    public static final int OP_IP_INCB      = 0xAC;
    public static final int OP_IP_DECB      = 0xAD;
    public static final int OP_IP_ADD       = 0xAE;
    public static final int OP_IP_SUB       = 0xAF;
    public static final int OP_IP_MUL       = 0xB0;
    public static final int OP_IP_DIV       = 0xB1;
    public static final int OP_IP_MOD       = 0xB2;
    public static final int OP_IP_SHL       = 0xB3;
    public static final int OP_IP_SHR       = 0xB4;
    public static final int OP_IP_USHR      = 0xB5;
    public static final int OP_IP_AND       = 0xB6;
    public static final int OP_IP_OR        = 0xB7;
    public static final int OP_IP_XOR       = 0xB8;

    public static final int OP_PIP_INC      = 0xB9;
    public static final int OP_PIP_DEC      = 0xBA;
    public static final int OP_PIP_INCA     = 0xBB;
    public static final int OP_PIP_DECA     = 0xBC;
    public static final int OP_PIP_INCB     = 0xBD;
    public static final int OP_PIP_DECB     = 0xBE;
    public static final int OP_PIP_ADD      = 0xBF;
    public static final int OP_PIP_SUB      = 0xC0;
    public static final int OP_PIP_MUL      = 0xC1;
    public static final int OP_PIP_DIV      = 0xC2;
    public static final int OP_PIP_MOD      = 0xC3;
    public static final int OP_PIP_SHL      = 0xC4;
    public static final int OP_PIP_SHR      = 0xC5;
    public static final int OP_PIP_USHR     = 0xC6;
    public static final int OP_PIP_AND      = 0xC7;
    public static final int OP_PIP_OR       = 0xC8;
    public static final int OP_PIP_XOR      = 0xC9;

    public static final int OP_I_GET        = 0xCA;
    public static final int OP_I_SET        = 0xCB;

    public static final int OP_IIP_INC      = 0xCC;
    public static final int OP_IIP_DEC      = 0xCD;
    public static final int OP_IIP_INCA     = 0xCE;
    public static final int OP_IIP_DECA     = 0xCF;
    public static final int OP_IIP_INCB     = 0xD0;
    public static final int OP_IIP_DECB     = 0xD1;
    public static final int OP_IIP_ADD      = 0xD2;
    public static final int OP_IIP_SUB      = 0xD3;
    public static final int OP_IIP_MUL      = 0xD4;
    public static final int OP_IIP_DIV      = 0xD5;
    public static final int OP_IIP_MOD      = 0xD6;
    public static final int OP_IIP_SHL      = 0xD7;
    public static final int OP_IIP_SHR      = 0xD8;
    public static final int OP_IIP_USHR     = 0xD9;
    public static final int OP_IIP_AND      = 0xDA;
    public static final int OP_IIP_OR       = 0xDB;
    public static final int OP_IIP_XOR      = 0xDC;

    public static final int OP_M_GET        = 0xDD;
    public static final int OP_M_SET        = 0xDE;
    public static final int OP_M_VAR        = 0xDF;
    public static final int OP_M_REF        = 0xE0;

    public static final int OP_MIP_INC      = 0xE1;
    public static final int OP_MIP_DEC      = 0xE2;
    public static final int OP_MIP_INCA     = 0xE3;
    public static final int OP_MIP_DECA     = 0xE4;
    public static final int OP_MIP_INCB     = 0xE5;
    public static final int OP_MIP_DECB     = 0xE6;
    public static final int OP_MIP_ADD      = 0xE7;
    public static final int OP_MIP_SUB      = 0xE8;
    public static final int OP_MIP_MUL      = 0xE9;
    public static final int OP_MIP_DIV      = 0xEA;
    public static final int OP_MIP_MOD      = 0xEB;
    public static final int OP_MIP_SHL      = 0xEC;
    public static final int OP_MIP_SHR      = 0xED;
    public static final int OP_MIP_USHR     = 0xEE;
    public static final int OP_MIP_AND      = 0xEF;
    public static final int OP_MIP_OR       = 0xF0;
    public static final int OP_MIP_XOR      = 0xF1;

    public static final int OP_RSVD_F2      = 0xF2;
    public static final int OP_RSVD_F3      = 0xF3;
    public static final int OP_RSVD_F4      = 0xF4;
    public static final int OP_RSVD_F5      = 0xF5;
    public static final int OP_RSVD_F6      = 0xF6;
    public static final int OP_RSVD_F7      = 0xF7;
    public static final int OP_RSVD_F8      = 0xF8;
    public static final int OP_RSVD_F9      = 0xF9;
    public static final int OP_RSVD_FA      = 0xFA;
    public static final int OP_RSVD_FB      = 0xFB;
    public static final int OP_RSVD_FC      = 0xFC;
    public static final int OP_RSVD_FD      = 0xFD;
    public static final int OP_RSVD_FE      = 0xFE;
    public static final int OP_RSVD_FF      = 0xFF;


    // ----- pre-defined arguments -----------------------------------------------------------------

    /**
     * Pre-defined argument: a frame-local stack
     */
    public static final int A_STACK     = -1;

    /**
     * Pre-defined argument: a write-only "black hole" register, akin to {@code /dev/null}
     */
    public static final int A_IGNORE    = -2;

    /**
     * Pre-defined argument: an argument value to be retrieved from a method structure
     */
    public static final int A_DEFAULT   = -3;

    /**
     * Pre-defined argument: {@code this}
     */
    public static final int A_THIS      = -4;

    /**
     * Pre-defined argument: {@code this:target}
     */
    public static final int A_TARGET    = -5;

    /**
     * Pre-defined argument: {@code this:public}
     */
    public static final int A_PUBLIC    = -6;

    /**
     * Pre-defined argument: {@code this:protected}
     */
    public static final int A_PROTECTED = -7;

    /**
     * Pre-defined argument: {@code this:private}
     */
    public static final int A_PRIVATE   = -8;

    /**
     * Pre-defined argument: {@code this:struct}
     */
    public static final int A_STRUCT    = -9;

    /**
     * Pre-defined argument: {@code this:class}
     */
    public static final int A_CLASS     = -10;

    /**
     * Pre-defined argument: {@code this:service}
     */
    public static final int A_SERVICE   = -11;

    /**
     * Pre-defined argument: {@code super} (function).
     */
    public static final int A_SUPER     = -12;

    /**
     * Pre-defined argument: an indicator for a "blocking wait" return value. It's similar to
     * {@link #A_IGNORE}, but effectively turns on the "forbidden" reentrancy.
     */
    public static final int A_BLOCK     = -13;

    /**
     * Pre-defined argument: an indicator for "multiple return values" (internal)
     */
    public final static int A_MULTI     = -14;
    /**
     * Pre-defined argument: an indicator for a "tuple return" (internal)
     */
    public final static int A_TUPLE     = -15;
    /**
     * Pre-defined and compile-time only argument: A label.
     */
    public final static int A_LABEL     = -16;

    /**
     * The first constant, constant #0, is at this index (which is a negative). For a constant whose
     * index is {@code i}, it is encoded as: {@code CONSTANT_OFFSET - i}
     */
    public static final int CONSTANT_OFFSET = -17;


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
     * Result from process() method: call the frame placed in frame.f_framePrev.m_frameNext.
     */
    public static final int R_RETURN_CALL = -6;

    /**
     * Result from process() method: some registers are not ready for a read; yield and repeat the
     * same op-code.
     */
    public static final int R_REPEAT = -7;

    /**
     * Result from process() method: at the moment used only by Service.registerAsyncSection() API;
     * the execution must be blocked until Fiber.m_resume continuation allows it to proceed.
     */
    public static final int R_BLOCK = -8;

    /**
     * Result from process() method: yield before executing the next op-code.
     */
    public static final int R_YIELD = -9;

    /**
     * Result from process() method: some registers are not ready for a read; block any other
     * fibers from execution and repeat the same op-code.
     */
    public static final int R_PAUSE = -10;


    // ----- other constants -----------------------------------------------------------------------

    /**
     * An empty array of ops.
     */
    public static final Op[] NO_OPS = new Op[0];

    private static final long REACHABLE_BIT        = 0x8000_0000_0000_0000L;
    private static final long NECESSARY_BIT        = 0x4000_0000_0000_0000L;
    private static final long REDUNDANT_BIT        = 0x2000_0000_0000_0000L;
    private static final long RESERVED_BITS        = 0x1FFF_F000_0000_0000L;
    private static final long GUARD_ALL_DEPTH_BITS = 0x0000_0FF0_0000_0000L, GUARD_ALL_DEPTH_SHIFT = 36;
    private static final long GUARD_DEPTH_BITS     = 0x0000_000F_F000_0000L, GUARD_DEPTH_SHIFT     = 28;
    private static final long SCOPE_DEPTH_BITS     = 0x0000_0000_0FF0_0000L, SCOPE_DEPTH_SHIFT     = 20;
    private static final long POSITION_BITS        = 0x0000_0000_000F_FFFFL;


    // ----- data members --------------------------------------------------------------------------

    /**
     * A bunch of internal info munged into a long.
     */
    private long m_lStruct;
    }