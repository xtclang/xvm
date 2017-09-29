package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

import org.xvm.asm.Op.Prefix;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a method.
 *
 * @author cp 2016.04.25
 */
public class MethodStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MethodStructure with the specified identity. This constructor is used to
     * deserialize a MethodStructure.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }

    /**
     * Construct a method structure.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     * @param aReturns   an array of Parameters representing the "out" values
     * @param aParams    an array of Parameters representing the "in" values
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId, ConditionalConstant condition,
            Parameter[] aReturns, Parameter[] aParams)
        {
        this(xsParent, nFlags, constId, condition);

        m_aReturns = aReturns;
        m_aParams  = aParams;

        if (aReturns.length > 0 && aReturns[0].isConditionalReturn())
            {
            setConditionalReturn(true);
            }

        int cTypeParams = 0;
        for (Parameter param : aParams)
            {
            if (param.isTypeParameter())
                {
                ++cTypeParams;
                }
            else
                {
                break;
                }
            }
        m_cTypeParams = cTypeParams;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the number of return values
     */
    public int getReturnCount()
        {
        return m_aReturns.length;
        }

    /**
     * Get the Parameter structure that represents the i-th return value.
     *
     * @param i  an index
     *
     * @return the i-th return value
     */
    public Parameter getReturn(int i)
        {
        return m_aReturns[i];
        }

    /**
     * @return a list of Parameter structures that represent all return values of the method
     */
    public List<Parameter> getReturns()
        {
        return Arrays.asList(m_aReturns);
        }

    /**
     * @return the number of method parameters (including the number of type parameters)
     */
    public int getParamCount()
        {
        return m_aParams.length;
        }

    /**
     * @return the number of type parameters
     */
    public int getTypeParamCount()
        {
        return m_cTypeParams;
        }

    /**
     * Get the Parameter structure that represents the i-th method parameter. The type parameters
     * come first, followed by the ordinary parameters.
     *
     * @param i  an index
     *
     * @return the i-th method parameter
     */
    public Parameter getParam(int i)
        {
        return m_aParams[i];
        }

    /**
     * @return a list of Parameter structures that represent all parameters of the method
     */
    public List<Parameter> getParams()
        {
        return Arrays.asList(m_aParams);
        }

    /**
     * Ensure that a Code object exists. If the MethodStructure was disassembled, then the Code will
     * hold the deserialized Ops. If the MethodStructure has been created but no Code has already
     * been created, then the Code will be empty. If the Code was already created, then that
     * previous Code will be returned.
     *
     * @return a Code object, or null iff this MethodStructure has been marked as native
     */
    public Code ensureCode()
        {
        if (isNative())
            {
            return null;
            }

        Code code = m_code;
        if (code == null)
            {
            m_code = code = new Code();
            }
        return code;
        }

    /**
     * Create an empty Code object.
     *
     * @return a new and empty Code object
     */
    public Code createCode()
        {
        Code code;

        resetRuntimeInfo();
        m_aconstLocal = null;
        m_abOps       = null;
        m_code = code = new Code();

        markModified();

        return code;
        }

    /**
     * @return the op-code array for this method
     */
    public Op[] getOps()
        {
        Op[] aop = null;

        Code code = ensureCode();
        if (code != null)
            {
            aop = code.getOps();
            }

        return aop;
        }

    public Constant[] getLocalConstants()
        {
        return m_aconstLocal;
        }

    /**
     * Specify the ops for this method.
     *
     * @param aop  the op-code array for this method
     *
     * @deprecated
     */
    public void setOps(Op[] aop)
        {
        resetRuntimeInfo();
        m_aconstLocal = null;
        m_abOps       = null;
        m_code        = new Code(aop);
        markModified();
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * @return a scope containing just the parameters to the method
     */
    Scope createInitialScope()
        {
        Scope scope = new Scope();
        for (int i = 0, c = getParamCount(); i < c; ++i)
            {
            scope.allocVar();
            }
        return scope;
        }

    /**
     * Initialize the runtime information. This is done automatically.
     */
    public void ensureRuntimeInfo()
        {
        if (m_cScopes == 0)
            {
            if (m_fNative)
                {
                Scope scope = createInitialScope();
                m_cVars   = scope.getMaxVars();
                m_cScopes = scope.getMaxDepth();
                }
            else
                {
                ensureCode().ensureAssembled();
                assert m_cScopes > 0;
                }
            }
        }

    /**
     * Discard any runtime information.
     */
    public void resetRuntimeInfo()
        {
        m_code    = null;
        m_cVars   = 0;
        m_cScopes = 0;
        m_fNative = false;
        }

    /**
     * @return the number of variables (registers) necessary for a frame running this method's code
     *         (including the parameters)
     */
    public int getMaxVars()
        {
        ensureRuntimeInfo();
        return m_cVars;
        }

    /**
     * @return the number of scopes necessary for a frame running this method's code
     */
    public int getMaxScopes()
        {
        ensureRuntimeInfo();
        return m_cScopes;
        }

    /**
     * @return true iff the method has been marked as native
     */
    public boolean isNative()
        {
        return m_fNative;
        }

    /**
     * Specifies whether or not the method implementation is provided directly by the runtime, aka
     * "native".
     *
     * @param fNative  pass true to mark the method as native
     */
    public void setNative(boolean fNative)
        {
        if (fNative)
            {
            resetRuntimeInfo();
            }
        m_fNative = fNative;
        }

    /**
     * @deprecated
     */
    public MethodStructure getConstructFinally()
        {
        // TODO this method must calculate the value
        return m_structFinally;
        }

    /**
     * @deprecated
     */
    public void setConstructFinally(MethodStructure structFinally)
        {
        // TODO this method must die (eventually)
        m_structFinally = structFinally;
        }

    /**
     * Indicates whether or not this method contains a call to its super.
     */
    public boolean isSuperCalled()
        {
        // TODO: the compiler would supply this information
        return getAccess() != Access.PRIVATE;
        }

    /**
     * Check if this method could act as a substitute for the specified method.
     *
     * @param signature   the signature of the matching method
     * @param clazz       the TypeComposition in which context's the matching is evaluated
     */
    public boolean isSubstitutableFor(SignatureConstant signature, TypeComposition clazz)
        {
        int cParams = getParamCount();
        int cReturns = getReturnCount();

        if (cParams != signature.getParams().size() ||
            cReturns != signature.getReturns().size())
            {
            return false;
            }

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
        for (int i = 0; i < cReturns; i++)
            {
            TypeConstant typeR2 = getReturn(i).getType();
            TypeConstant typeR1 = signature.getRawReturns()[i];

            if (!typeR2.isA(typeR1, clazz))
                {
                return false;
                }
            }

        for (int i = 0; i < cParams; i++)
            {
            TypeConstant typeP2 = getParam(i).getType();
            TypeConstant typeP1 = signature.getRawParams()[i];

            if (typeP1.isA(typeP2, clazz))
                {
                continue;
                }

            if (!typeP2.isA(typeP1, clazz))
                {
                return false;
                }

            // TODO:
            // if there is an number of different formal names, then at least one of them must be
            // produced by the type T1
//            if (String[] namesThis : this.formalParamNames(loop.count))
//                {
//                if (String[] namesThat : that.formalParamNames(loop.count))
//                    {
//                    for (String name : nameThis.intersection(namesThat))
//                        {
//                        if (that.TargetType.produces(typeP1))
//                            {
//                            return true;
//                            }
//                        }
//                    }
//                }

            return false;
            }
        return true;
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public boolean isConditionalReturn()
        {
        return super.isConditionalReturn();
        }

    @Override
    public void setConditionalReturn(boolean fConditional)
        {
        if (fConditional != isConditionalReturn())
            {
            // verify that the first return value is a boolean
            Parameter paramOld = m_aReturns[0];
            if (!(paramOld.getType().isEcstasy("Boolean")))
                {
                throw new IllegalStateException("first return value is not Boolean (" + paramOld + ")");
                }

            // change the first return value as specified
            m_aReturns[0] = new Parameter(getConstantPool(), paramOld.getType(), paramOld.getName(),
                    paramOld.getDefaultValue(), true, 0, fConditional);

            super.setConditionalReturn(fConditional);
            }
        }

    @Override
    public String getName()
        {
        return getIdentityConstant().getName();
        }

    @Override
    protected boolean isChildLessVisible()
        {
        return true;
        }

    @Override
    protected Component getEldestSibling()
        {
        Component parent = getParent();
        assert parent != null;

        Component sibling = parent.getMethodByConstantMap().get(getIdentityConstant());
        assert sibling != null;

        return sibling;
        }

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }

    @Override
    public ResolutionResult resolveName(String sName, ResolutionCollector collector)
        {
        for (int i = 0, c = m_cTypeParams; i < c; ++i)
            {
            Parameter param = m_aParams[i];
            assert param.isTypeParameter();

            if (param.getName().equals(sName))
                {
                return collector.resolvedType(
                        getConstantPool().ensureRegisterConstant(getIdentityConstant(), i));
                }
            }

        // method short-circuits the search
        return ResolutionResult.UNKNOWN;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MethodConstant getIdentityConstant()
        {
        return (MethodConstant) super.getIdentityConstant();
        }

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        super.disassemble(in);

        ConstantPool   pool              = getConstantPool();
        MethodConstant constMethod       = getIdentityConstant();
        TypeConstant[] aconstReturnTypes = constMethod.getRawReturns();
        TypeConstant[] aconstParamTypes  = constMethod.getRawParams();

        int         cReturns = aconstReturnTypes.length;
        Parameter[] aReturns = new Parameter[cReturns];
        boolean     fCond    = isConditionalReturn();
        for (int i = 0; i < cReturns; ++i)
            {
            Parameter param = new Parameter(pool, in, true, i, i==0 && fCond);
            if (!param.getType().equals(aconstReturnTypes[i]))
                {
                throw new IOException("type mismatch between method constant and return " + i + " value type");
                }
            aReturns[i] = param;
            }

        int         cParams     = aconstParamTypes.length;
        Parameter[] aParams     = new Parameter[cParams];
        int         cTypeParams = readMagnitude(in);
        for (int i = 0; i < cParams; ++i)
            {
            Parameter param = new Parameter(pool, in, true, i, i < cTypeParams);
            if (!param.getType().equals(aconstParamTypes[i]))
                {
                throw new IOException("type mismatch between method constant and param " + i + " value type");
                }
            aParams[i] = param;
            }

        // read local "constant pool"
        int cConsts = readMagnitude(in);
        Constant[] aconst = cConsts == 0 ? Constant.NO_CONSTS : new Constant[cConsts];
        for (int i = 0; i < cConsts; ++i)
            {
            aconst[i] = pool.getConstant(readMagnitude(in));
            }

        // read code
        int cbOps = readMagnitude(in);
        byte[] abOps = new byte[cbOps];
        in.readFully(abOps);

        m_aReturns    = aReturns;
        m_cTypeParams = cTypeParams;
        m_aParams     = aParams;
        m_aconstLocal = aconst;
        m_abOps       = abOps;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // if the return type is Void, that means that there is no return type at all
        if (m_aReturns.length == 1 && m_aReturns[0].getType().isVoid())
            {
            m_aReturns = NO_PARAMS;
            }

        for (Parameter param : m_aReturns)
            {
            param.registerConstants(pool);
            }

        for (Parameter param : m_aParams)
            {
            param.registerConstants(pool);
            }

        // local constants:
        // (1) if code was created for this method, then it needs to register the constanats;
        // (2) otherwise, if the local constants are present (because we read them in), then make
        //     sure they're all registered;
        // (3) otherwise, assume there are no local constants
        Code code = m_code;
        if (code == null)
            {
            Constant[] aconst = m_aconstLocal;
            if (aconst != null)
                {
                for (int i = 0, c = aconst.length; i < c; ++i)
                    {
                    Constant constOld = aconst[i];
                    Constant constNew = pool.register(constOld);
                    if (constNew != constOld)
                        {
                        aconst[i] = constNew;
                        }
                    }
                }
            }
        else
            {
            code.ensureAssembled();
            }
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        super.assemble(out);

        for (Parameter param : m_aReturns)
            {
            param.assemble(out);
            }

        writePackedLong(out, m_cTypeParams);
        for (Parameter param : m_aParams)
            {
            param.assemble(out);
            }

        // write out the "local constant pool"
        Constant[] aconst  = m_aconstLocal;
        int        cConsts = aconst == null ? 0 : aconst.length;
        writePackedLong(out, cConsts);
        for (int i = 0; i < cConsts; ++i)
            {
            writePackedLong(out, aconst[i].getPosition());
            }

        // write out the bytes (if there are any)
        byte[] abOps = m_abOps;
        int    cbOps = abOps == null ? 0 : abOps.length;
        writePackedLong(out, cbOps);
        if (cbOps > 0)
            {
            out.write(abOps);
            }
        }


    @Override
    public String getDescription()
        {
        return new StringBuilder()

                .append("id=\"")
                .append(getIdentityConstant().getValueString())
                .append("\", sig=")
                .append(getIdentityConstant().getSignature())
                .append(", ")
                .append(super.getDescription())
                .append(", conditional=")
                .append(isConditionalReturn())
                .append(", type-param-count=")
                .append(m_cTypeParams)
                .toString();
        }


    // ----- inner class: Code ---------------------------------------------------------------------

    /**
     * TODO
     */
    public class Code
        {
        // ----- constructors -----------------------------------------------------------------

        /**
         * TODO temporary
         *
         * @param aop   array of ops
         */
        Code(Op[] aop)
            {
            m_aop = aop;
            calcVars();
            }

        Code()
            {
            byte[] abOps = m_abOps;
            if (abOps != null)
                {
                try
                    {
                    m_aop = abOps.length == 0
                            ? Op.NO_OPS
                            : Op.readOps(new DataInputStream(new ByteArrayInputStream(abOps)), m_aconstLocal);
                    }
                catch (IOException e)
                    {
                    throw new RuntimeException(e);
                    }
                }
            }

        // ----- Code methods -----------------------------------------------------------------

        /**
         * Add the specified op to the end of the code.
         *
         * @param op  the Op to add
         */
        public void add(Op op)
            {
            ensureAppending();

            ArrayList<Op> listOps = m_listOps;
            if (m_fTrailingPrefix)
                {
                // get the last op and append this op to it
                ((Prefix) listOps.get(listOps.size()-1)).append(op);
                }
            else
                {
                listOps.add(op);
                }

            m_fTrailingPrefix = op instanceof Prefix;
            m_mapIndex        = null;
            }

        /**
         * Find the specified op in the sequence of op codes.
         *
         * @param op  the op to find
         *
         * @return the index (aka the PC) within the method) of the op, or -1 if the op could not be
         *         found
         */
        public int addressOf(Op op)
            {
            IdentityHashMap<Op, Integer> mapIndex = m_mapIndex;
            if (mapIndex == null)
                {
                ArrayList<Op> listOps = m_listOps;
                int           cOps    = listOps.size();
                if (cOps < 50)
                    {
                    // brute force search a small list of ops
                    for (int i = 0; i < cOps; ++i)
                        {
                        if (listOps.get(i).contains(op))
                            {
                            return i;
                            }
                        }
                    return -1;
                    }

                m_mapIndex = mapIndex = new IdentityHashMap<>();
                for (int i = 0; i < cOps; ++i)
                    {
                    Op opEach = listOps.get(i);
                    do
                        {
                        mapIndex.put(opEach, i);
                        }
                    while (opEach instanceof Prefix && (opEach = ((Prefix) opEach).getNextOp()) != null);
                    }
                }

            Integer index = mapIndex.get(op);
            return index == null ? -1 : index;
            }

        /**
         * @return the array of Ops that make up the Code
         */
        public Op[] getOps()
            {
            ensureAssembled();
            return m_aop;
            }

        // ----- internal ---------------------------------------------------------------------

        private void ensureAppending()
            {
            if (m_abOps != null)
                {
                throw new IllegalStateException("not appendable");
                }

            if (m_listOps == null)
                {
                m_listOps =  new ArrayList<>();
                }
            }

        private void ensureAssembled()
            {
            if (m_abOps == null)
                {
                // build the local constant array
                Op[] aop = m_aop;
                if (aop == null)
                    {
                    m_aop = aop = m_listOps.toArray(new Op[m_listOps.size()]);
                    }

                Op.ConstantRegistry registry = new Op.ConstantRegistry(getConstantPool());
                Scope scope = createInitialScope();
                for (int i = 0, c = aop.length; i < c; ++i)
                    {
                    Op op = aop[i];
                    op.registerConstants(registry);
                    op.simulate(scope);
                    op.resolveAddress(this, i);
                    }

                // assemble the ops into bytes
                ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
                DataOutputStream outData = new DataOutputStream(outBytes);
                try
                    {
                    for (Op op : aop)
                        {
                        op.write(outData, registry);
                        }
                    }
                catch (IOException e)
                    {
                    throw new IllegalStateException(e);
                    }

                m_abOps       = outBytes.toByteArray();
                m_aconstLocal = registry.getConstantArray();
                m_cVars       = scope.getMaxVars();
                m_cScopes     = scope.getMaxDepth();
                markModified();
                }
            }

        private void calcVars()
            {
            Scope scope = createInitialScope();

            Op[] aop = getOps();
            if (m_cScopes == 0)
                {
                for (Op op : aop)
                    {
                    op.simulate(scope);
                    }

                m_cVars   = scope.getMaxVars();
                m_cScopes = scope.getMaxDepth();
                }
            }

        // ----- fields -----------------------------------------------------------------------

        /**
         * List of ops being assembled.
         */
        private ArrayList<Op> m_listOps;

        /**
         * Lookup of op address by op.
         */
        private IdentityHashMap<Op, Integer> m_mapIndex;

        /**
         * True iff the last op added was a prefix.
         */
        private boolean m_fTrailingPrefix;

        /**
         * The array of ops.
         */
        private Op[] m_aop;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of Parameters.
     */
    public static final Parameter[] NO_PARAMS = new Parameter[0];

    /**
     * Empty array of Ops.
     */
    public static final Op[] NO_OPS = new Op[0];

    /**
     * The return value types. (A zero-length array is "Void".)
     */
    private Parameter[] m_aReturns;

    /**
     * The number of type parameters.
     */
    private int m_cTypeParams;

    /**
     * The parameter types.
     */
    private Parameter[] m_aParams;

    /**
     * The constants used by the Ops.
     */
    transient Constant[] m_aconstLocal;

    /**
     * The yet-to-be-deserialized ops.
     */
    transient byte[] m_abOps;

    /**
     * The method's code (for assembling new code).
     */
    private transient Code m_code;

    /**
     * The max number of registers used by the method. Calculated from the ops.
     */
    transient int m_cVars;

    /**
     * The max number of scopes used by the method. Calculated from the ops.
     */
    transient int m_cScopes;

    /**
     * True iff the method has been marked as "native". This is not part of the persistent method
     * structure; it exists only to support the prototype interpreter implementation.
     */
    private transient boolean m_fNative;

    /**
     * Cached method for the contruct-finally that goes with this method, iff this method is a
     * construct function that has a finally.
     */
    private transient MethodStructure m_structFinally;
    }
