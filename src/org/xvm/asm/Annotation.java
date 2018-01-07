package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.function.Consumer;

import org.xvm.asm.Component.Format;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the annotation of another type constant.
 */
public class Annotation
        extends XvmStructure
        implements Comparable<Annotation>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public Annotation(ConstantPool pool, DataInput in)
            throws IOException
        {
        super(pool);

        m_iClass = readIndex(in);

        int cParams = readMagnitude(in);
        if (cParams > 0)
            {
            int[] aiParam = new int[cParams];
            for (int i = 1; i <= cParams; ++i)
                {
                aiParam[i] = readIndex(in);
                }
            m_aiParam = aiParam;
            }
        }

    /**
     * Construct an annotation.
     *
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     */
    public Annotation(Constant constClass, Constant[] aconstParam)
        {
        super(constClass.getConstantPool());

        if (constClass == null)
            {
            throw new IllegalArgumentException("annotation class required");
            }

        if (aconstParam != null)
            {
            checkElementsNonNull(aconstParam);
            }

        m_constClass = constClass;
        m_aParams    = aconstParam == null ? Constant.NO_CONSTS : aconstParam;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the class of the annotation
     */
    public Constant getAnnotationClass()
        {
        return m_constClass;
        }

    /**
     * @return an array of constants which are the parameters for the annotation
     */
    public Constant[] getParams()
        {
        return m_aParams;
        }


    // ----- Constant helpers ----------------------------------------------------------------------

    /**
     * Helper for Constant.
     */
    public void simplify()
        {
        Constant[] aParams = m_aParams;
        for (int i = 0, c = aParams.length; i < c; ++i)
            {
            Constant constOld = aParams[i];
            Constant constNew = constOld.simplify();
            if (constNew != constOld)
                {
                aParams[i] = constNew;
                }
            }
        }

    /**
     * Helper for Constant.
     */
    public boolean containsUnresolved()
        {
        if (m_constClass.containsUnresolved())
            {
            return true;
            }
        for (Constant param : m_aParams)
            {
            if (param.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Helper for Constant.
     */
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constClass);
        for (Constant param : m_aParams)
            {
            visitor.accept(param);
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void markModified()
        {
        // annotations are basically constants
        throw new UnsupportedOperationException();
        }

    @Override
    public void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constClass = (ClassConstant) pool.getConstant(m_iClass);

        int cParams = m_aiParam == null ? 0 : m_aiParam.length;
        if (cParams == 0)
            {
            m_aParams = Constant.NO_CONSTS;
            }
        else
            {
            Constant[] aParams = new Constant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                aParams[i] = pool.getConstant(m_aiParam[i]);
                }
            m_aParams = aParams;
            }
        }

    @Override
    public void registerConstants(ConstantPool pool)
        {
        m_constClass = pool.register(m_constClass);

        Constant[] aParams = m_aParams;
        for (int i = 0, c = aParams.length; i < c; ++i)
            {
            Constant constOld = aParams[i];
            Constant constNew = pool.register(constOld);
            if (constNew != constOld)
                {
                aParams[i] = constNew;
                }
            }
        }

    @Override
    public void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_constClass.getPosition());
        writePackedLong(out, m_aParams.length);
        for (Constant param : m_aParams)
            {
            writePackedLong(out, param.getPosition());
            }
        }

    @Override
    public boolean validate(ErrorListener errlist)
        {
        boolean fHalt = super.validate(errlist);

        // it must be a mixin type
        Constant constClass = m_constClass;
        if (!(constClass instanceof ClassConstant
                && ((ClassConstant) constClass).getComponent().getFormat() == Format.MIXIN))
            {
            fHalt |= log(errlist, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN, constClass.getValueString());
            }

        // TODO validate the parameters against the mixin definition

        return fHalt;
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        int cParams = m_aParams.length;

        sb.append("class=")
          .append(m_constClass.getValueString())
          .append(", params=")
          .append(cParams);

        if (cParams > 0)
            {
            sb.append(", values=(");
            for (int i = 0; i < cParams; ++i)
                {
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                sb.append(m_aParams[i].getValueString());
                }
            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());
        }


    // ----- Comparable interface ------------------------------------------------------------------

    @Override
    public int compareTo(Annotation that)
        {
        int n = this.m_constClass.compareTo(that.m_constClass);

        if (n == 0)
            {
            Constant[] aThisParam = this.m_aParams;
            Constant[] aThatParam = that.m_aParams;
            for (int i = 0, c = Math.min(aThisParam.length, aThatParam.length); i < c; ++i)
                {
                n = aThisParam[i].compareTo(aThatParam[i]);
                if (n != 0)
                    {
                    return n;
                    }
                }
            n = aThisParam.length - aThatParam.length;
            }

        return n;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        Constant[] aParams = m_aParams;
        int        cParams = aParams.length;
        int        n       = m_constClass.hashCode() + cParams;
        for (int i = 0; i < cParams; ++i)
            {
            n *= 11 + aParams[i].hashCode();
            }
        return n;
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof Annotation)
            {
            Annotation that       = (Annotation) obj;
            Constant[] aThisParam = this.m_aParams;
            Constant[] aThatParam = that.m_aParams;
            if (this.m_constClass.equals(that.m_constClass) && aThisParam.length == aThatParam.length)
                {
                for (int i = 0, c = aThisParam.length; i < c; ++i)
                    {
                    if (!aThisParam[i].equals(aThatParam[i]))
                        {
                        return false;
                        }
                    }
                return true;
                }
            }

        return false;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('@')
          .append(m_constClass.getValueString());

        if (m_aParams.length > 0)
            {
            sb.append('(');

            boolean first = true;
            for (Constant param : m_aParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.getValueString());
                }

            sb.append(')');
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of annotations.
     */
    public static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    /**
     * During disassembly, this holds the index of the class constant of the annotation.
     */
    private int m_iClass;

    /**
     * During disassembly, this holds the index of the the annotation parameters.
     */
    private int[] m_aiParam;

    /**
     * The annotating class.
     */
    private Constant m_constClass;

    /**
     * The annotation parameters.
     */
    private Constant[] m_aParams;
    }
