package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the annotation of another type constant.
 */
public class AnnotatedTypeConstant
        extends TypeConstant
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
    public AnnotatedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_annotation = new Annotation(pool, in);
        m_iType      = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalArgumentException("annotation class required");
            }

        if (aconstParam != null)
            {
            checkElementsNonNull(aconstParam);
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_annotation = new Annotation(pool, constClass, aconstParam);
        m_constType  = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the class of the annotation
     */
    public Constant getAnnotationClass()
        {
        return m_annotation.getAnnotationClass();
        }

    /**
     * @return an array of constants which are the parameters for the annotation
     */
    public Constant[] getAnnotationParams()
        {
        return m_annotation.getParams();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
        }

    @Override
    public boolean isAnnotated()
        {
        return true;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        return constResolved == constOriginal
                ? this
                : getConstantPool().ensureAnnotatedTypeConstant(getAnnotationClass(),
                        getAnnotationParams(), constResolved);
        }

    @Override
    public boolean isNullable()
        {
        return m_constType.isNullable();
        }

    @Override
    public TypeConstant nonNullable()
        {
        return isNullable()
                ? getConstantPool().ensureAnnotatedTypeConstant(getAnnotationClass(),
                        getAnnotationParams(), m_constType.nonNullable())
                : this;
        }

    @Override
    protected TypeConstant unwrapForCongruence()
        {
        // TODO this is wrong, but the annotations aren't being applied correctly right now, so defer ...
        return m_constType.unwrapForCongruence();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AnnotatedType;
        }

    @Override
    public Constant simplify()
        {
        m_annotation.simplify();
        m_constType = (TypeConstant) m_constType.simplify();
        return this;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_annotation.containsUnresolved() || m_constType.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        m_annotation.forEachUnderlying(visitor);
        visitor.accept(m_constType);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        AnnotatedTypeConstant that = (AnnotatedTypeConstant) obj;
        int n = this.m_annotation.compareTo(that.m_annotation);

        if (n == 0)
            {
            n = this.m_constType.compareTo(that.m_constType);
            }

        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_annotation.toString())
          .append(' ')
          .append(m_constType.getValueString());

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_annotation.disassemble(in);
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_annotation.registerConstants(pool);
        m_constType = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        m_annotation.assemble(out);
        writePackedLong(out, indexOf(m_constType));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_annotation.hashCode() ^ m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The annotation.
     */
    private Annotation m_annotation;

    /**
     * During disassembly, this holds the index of the type constant of the type being annotated.
     */
    private int m_iType;

    /**
     * The type being annotated.
     */
    private TypeConstant m_constType;
    }
