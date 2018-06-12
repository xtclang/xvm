package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant that will eventually be replaced with a real identity constant.
 */
public class UnresolvedNameConstant
        extends PseudoConstant
        implements ResolvableConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public UnresolvedNameConstant(ConstantPool pool, String[] names, boolean fExplicitlyNonNarrowing)
        {
        super(pool);
        this.m_asName    = names;
        this.m_fNoNarrow = fExplicitlyNonNarrowing;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the name of the constant
     */
    public String getName()
        {
        if (m_constId instanceof IdentityConstant)
            {
            return ((IdentityConstant) m_constId).getName();
            }

        String[]      names  = this.m_asName;
        StringBuilder sb     = new StringBuilder();
        for (int i = 0, c = names.length; i < c; ++i)
            {
            if (i > 0)
                {
                sb.append('.');
                }
            sb.append(names[i]);
            }
        return sb.toString();
        }

    /**
     * @return the number of simple names in the unresolved name
     */
    public int getNameCount()
        {
        return m_asName.length;
        }

    /**
     * @param i  the name index, <tt>0 <= i < getNameCount()</tt>
     * @return the i-th simple name in the unresolved name
     */
    public String getName(int i)
        {
        return m_asName[i];
        }

    /**
     * @return true if the UnresolvedNameConstant has been resolved
     */
    public boolean isNameResolved()
        {
        return m_constId != null;
        }


    // ----- ResolvableConstant methods ------------------------------------------------------------

    @Override
    public Constant getResolvedConstant()
        {
        return m_constId;
        }

    @Override
    public void resolve(Constant constant)
        {
        assert this.m_constId == null || this.m_constId == constant || this.m_constId.equals(constant);
        assert !(constant instanceof TypeConstant);
        this.m_constId = constant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isNameResolved()
                ? m_constId.getFormat()
                : Format.UnresolvedName;
        }

    @Override
    public boolean isValue()
        {
        return isNameResolved()
                ? m_constId.isValue()
                : super.isValue();
        }

    @Override
    public boolean isClass()
        {
        return isNameResolved()
                ? m_constId.isClass()
                : super.isClass();
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return isNameResolved()
                ? m_constId.isAutoNarrowing()
                : super.isAutoNarrowing();
        }

    @Override
    public boolean isProperty()
        {
        return isNameResolved()
                ? m_constId.isProperty()
                : super.isProperty();
        }

    @Override
    public boolean containsUnresolved()
        {
        Constant constant = unwrap();
        return constant instanceof ResolvableConstant || constant.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        if (m_constId != null)
            {
            visitor.accept(m_constId);
            }
        }

    @Override
    public Constant resolveTypedefs()
        {
        return m_constId == null
                ? this
                : m_constId.resolveTypedefs();
        }

    @Override
    protected void setPosition(int iPos)
        {
        throw new UnsupportedOperationException("unresolved: " + this);
        }

    @Override
    protected Object getLocator()
        {
        if (isNameResolved())
            {
            Constant constId = unwrap();
            if (constId instanceof IdentityConstant)
                {
                return ((IdentityConstant) m_constId).getLocator();
                }
            else if (constId instanceof PseudoConstant)
                {
                return ((PseudoConstant) m_constId).getLocator();
                }
            }
        return null;
        }

    @Override
    public String getValueString()
        {
        return isNameResolved()
                ? m_constId.getValueString()
                : (getName() + (m_fNoNarrow ? "!" : ""));
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (that instanceof ResolvableConstant)
            {
            that = ((ResolvableConstant) that).unwrap();
            }

        if (isNameResolved())
            {
            return unwrap().compareTo(that);
            }

        if (that instanceof UnresolvedNameConstant)
            {
            String[] asThis = this.m_asName;
            String[] asThat = ((UnresolvedNameConstant) that).m_asName;
            int      cThis  = asThis.length;
            int      cThat  = asThat.length;
            for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
                {
                int n = asThis[i].compareTo(asThat[i]);
                if (n != 0)
                    {
                    return n;
                    }
                }
            int n = cThis - cThat;
            if (n == 0)
                {
                n = (this.m_fNoNarrow ? 1 : 0) - (((UnresolvedNameConstant) that).m_fNoNarrow ? 1 : 0);
                }
            return n;
            }

        // need to return a value that allows for stable sorts, but unless this==that, the
        // details can never be equal
        return this == that ? 0 : -1;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        if (isNameResolved())
            {
            m_constId = pool.register(m_constId);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        Constant constId = unwrap();
        if (constId instanceof IdentityConstant)
            {
            ((IdentityConstant) constId).assemble(out);
            }
        else if (constId instanceof PseudoConstant)
            {
            ((PseudoConstant) constId).assemble(out);
            }
        else
            {
            throw new IllegalStateException("unresolved: " + getName());
            }
        }

    @Override
    public String getDescription()
        {
        return isNameResolved()
                ? "(resolved) " + m_constId.getDescription()
                : "name=" + getName();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        if (isNameResolved())
            {
            return m_constId.hashCode();
            }
        else
            {
            int      nHash  = 0;
            String[] names  = this.m_asName;
            for (int i = 0, c = names.length; i < c; ++i)
                {
                nHash ^= names[i].hashCode();
                }
            return nHash;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The unresolved name, as an array of simple names.
     */
    private String[] m_asName;

    /**
     * The resolved constant, or null if the name has not yet been resolved to a constant.
     */
    private Constant m_constId;

    /**
     * True iff the type name is explicitly non-narrowing.
     */
    private boolean m_fNoNarrow;
    }
