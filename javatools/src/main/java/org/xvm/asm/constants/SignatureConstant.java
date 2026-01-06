package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import java.util.concurrent.locks.StampedLock;

import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.javajit.TypeSystem;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a method signature constant. A signature constant identifies a method's call signature
 * for the purpose of invocation; in other words, it specifies what name, parameters, and return
 * values a method must have in order to be selected for invocation. This is particularly useful for
 * supporting virtual method invocation with auto-narrowing types, as the invocation site does not
 * have to specify which exact method is being invoked (such as a particular method on a particular
 * class), but rather that some virtual method chain exists such that it matches a particular
 * signature.
 * <p/>
 * In Ecstasy, a type is simply a collection of methods and properties. Even properties can be
 * expressed as methods; a property of type T and name N can be represented as a method N that takes
 * no parameters and returns a single value of type T. As such, a type can be represented as a
 * collection of method signatures.
 * <p/>
 * Method signatures are not necessarily exact, however. Consider the support in Ecstasy for auto-
 * narrowing types:
 * <p/>
 * <code><pre>
 *     interface I
 *         {
 *         I foo();
 *         void bar(I i);
 *     }
 * </pre></code>
 * <p/>
 * Now consider a class:
 * <p/>
 * <code><pre>
 *     class C
 *         {
 *         C! foo() {...}
 *         void bar(C! c) {...}
 *     }
 * </pre></code>
 * <p/>
 * While the class does not explicitly implement the interface I, and while the methods on the class
 * are explicit (not auto-narrowing), the class C does implicitly implement interface I, and thus an
 * instance of C can be passed to (or returned from) any method that accepts (or returns) an "I".
 * <p/>
 * A SignatureConstant can also be used to represent a property, but such a use is never serialized;
 * i.e. it is a transient use case.
 */
