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

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Method constant. A method constant uniquely identifies a method structure within a
 * named multi-method structure (a group of methods by the same name).
 */
public class MethodConstant
        extends IdentityConstant
    {
    // ----- constructors --------------------------------------------------------------------------

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
    protected MethodConstant(ConstantPool pool, MultiMethodConstant constParent, SignatureConstant constSig, int iLambda)
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

    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return true iff the method is a lambda
     */
    public boolean isLambda()
        {
        // a missing signature can only occur for a lambda
        // a lambda identity always occurs for a lambda
        boolean fLambda = m_iLambda > 0;
        assert fLambda || m_constSig != null;
        assert fLambda == getName().equals("->");
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
     * Note: This is just a helper method; the "whether this refers to a function or not" question
     * belongs to either the component or the type info, and not to the constant, but we need this
     * information.
     *
     * @return true iff this method represents a function
     */
    boolean isFunction()
        {
        assert !isNascent();
        MethodStructure method = (MethodStructure) getComponent();

        // we treat an absence of a component as a sign that the method is virtual
        // (because when this code was written, that could only occur on a method "cap")
        return method != null && method.isFunction();
        }

    /**
     * If any of this method's signature components are auto-narrowing (or have any references to
     * auto-narrowing types), replace any auto-narrowing portion with an explicit class identity in
     * the context of the specified target.
     *
     * Note 1: this functionality is not applicable to functions.
     * Note 2: the target type must be "isA" of this method's containing class
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param typeTarget  the type of a method's target (null if the target is the containing class)
     *
     * @return the SignatureConstant with explicit identities swapped in for any auto-narrowing
     *         identities
     */
    public SignatureConstant resolveAutoNarrowing(ConstantPool pool, TypeConstant typeTarget)
        {
        assert !isFunction();
        assert typeTarget == null || typeTarget.isA(getClassIdentity().getType());

        return getSignature().resolveAutoNarrowing(pool, typeTarget);
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
    public boolean trailingSegmentEquals(IdentityConstant that)
        {
        return that instanceof MethodConstant && (isLambda()
                ? this.m_iLambda == ((MethodConstant) that).m_iLambda
                : this.m_constSig.equals(((MethodConstant) that).m_constSig));
        }

    @Override
    public TypeConstant getType()
        {
        TypeConstant type = m_type;
        if (type == null)
            {
            // Note, that the implementation doesn't differentiate between methods and functions,
            // making both the methods and the functions be of the "Function" type
            // (@see InvocationExpression.generateArguments)
            type = getSignature().asFunctionType();
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
                ? new NestedIdentity()
                : getPathElement();
        }

    @Override
    public Object resolveNestedIdentity(ConstantPool pool, GenericTypeResolver resolver)
        {
        // REVIEW: should we resolveAutoNarrowing()
        if (getComponent() == null)
            {
            // absence of the Component means that this constant is synthetic (e.g. a "capped" method)
            // and as such, already resolved
            resolver = null;
            }
        return getNamespace().isNested()
                ? new NestedIdentity(resolver)
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
    public IdentityConstant ensureNestedIdentity(IdentityConstant that)
        {
        assert !isLambda();
        return that.getConstantPool().ensureMethodConstant(
                getParentConstant().ensureNestedIdentity(that), getSignature());
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return getSignature().isAutoNarrowing();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Method;
        }

    @Override
    public TypeConstant getRefType(TypeConstant typeTarget)
        {
        if (isFunction())
            {
            assert typeTarget == null;
            return getSignature().getRefType(null);
            }

        return resolveAutoNarrowing(getConstantPool(), typeTarget).getRefType(typeTarget);
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
    public boolean containsUnresolved()
        {
        // the constant is considered to be unresolved until the signature is resolved; however, a
        // nascent lambda identity is considered to be resolved because its identity is its lambda
        // index (not the signature)
        return super.containsUnresolved() || !isNascent() && getSignature().containsUnresolved();
        }

    @Override
    public MethodConstant resolveTypedefs()
        {
        MultiMethodConstant idOldParent = getParentConstant();
        MultiMethodConstant idNewParent = (MultiMethodConstant) idOldParent.resolveTypedefs();

        SignatureConstant sigOld = m_constSig;
        SignatureConstant sigNew = sigOld == null ? null : sigOld.resolveTypedefs();
        return idOldParent == idNewParent && sigNew == sigOld
                ? this
                : (MethodConstant) getConstantPool().register(new MethodConstant(
                        getConstantPool(), idNewParent, sigNew, m_iLambda));
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        MethodConstant that = (MethodConstant) obj;
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

                // WARNING! this is very unorthodox behavior. what we're about to do is to mutate
                // a constant deep inside of its "equals()" call chain. that should be illegal, bad,
                // naughty, etc., but it *isn't* because this is a lambda, and the signature isn't
                // really part of the identity, i.e. it's just "cached" information, so we need to
                // make sure that once the signature is assigned, that all of the identities that
                // point to the lambda use the same signature (in reality, there will only be one)
                if (sigThis != null)
                    {
                    that.setSignature(sigThis);
                    }
                else if (sigThat != null)
                    {
                    this.setSignature(sigThat);
                    }

                return 0;
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
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constParent = (MultiMethodConstant) pool.getConstant(m_iParent);
        m_constSig    = (SignatureConstant  ) pool.getConstant(m_iSig   );
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        // TODO - since we go ahead and build everything even after we hit otherwise-fatal errors, this assertion is currently invalid
        // assert !isNascent();

        m_constParent = (MultiMethodConstant) pool.register(m_constParent);
        m_constSig    = (SignatureConstant  ) pool.register(m_constSig   );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // TODO - since we go ahead and build everything even after we hit otherwise-fatal errors, this assertion is currently invalid
        // assert !isNascent();

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
        while (idParent != null)
            {
            switch (idParent.getFormat())
                {
                case Method:
                case Property:
                    sb.insert(0, idParent.getName() + '#');
                    idParent = idParent.getNamespace();
                    break;

                default:
                    idParent = null;
                }
            }
        sb.insert(0, "name=");

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
    public int hashCode()
        {
        return m_constParent.hashCode() * 17
                + (isLambda() ? m_iLambda : m_constSig.hashCode());
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
    private int m_iLambda;

    /**
     * Cached type.
     */
    private transient TypeConstant m_type;
    }
