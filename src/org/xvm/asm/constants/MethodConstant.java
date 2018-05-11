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
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent required");
            }

        if (constSig == null)
            {
            throw new IllegalArgumentException("signature required");
            }

        m_constParent = constParent;
        m_constSig    = constSig;
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
        this(pool, constParent, pool.ensureSignatureConstant(constParent.getName(), params, returns));
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the method's signature constant
     */
    public SignatureConstant getSignature()
        {
        return m_constSig;
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
        return m_constSig;
        }

    @Override
    public String getPathElementString()
        {
        StringBuilder sb = new StringBuilder();

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
        return sb.toString();
        }

    @Override
    public boolean trailingSegmentEquals(IdentityConstant that)
        {
        return that instanceof MethodConstant && this.m_constSig.equals(((MethodConstant) that).m_constSig);
        }

    @Override
    public Object getNestedIdentity()
        {
        // method can be identified with only a signature, assuming it is not recursively nested
        return getNamespace().isNested()
                ? new NestedIdentity()
                : getSignature();
        }

    @Override
    public Object resolveNestedIdentity(GenericTypeResolver resolver)
        {
        // REVIEW: should we resolveAutoNarrowing()
        return getNamespace().isNested()
                ? new NestedIdentity(resolver)
                : getSignature().resolveGenericTypes(resolver);
        }

    @Override
    public MethodStructure relocateNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().relocateNestedIdentity(clz);
        return parent == null
                ? null
                : parent.findMethod(getSignature());
        }

    @Override
    public IdentityConstant ensureNestedIdentity(IdentityConstant that)
        {
        return that.getConstantPool().ensureMethodConstant(
                getParentConstant().ensureNestedIdentity(that), getSignature());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Method;
        }

    @Override
    public TypeConstant getRefType()
        {
        ConstantPool    pool    = getConstantPool();
        MethodStructure method  = (MethodStructure) getComponent();
        TypeConstant    params  = pool.ensureParameterizedTypeConstant(pool.typeTuple(), getRawParams());
        TypeConstant    returns = pool.ensureParameterizedTypeConstant(pool.typeTuple(), getRawReturns());
        return method.isStatic()
                // Function<Tuple<ParamTypes...>, Tuple<ReturnTypes...>>
                ? pool.ensureParameterizedTypeConstant(pool.typeFunction(), params, returns)
                // Method<TargetType, Tuple<ParamTypes...>, Tuple<ReturnTypes...>>
                : pool.ensureParameterizedTypeConstant(pool.typeMethod(),
                        getNamespace().getType(), params, returns);
        }

    @Override
    public Constant simplify()
        {
        m_constParent = (MultiMethodConstant) m_constParent.simplify();
        m_constSig    = (SignatureConstant  ) m_constSig.simplify();
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        visitor.accept(m_constSig);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        MethodConstant that = (MethodConstant) obj;
        int n = this.m_constParent.compareTo(that.m_constParent);
        if (n == 0)
            {
            n = this.m_constSig.compareTo(that.m_constSig);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constSig.getValueString();
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
        m_constParent = (MultiMethodConstant) pool.register(m_constParent);
        m_constSig    = (SignatureConstant  ) pool.register(m_constSig   );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constParent.getPosition());
        writePackedLong(out, m_constSig.getPosition());
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

        return "name=" + sb.toString()
                + ", signature=" + getSignature().getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() * 17 + m_constSig.hashCode();
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
    }