public class SignatureConstant extends PseudoConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a method signature identifier with no params or returns.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param sName  the name of the method
     */
    public SignatureConstant(ConstantPool pool, @NotNull String sName) {
        this(pool, sName, List.of(), List.of());
    }

    /**
     * Construct a constant whose value is a method signature identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the name of the method
     * @param params   the param types
     * @param returns  the return types
     */
    public SignatureConstant(ConstantPool pool, @NotNull String sName,
                             @NotNull List<TypeConstant> params, @NotNull List<TypeConstant> returns) {
        super(pool);

        m_constName         = pool.ensureStringConstant(Objects.requireNonNull(sName, "name required"));
        m_listConstParams   = List.copyOf(Objects.requireNonNull(params, "params required"));
        m_listConstReturns  = List.copyOf(Objects.requireNonNull(returns, "returns required"));
    }

    /**
     * Construct a constant whose value is a property signature identifier.
     * <p/>
     * This use case allows methods and properties to both be represented in a transient data
     * structure as SignatureConstants; this form of a SignatureConstant cannot be serialized.
     *
     * @param pool           the ConstantPool that will contain this Constant
     * @param constProperty  the property
     */
    public SignatureConstant(ConstantPool pool, PropertyConstant constProperty) {
        super(pool);

        if (constProperty == null) {
            throw new IllegalArgumentException("property required");
        }

        m_constName         = pool.ensureStringConstant(constProperty.getName());
        m_listConstParams   = List.of();
        m_listConstReturns  = List.of(constProperty.getType());
        m_fProperty         = true;
    }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public SignatureConstant(ConstantPool pool, Format format, DataInput in) throws IOException {
        super(pool);

        m_iName     = readMagnitude(in);
        m_aiParams  = readMagnitudeArray(in);
        m_aiReturns = readMagnitudeArray(in);
    }

    @Override
    protected void resolveConstants() {
        ConstantPool pool = getConstantPool();

        m_constName         = pool.getConstant(m_iName, StringConstant.class);
        m_listConstParams   = lookupTypes(pool, m_aiParams);
        m_listConstReturns  = lookupTypes(pool, m_aiReturns);

        m_aiParams  = null;
        m_aiReturns = null;
    }


    // ----- PoolTransferable ----------------------------------------------------------------------

    @Override
    public SignatureConstant transferTo(ConstantPool pool) {
        if (pool == getConstantPool()) {
            return this;
        }

        List<TypeConstant> listParams  = m_listConstParams.stream()
                .map(type -> (TypeConstant) type.transferTo(pool))
                .toList();
        List<TypeConstant> listReturns = m_listConstReturns.stream()
                .map(type -> (TypeConstant) type.transferTo(pool))
                .toList();

        return pool.ensureSignatureConstant(getName(), listParams, listReturns);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the name of the method specified by this signature
     */
    public String getName() {
        return m_constName.getValue();
    }

    /**
     * @return the method's parameter count
     */
    public int getParamCount() {
        return m_listConstParams.size();
    }

    /**
     * @return the method's parameter types
     */
    public List<TypeConstant> getParams() {
        return m_listConstParams;
    }

    /**
     * @return the method's return count
     */
    public int getReturnCount() {
        return m_listConstReturns.size();
    }

    /**
     * @return the method's return types
     */
    public List<TypeConstant> getReturns() {
        return m_listConstReturns;
    }

    /**
     * @return whether this signature represents a property
     */
    public boolean isProperty() {
        return m_fProperty;
    }

    /**
     * @return true iff this signature contains any generic types
     */
    public boolean containsGenericTypes() {
        for (TypeConstant type : m_listConstParams) {
            if (type.containsGenericType(true)) {
                return true;
            }
        }

        for (TypeConstant type : m_listConstReturns) {
            if (type.containsGenericType(true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true iff this signature contains any formal type parameters
     */
    public boolean containsTypeParameters() {
        for (TypeConstant type : m_listConstParams) {
            if (type.containsTypeParameter(true)) {
                return true;
            }
        }

        for (TypeConstant type : m_listConstReturns) {
            if (type.containsTypeParameter(true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create an equivalent signature with generic types resolved based on the specified resolver.
     *
     * @param pool      the ConstantPool to place a potentially created new constant into
     * @param resolver  the resolver
     *
     * @return a resolved signature
     */
    public SignatureConstant resolveGenericTypes(ConstantPool pool, GenericTypeResolver resolver) {
        if (resolver == null) {
            return this;
        }

        List<TypeConstant> listParams  = m_listConstParams;
        List<TypeConstant> listReturns = m_listConstReturns;
        boolean            fDiff       = false;

        for (int i = 0, c = listParams.size(); i < c; ++i) {
            TypeConstant constOriginal = listParams.get(i);
            TypeConstant constResolved = constOriginal.resolveGenerics(pool, resolver);
            if (constOriginal != constResolved) {
                if (!fDiff) {
                    listParams = new ArrayList<>(m_listConstParams);
                    fDiff      = true;
                }
                listParams.set(i, constResolved);
            }
        }

        for (int i = 0, c = listReturns.size(); i < c; ++i) {
            TypeConstant constOriginal = listReturns.get(i);
            TypeConstant constResolved = constOriginal.resolveGenerics(pool, resolver);
            if (constOriginal != constResolved) {
                if (listReturns == m_listConstReturns) {
                    listReturns = new ArrayList<>(m_listConstReturns);
                    fDiff       = true;
                }
                listReturns.set(i, constResolved);
            }
        }

        if (fDiff) {
            SignatureConstant that = pool.ensureSignatureConstant(getName(), listParams, listReturns);
            that.m_fProperty = this.m_fProperty;
            return that;
        }

        return this;
    }

    /**
     * Check if this signature contains any auto-narrowing portion.
     *
     * @param fAllowVirtChild  if false, virtual child constants should not be checked for
     *                         auto-narrowing
     *
     * @return true iff any portion of this TypeConstant represents an auto-narrowing type
     */
    public boolean containsAutoNarrowing(boolean fAllowVirtChild) {
        for (TypeConstant typeParam : m_listConstParams) {
            if (typeParam.containsAutoNarrowing(fAllowVirtChild)) {
                return true;
            }
        }

        for (TypeConstant typeReturn : m_listConstReturns) {
            if (typeReturn.containsAutoNarrowing(fAllowVirtChild)) {
                return true;
            }
        }
        return false;
    }

    /**
     * If any of the signature components are auto-narrowing (or have any references to
     * auto-narrowing types), replace any auto-narrowing portion with an explicit class identity
     * in the context of the specified type.
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param typeTarget  the target type
     * @param idCtx       if specified, the identity of the auto-narrowing "context" class that
     *                    should replace any corresponding auto-narrowing identity
     *
     * @return the SignatureConstant with explicit identities swapped in for any auto-narrowing
     *         identities
     */
    public SignatureConstant resolveAutoNarrowing(ConstantPool pool, TypeConstant typeTarget,
                                                  IdentityConstant idCtx) {
        List<TypeConstant> listParams  = m_listConstParams;
        List<TypeConstant> listReturns = m_listConstReturns;
        boolean            fDiff       = false;

        for (int i = 0, c = listParams.size(); i < c; ++i) {
            TypeConstant constOriginal = listParams.get(i);
            TypeConstant constResolved = constOriginal.resolveAutoNarrowing(pool, false, typeTarget, idCtx);
            if (constOriginal != constResolved) {
                if (!fDiff) {
                    listParams = new ArrayList<>(m_listConstParams);
                    fDiff      = true;
                }
                listParams.set(i, constResolved);
            }
        }

        for (int i = 0, c = listReturns.size(); i < c; ++i) {
            TypeConstant constOriginal = listReturns.get(i);
            TypeConstant constResolved = constOriginal.resolveAutoNarrowing(pool, false, typeTarget, idCtx);
            if (constOriginal != constResolved) {
                if (listReturns == m_listConstReturns) {
                    listReturns = new ArrayList<>(m_listConstReturns);
                    fDiff       = true;
                }
                listReturns.set(i, constResolved);
            }
        }

        if (fDiff) {
            SignatureConstant that = pool.ensureSignatureConstant(getName(), listParams, listReturns);
            that.m_fProperty = this.m_fProperty;
            return that;
        }

        return this;
    }

    /**
     * If any of the signature components are auto-narrowing (or have any references to
     * auto-narrowing types), replace any auto-narrowing portion with a declared class identity.
     *
     * @return the SignatureConstant with all auto-narrowing types resolved
     */
    public SignatureConstant removeAutoNarrowing() {
        return resolveAutoNarrowing(getConstantPool(), null, null);
    }

    /**
     * Check if a method with this signature could be called via the specified signature
     * (it also means that a method with this signature could "super" to the specified method).
     *
     * In other words, check that this signature is "narrower" than the specified one.
     *
     * Note: both "this" and "that" signatures must be resolved.
     *
     * @param that     the signature of the matching method
     * @param typeCtx  the type within which "this" signature is used
     */
    public boolean isSubstitutableFor(SignatureConstant that, TypeConstant typeCtx) {
        /*
         * From Method.x # isSubstitutableFor() (where m2 == this and m1 == that)
         *
         * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
         *    true:
         *    1. _m1_ and _m2_ have the same name
         *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
         *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
         *       1. _p1_ is assignable to _p2_
         *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
         *          the following hold true:
         *          1. _p2_ is assignable to _p1_
         *          2. _T1_ produces _p1_
         *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
         *       _m1_ and _r2_ of _m2_, the following holds true:
         *      1. _r2_ is assignable to _r1_
         */

        // Note, that rule 1.2.2 does not apply in our case (duck typing)

        if (!this.getName().equals(that.getName())) {
            return false;
        }

        int cR1 = that.getReturnCount();
        int cR2 = this.getReturnCount();
        int cP1 = that.getParamCount();
        int cP2 = this.getParamCount();

        // the "sub" method can add return values, but never reduce them; additional parameters
        // should be handled by the caller (see TypeConstant.collectPotentialSuperMethods)
        if (cP2 != cP1 || cR2 < cR1) {
            return false;
        }

        List<TypeConstant> listR1 = that.getReturns();
        List<TypeConstant> listR2 = this.getReturns();
        for (int i = 0, c = Math.min(cR1, cR2); i < c; i++) {
            if (!listR2.get(i).isCovariantReturn(listR1.get(i), typeCtx)) {
                return false;
            }
        }

        List<TypeConstant> listP1 = that.getParams();
        List<TypeConstant> listP2 = this.getParams();
        for (int i = 0; i < cP1; i++) {
            if (!listP2.get(i).isContravariantParameter(listP1.get(i), typeCtx)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a method with this signature could be called via the specified signature.
     *
     * Unlike the "isSubstitutableFor" method above, this method is called only by the run-time call
     * chain computation logic and only if isSubstitutableFor failed. It basically tests if it's
     * "good enough for government work", i.e. could someone have previously signed off on a method
     * represented by this signature being callable.
     *
     * Note, that when the "shim" of the "weak" isA() assignment is in place, including the verifier
     * work for the variables on stack, this method is quite likely won't be needed.
     */
    public boolean isCallableAs(SignatureConstant that) {
        if (!this.getName().equals(that.getName())) {
            return false;
        }

        int cR1 = that.getReturnCount();
        int cR2 = this.getReturnCount();
        int cP1 = that.getParamCount();
        int cP2 = this.getParamCount();

        if (cP2 != cP1 || cR2 < cR1) {
            return false;
        }

        List<TypeConstant> listR1 = that.getReturns();
        List<TypeConstant> listR2 = this.getReturns();
        for (int i = 0, c = Math.min(cR1, cR2); i < c; i++) {
            if (!listR2.get(i).isA(listR1.get(i)) && !listR1.get(i).isA(listR2.get(i))) {
                return false;
            }
        }

        List<TypeConstant> listP1 = that.getParams();
        List<TypeConstant> listP2 = this.getParams();
        for (int i = 0; i < cP1; i++) {
            if (!listP1.get(i).isA(listP2.get(i)) && !listP2.get(i).isA(listP1.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determine if all the types used by this SignatureConstant are shared between its pool and the
     * specified pool.
     *
     * @param poolOther  the constant pool to check
     *
     * @return true iff this SignatureConstant is shared between its pool and the specified pool
     */
    public boolean isShared(ConstantPool poolOther) {
        if (poolOther != getConstantPool()) {
            for (TypeConstant type : m_listConstParams) {
                if (!type.isShared(poolOther)) {
                    return false;
                }
            }

            for (TypeConstant type : m_listConstReturns) {
                if (!type.isShared(poolOther)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @return the type of the function that corresponds to this SignatureConstant
     */
    public TypeConstant asFunctionType() {
        return getConstantPool().buildFunctionType(m_listConstParams, m_listConstReturns);
    }

    /**
     * @return the type of the function that corresponds to this SignatureConstant with a first
     *         (at index zero) parameter accepting the specified target type
     */
    public TypeConstant asBjarneLambdaType(ConstantPool pool, TypeConstant typeTarget) {
        List<TypeConstant> listParamsNew = new ArrayList<>(m_listConstParams.size() + 1);
        listParamsNew.add(typeTarget);
        listParamsNew.addAll(m_listConstParams);
        return pool.buildFunctionType(listParamsNew, m_listConstReturns);
    }

    /**
     * @return the type of the method that corresponds to this SignatureConstant for the specified
     *         target type
     */
    public TypeConstant asMethodType(ConstantPool pool, TypeConstant typeTarget) {
        return pool.buildMethodType(typeTarget, m_listConstParams, m_listConstReturns);
    }

    /**
     * @return the type of the function that corresponds to this SignatureConstant as a constructor
     */
    public TypeConstant asConstructorType(ConstantPool pool, TypeConstant typeTarget) {
        assert "construct".equals(getName()) && m_listConstReturns.isEmpty();
        return pool.buildFunctionType(m_listConstParams, List.of(typeTarget));
    }

    /**
     * Truncate some of the signature's parameters.
     *
     * @param ofStart  the first index to retain a parameter at
     * @param cParams  the number of parameters to retain; -1 means "take all remaining"
     *
     * @return a new SignatureConstant that has the specified (lesser than original) number of
     *         parameters
     */
    public SignatureConstant truncateParams(int ofStart, int cParams) {
        if (cParams < 0) {
            cParams = m_listConstParams.size() - ofStart;
        }

        assert ofStart >= 0;
        assert ofStart + cParams <= m_listConstParams.size();

        return getConstantPool().ensureSignatureConstant(getName(),
                m_listConstParams.subList(ofStart, ofStart + cParams), m_listConstReturns);
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Signature;
    }

    @Override
    public boolean containsUnresolved() {
        if (isHashCached()) {
            return false;
        }

        if (m_constName.containsUnresolved()) {
            return true;
        }

        for (TypeConstant constant : m_listConstParams) {
            if (constant.containsUnresolved()) {
                return true;
            }
        }

        for (TypeConstant constant : m_listConstReturns) {
            if (constant.containsUnresolved()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constName);
        for (TypeConstant constant : m_listConstParams) {
            visitor.accept(constant);
        }
        for (TypeConstant constant : m_listConstReturns) {
            visitor.accept(constant);
        }
    }

    @Override
    public SignatureConstant resolveTypedefs() {
        List<TypeConstant> listParams  = m_listConstParams;
        List<TypeConstant> listReturns = m_listConstReturns;
        boolean            fDiff       = false;

        for (int i = 0, c = listParams.size(); i < c; ++i) {
            TypeConstant constOld = listParams.get(i);
            TypeConstant constNew = constOld.resolveTypedefs();
            if (constNew != constOld) {
                if (!fDiff) {
                    listParams = new ArrayList<>(m_listConstParams);
                    fDiff      = true;
                }
                listParams.set(i, constNew);
            }
        }

        for (int i = 0, c = listReturns.size(); i < c; ++i) {
            TypeConstant constOld = listReturns.get(i);
            TypeConstant constNew = constOld.resolveTypedefs();
            if (constNew != constOld) {
                if (listReturns == m_listConstReturns) {
                    listReturns = new ArrayList<>(m_listConstReturns);
                    fDiff       = true;
                }
                listReturns.set(i, constNew);
            }
        }

        if (fDiff) {
            return getConstantPool().ensureSignatureConstant(getName(), listParams, listReturns);
        }
        return this;
    }

    @Override
    protected int compareDetails(Constant obj) {
        if (!(obj instanceof SignatureConstant that)) {
            return -1;
        }

        if (that == m_sigPrev) {
            long stamp    = m_lockPrev.tryOptimisticRead();
            int  nCmpPrev = m_nCmpPrev;
            if (that == m_sigPrev && m_lockPrev.validate(stamp)) {
                return nCmpPrev;
            }
        }

        boolean fCache = this.getConstantPool() == that.getConstantPool() && !containsUnresolved();
        int     n      = this.m_constName.compareTo(that.m_constName);
        if (n == 0) {
            n = compareTypes(this.m_listConstParams, that.m_listConstParams);
            if (n == 0) {
                n = compareTypes(this.m_listConstReturns, that.m_listConstReturns);
                if (n == 0) {
                    n = (this.m_fProperty ? 1 : 0) - (that.m_fProperty ? 1 : 0);
                }
            }
        }

        if (fCache) {
            // while completely non-obvious at first look, caching this result has a tremendous
            // impact on the big-O, by short-circuiting a recursive comparison caused by signatures
            // containing TypeParameterConstants
            long stamp = m_lockPrev.tryWriteLock();
            if (stamp != 0) {
                m_sigPrev  = that;
                m_nCmpPrev = n;
                m_lockPrev.unlockWrite(stamp);
            }
        }
        return n;
    }

    @Override
    public String getValueString() {
        StringBuilder sb = new StringBuilder();

        switch (m_listConstReturns.size()) {
        case 0:
            sb.append("void");
            break;

        case 1:
            sb.append(m_listConstReturns.getFirst().getValueString());
            break;

        default:
            sb.append('(');
            boolean first = true;
            for (TypeConstant type : m_listConstReturns) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(type.getValueString());
            }
            sb.append(')');
            break;
        }

        sb.append(' ')
          .append(m_constName.getValue());

        if (!m_fProperty) {
            sb.append('(');

            boolean first = true;
            for (TypeConstant type : m_listConstParams) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(type.getValueString());
            }

            sb.append(')');
        }

        return sb.toString();
    }


    // ----- JIT support ---------------------------------------------------------------------------

    /**
     * Ensure a unique name for this method signature at the specified TypeSystem.
     */
    public String ensureJitMethodName(TypeSystem ts) {
        String sJitName = m_sJitName;
        if (sJitName == null) {
            // get the master instance of the signature constant
            ConstantPool      pool = ts.findOwnerPool(this);
            SignatureConstant sig  = pool.register(this);

            synchronized (sig) {
                sJitName = sig.m_sJitName;
                if (sJitName == null) {
                    String sNameOrig = getName();
                    sig.m_sJitName = sJitName = sNameOrig + ts.xvm.createUniqueSuffix(sNameOrig);
                }
            }
        }
        return sJitName;
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constName     = pool.register(m_constName);
        m_listConstParams  = TypeConstant.registerTypeConstants(pool, m_listConstParams);
        m_listConstReturns = TypeConstant.registerTypeConstants(pool, m_listConstReturns);

        // clear the cache
        long stamp = m_lockPrev.writeLock();
        m_sigPrev  = null;
        m_lockPrev.unlockWrite(stamp);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        if (m_fProperty) {
            throw new IllegalStateException("Signature refers to a property");
        }

        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName.getPosition());
        writeTypes(out, m_listConstParams);
        writeTypes(out, m_listConstReturns);
    }

    @Override
    public String getDescription() {
        return "name=" + getName()
                + ", params=" + formatTypes(m_listConstParams)
                + ", returns=" + formatTypes(m_listConstReturns);
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    protected int computeHashCode() {
        return Hash.of(m_listConstParams,
               Hash.of(m_listConstReturns,
               Hash.of(m_constName)));
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Read a length encoded array of constant indexes.
     *
     * @param in  a DataInput stream to read from
     *
     * @return an array of integers, which are the indexes of the constants
     *
     * @throws IOException  if an error occurs attempting to read from the stream
     */
    protected static int[] readMagnitudeArray(DataInput in)
            throws IOException {
        int   c  = readMagnitude(in);
        int[] an = new int[c];
        for (int i = 0; i < c; ++i) {
            an[i] = readMagnitude(in);
        }
        return an;
    }

    /**
     * Convert the passed array of constant indexes into a list of type constants.
     *
     * @param pool  the ConstantPool
     * @param an    an array of constant indexes
     *
     * @return an immutable list of type constants
     */
    protected static List<TypeConstant> lookupTypes(ConstantPool pool, int[] an) {
        if (an.length == 0) {
            return List.of();
        }
        return Arrays.stream(an)
                .mapToObj(i -> pool.getConstant(i, TypeConstant.class))
                .toList();
    }

    /**
     * Write a length-encoded series of type constants to the specified stream.
     *
     * @param out        a DataOutput stream
     * @param listConst  a list of constants
     *
     * @throws IOException  if an error occurs while writing the type constants
     */
    protected static void writeTypes(DataOutput out, List<TypeConstant> listConst)
            throws IOException {
        writePackedLong(out, listConst.size());

        for (TypeConstant typeConstant : listConst) {
            writePackedLong(out, typeConstant.getPosition());
        }
    }

    /**
     * Internal helper to validate a list of types for nulls.
     *
     * @param listConst  a list of TypeConstant; may be null
     *
     * @return an immutable non-null list of TypeConstant, each element of which is non-null
     */
    protected static List<TypeConstant> validateTypes(List<TypeConstant> listConst) {
        if (listConst == null || listConst.isEmpty()) {
            return List.of();
        }

        for (TypeConstant constant : listConst) {
            if (constant == null) {
                throw new IllegalArgumentException("type required");
            }
        }

        // Return immutable copy
        return List.copyOf(listConst);
    }

    /**
     * Compare two lists of type constants for order, as per the rules described by
     * {@link Comparable}.
     *
     * @param listThis  the first list of type constants
     * @param listThat  the second list of type constants
     *
     * @return a negative, zero, or a positive integer, depending on if the first list is less
     *         than, equal to, or greater than the second list for purposes of ordering
     */
    protected static int compareTypes(List<TypeConstant> listThis, List<TypeConstant> listThat) {
        int cThis = listThis.size();
        int cThat = listThat.size();
        for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i) {
            int n = listThis.get(i).compareTo(listThat.get(i));
            if (n != 0) {
                return n;
            }
        }
        return cThis - cThat;
    }

    /**
     * Render a list of TypeConstant objects as a comma-delimited string containing those types.
     *
     * @param listConst  the list of type constants
     *
     * @return a parenthesized, comma-delimited string of types
     */
    protected static String formatTypes(List<TypeConstant> listConst) {
        return listConst.stream()
                .map(TypeConstant::getValueString)
                .collect(Collectors.joining(", ", "(", ")"));
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the name of this
     * method.
     */
    private int m_iName;

    /**
     * During disassembly, this holds the indexes of the type constants for the parameters.
     */
    private int[] m_aiParams;

    /**
     * During disassembly, this holds the indexes of the type constants for the return values.
     */
    private int[] m_aiReturns;

    /**
     * The constant that represents the parent of this method.
     */
    private StringConstant m_constName;

    /**
     * The invocation parameters of the method.
     */
    private List<TypeConstant> m_listConstParams;

    /**
     * The return values from the method.
     */
    private List<TypeConstant> m_listConstReturns;

    /**
     * An indicator that this signature refers to a property.
     */
    private transient boolean m_fProperty;

    /**
     * Lock protecting {@link #m_sigPrev} and {@link #m_nCmpPrev}
     */
    private final StampedLock m_lockPrev = new StampedLock();

    /**
     * Cached comparison target.
     */
    private transient SignatureConstant m_sigPrev;

    /**
     * Cached comparison result.
     */
    private transient int m_nCmpPrev;

    /**
     * Cached JIT method name.
     */
    private transient String m_sJitName;
}