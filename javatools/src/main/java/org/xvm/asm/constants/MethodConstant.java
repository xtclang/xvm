package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Method constant. A method constant uniquely identifies a method structure within a
 * named multi-method structure (a group of methods by the same name).
 */
public class MethodConstant
        extends IdentityConstant
        implements GenericTypeResolver
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  specifies the MultiMethodConstant that contains this method
     * @param constSig     the method signature constant
     */
    public MethodConstant(ConstantPool pool, MultiMethodConstant constParent, SignatureConstant constSig)
        {
        this(pool, constParent, constSig, 0);
        }

    /**
     * Construct a constant whose value is a method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  specifies the MultiMethodConstant that contains this method
     * @param params       the param types
     * @param returns      the return types
     */
    public MethodConstant(ConstantPool pool, MultiMethodConstant constParent,
                          TypeConstant[] params, TypeConstant[] returns)
        {
        this(pool, constParent, pool.ensureSignatureConstant(constParent.getName(), params, returns), 0);
        }

    /**
     * Construct a constant whose value is a method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  specifies the MultiMethodConstant that contains this method
     * @param iLambda      the separate lambda identity
     */
    public MethodConstant(ConstantPool pool, MultiMethodConstant constParent, int iLambda)
        {
        this(pool, constParent, null, iLambda);
        }

    /**
     * (Internal) Construct a constant whose value is a method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  specifies the MultiMethodConstant that contains this method
     * @param constSig     the method signature constant
     * @param iLambda      the separate lambda identity
     */
    protected MethodConstant(ConstantPool pool, MultiMethodConstant constParent,
                             SignatureConstant constSig, int iLambda)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        if (constSig == null && iLambda == 0)
            {
            throw new IllegalArgumentException("signature or lambda identity required");
            }

        if (iLambda < 0)
            {
            throw new IllegalArgumentException("illegal lambda identity: " + iLambda);
            }

        m_constParent = constParent;
        m_constSig    = constSig;
        m_iLambda     = iLambda;
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
    public MethodConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iParent = readMagnitude(in);
        m_iSig    = readMagnitude(in);
        m_iLambda = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constParent = (MultiMethodConstant) pool.getConstant(m_iParent);
        m_constSig    = (SignatureConstant  ) pool.getConstant(m_iSig   );
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return true iff the method is a lambda
     */
    public boolean isLambda()
        {
        // a missing signature can only occur for a lambda;
        // a lambda identity always occurs for a lambda
        boolean fLambda = m_iLambda > 0;
        assert fLambda || m_constSig != null;
        assert fLambda == "->".equals(getName());
        return fLambda;
        }

    /**
     * @return the lambda index, which serves as its identity (instead of the signature)
     */
    public int getLambdaIndex()
        {
        return m_iLambda;
        }

    /**
     * @return true iff the method is a nascent lambda (still in the process of being defined)
     */
    public boolean isNascent()
        {
        return m_constSig == null;
        }

    /**
     * @return the method's signature constant
     */
    public SignatureConstant getSignature()
        {
        assert !isNascent();
        return m_constSig;
        }

    /**
     * Provide a lambda with its signature, once the signature is known.
     *
     * @param sig  the lambda function or method signature
     */
    public void setSignature(SignatureConstant sig)
        {
        assert isLambda();
        assert isNascent();
        assert sig != null;
        m_constSig = sig;
        }

    /**
     * @return the method's parameter types
     */
    public TypeConstant[] getRawParams()
        {
        return getSignature().getRawParams();
        }

    /**
     * @return the method's parameter types
     */
    public List<TypeConstant> getParams()
        {
        return getSignature().getParams();
        }

    /**
     * @return the method's return types
     */
    public TypeConstant[] getRawReturns()
        {
        return getSignature().getRawReturns();
        }

    /**
     * @return the method's return types
     */
    public List<TypeConstant> getReturns()
        {
        return getSignature().getReturns();
        }

    /**
     * @return the method's return types as a Tuple
     */
    public TypeConstant getReturnsAsTuple()
        {
        return getConstantPool().ensureTupleType(getRawReturns());
        }

    /**
     * Note: This is just a helper method; the "whether this refers to a function or not" question
     * belongs to either the component or the type info, and not to the constant, but we need this
     * information.
     *
     * @return true iff this method represents a function
     */
    public boolean isFunction()
        {
        assert !isNascent();
        MethodStructure method = (MethodStructure) getComponent();

        // we treat an absence of a component as a sign that the method is virtual
        // (because when this code was written, that could only occur on a method "cap")
        return method != null && method.isFunction();
        }

    /**
     * Note: This is just a helper method; the "whether this refers to a constructor or not"
     * question belongs to either the component or the type info, and not to the constant, but we
     * need this information.
     *
     * @return true iff this method represents a constructor
     */
    public boolean isConstructor()
        {
        assert !isNascent();
        MethodStructure method = (MethodStructure) getComponent();

        // we treat an absence of a component as a sign that the method is virtual
        // (because when this code was written, that could only occur on a method "cap")
        return method != null && method.isConstructor();
        }

    /**
     * @return true iff this method is nested directly inside of a class
     */
    public boolean isTopLevel()
        {
        return getParentConstant().getParentConstant().isClass();
        }

    /**
     * Bjarne Lambda is a function that performs the following transformation:
     *      (t, a1, a2) -> t.m(a1, a2)
     * where "m" is this method and "t" is a target argument of the {@link #getNamespace host} type.
     * (In Java, this is known as a "method handle".)
     *
     * @return the TypeConstant that represents a Bjarne lambda for this method
     */
    public TypeConstant getBjarneLambdaType()
        {
        assert !isFunction();
        return m_constSig.asBjarneLambdaType(getConstantPool(), getNamespace().getType());
        }


    // ----- GenericTypeResolver interface ---------------------------------------------------------

    @Override
    public TypeConstant resolveGenericType(String sFormalName)
        {
        MethodStructure method = (MethodStructure) getComponent();
        if (method != null)
            {
            // look for a name match only amongst the method's formal type parameters
            // and replace it with this method's formal type parameter type
            for (int i = 0, c = method.getTypeParamCount(); i < c; i++)
                {
                Parameter param = method.getParam(i);

                if (sFormalName.equals(param.getName()))
                    {
                    return param.asTypeParameterConstant(this).getType();
                    }
                }
            }
        return null;
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public MultiMethodConstant getParentConstant()
        {
        return m_constParent;
        }

    @Override
    public IdentityConstant getNamespace()
        {
        return getParentConstant().getNamespace();
        }

    @Override
    public String getName()
        {
        return getParentConstant().getName();
        }

    @Override
    protected StringBuilder buildPath()
        {
        return getParentConstant().buildPath()
                .append(getPathElementString());
        }

    @Override
    public Object getPathElement()
        {
        return isLambda()
                ? Integer.valueOf(m_iLambda)
                : m_constSig;
        }

    @Override
    public String getPathElementString()
        {
        StringBuilder sb = new StringBuilder();

        if (isNascent())
            {
            sb.append('[')
              .append(m_iLambda)
              .append(']');
            }
        else
            {
            sb.append('(');
            TypeConstant[] aParamType = getRawParams();
            for (int i = 0, c = aParamType.length; i < c; i++)
                {
                TypeConstant typeParam = aParamType[i];
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                sb.append(typeParam.getValueString());
                }
            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public boolean trailingSegmentEquals(IdentityConstant idThat)
        {
        return idThat instanceof MethodConstant that && (isLambda()
                ? this.m_iLambda       == that.m_iLambda
                : this.m_constSig.equals(that.m_constSig));
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensureMethodConstant(that, m_constSig);
        }

    @Override
    public TypeConstant getType()
        {
        TypeConstant type = m_type;
        if (type == null)
            {
            if (isFunction())
                {
                type = getSignature().asFunctionType();
                }
            else
                {
                ConstantPool     pool     = getConstantPool();
                IdentityConstant idTarget = getNamespace();
                TypeConstant     typeTarget;

                if (idTarget.isClass())
                    {
                    typeTarget = ((ClassStructure) idTarget.getComponent()).getFormalType();
                    if (isConstructor())
                        {
                        typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.STRUCT);
                        }
                    }
                else
                    {
                    typeTarget = idTarget.getType();
                    }
                type = getSignature().asMethodType(pool, typeTarget);
                }

            if (!type.containsUnresolved())
                {
                m_type = type;
                }
            }
        return type;
        }

    @Override
    public Object getNestedIdentity()
        {
        // method can be identified with only a signature, assuming it is not recursively nested
        return getNamespace().isNested()
                ? getCanonicalNestedIdentity()
                : getPathElement();
        }

    @Override
    public Object resolveNestedIdentity(ConstantPool pool, GenericTypeResolver resolver)
        {
        MethodStructure method = (MethodStructure) getComponent();
        if (resolver != null && method != null && method.isFunction())
            {
            // avoid calling "resolveGenericTypes" for functions
            resolver = null;
            }
        return getNamespace().isNested()
                ? resolver == null
                    ? getCanonicalNestedIdentity()
                    : new NestedIdentity(resolver)
                : getSignature().resolveGenericTypes(pool, resolver);
        }

    @Override
    public MethodStructure relocateNestedIdentity(ClassStructure clz)
        {
        assert !isLambda();
        Component parent = getNamespace().relocateNestedIdentity(clz);
        return parent == null
                ? null
                : parent.findMethod(getSignature());
        }

    @Override
    public MethodConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that)
        {
        assert !isLambda();
        return pool.ensureMethodConstant(
                getParentConstant().ensureNestedIdentity(pool, that), getSignature());
        }

    @Override
    public TypeConstant getValueType(ConstantPool pool, TypeConstant typeTarget)
        {
        SignatureConstant sig       = getSignature();
        boolean           fFunction = isFunction();

        if (fFunction)
            {
            assert typeTarget == null;
            }
        else
            {
            if (typeTarget == null)
                {
                typeTarget = ((ClassStructure) getClassIdentity().getComponent()).getFormalType();
                }
            sig = sig.resolveAutoNarrowing(pool, typeTarget, null);
            }

        MethodStructure method = (MethodStructure) getComponent();
        if (method != null)
            {
            int cTypeParams = method.getTypeParamCount();
            if (cTypeParams > 0)
                {
                sig = sig.truncateParams(cTypeParams, sig.getParamCount() - cTypeParams);
                }
            }
        return fFunction
                ? sig.asFunctionType()
                : sig.asMethodType(pool, typeTarget);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Method;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        if (m_constSig != null)
            {
            visitor.accept(m_constSig);
            }
        }

    @Override
    public boolean isValueCacheable()
        {
        // a Method handle type is context specific (see xRTMethod.createConstHandle)
        return isFunction();
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached()
            && (super.containsUnresolved() || isNascent() || getSignature().containsUnresolved());
        }

    @Override
    public MethodConstant resolveTypedefs()
        {
        MultiMethodConstant idOldParent = getParentConstant();
        MultiMethodConstant idNewParent = (MultiMethodConstant) idOldParent.resolveTypedefs();

        SignatureConstant sigOld = m_constSig;
        SignatureConstant sigNew = sigOld == null ? null : sigOld.resolveTypedefs();
        if (idOldParent == idNewParent && sigNew == sigOld)
            {
            return this;
            }

        ConstantPool pool = getConstantPool();
        return (MethodConstant) pool.register(
                new MethodConstant(pool, idNewParent, sigNew, m_iLambda));
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof MethodConstant that))
            {
            return -1;
            }

        int n = this.m_constParent.compareTo(that.m_constParent);
        if (n == 0)
            {
            n = this.m_iLambda - that.m_iLambda;
            if (n == 0)
                {
                SignatureConstant sigThis = this.m_constSig;
                SignatureConstant sigThat = that.m_constSig;
                if (sigThis != null && sigThat != null)
                    {
                    return sigThis.compareTo(sigThat);
                    }
                // we can only get here for a nascent lambda, in which case the signature isn't
                // really part of the identity
                }
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return isNascent()
                ? getPathElementString()
                : m_constSig.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_type = null;

        // there is a possibility of creating a method for a lambda hosted by another lambda
        // while the containing lambda has not yet been injected with the signature
        m_constParent = (MultiMethodConstant) pool.register(m_constParent);
        m_constSig    = (SignatureConstant  ) pool.register(m_constSig   );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        m_type = null;

        assert !isNascent();

        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constParent.getPosition());
        writePackedLong(out, Constant.indexOf(m_constSig));
        writePackedLong(out, m_iLambda);
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(getName());
        IdentityConstant idParent = getNamespace();
        while (idParent.isNested())
            {
            switch (idParent.getFormat())
                {
                case Method:
                case Property:
                    sb.insert(0, idParent.getName() + '#');
                    break;

                default:
                    break;
                }
            idParent = idParent.getNamespace();
            }
        sb.insert(0, ", name=");
        sb.insert(0, "host=" + idParent.getName());

        if (!isNascent())
            {
            sb.append(", signature=")
              .append(getSignature().getValueString());
            }

        if (isLambda())
            {
            sb.append(", lambda=")
              .append(m_iLambda);
            }

        return sb.toString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    protected int computeHashCode()
        {
        return Hash.of(m_constParent,
               Hash.of(m_iLambda,
               Hash.of(m_constSig)));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of this
     * method.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the signature of this
     * method.
     */
    private int m_iSig;

    /**
     * The constant that represents the parent of this method.
     */
    private MultiMethodConstant m_constParent;

    /**
     * The constant that represents the signature of this method.
     */
    private SignatureConstant m_constSig;

    /**
     * The lambda synthetic identity, separate from the signature.
     */
    private final int m_iLambda;

    /**
     * Cached type.
     */
    private transient TypeConstant m_type;
    }