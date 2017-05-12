
package org.xvm.asm;


import java.util.List;


/**
 * A Component representing more than one sibling by the same name or identity constant.
 *
 * @author cp 2017.05.12
 */
public class CompositeComponent
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a composite component.
     *
     * @param parent    the parent (which may itself be a composite)
     * @param siblings  the siblings 
     */
    protected CompositeComponent(Component parent, List<Component> siblings)
        {
        super(parent);
        m_siblings = siblings;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Constant getIdentityConstant()
        {
        // this is a legal request iff all of the siblings have the same identity constant
        Constant constId = null;
        for (Component sibling : m_siblings)
            {
            if (constId == null)
                {
                constId = sibling.getIdentityConstant();
                }
            else if (!constId.equals(sibling.getIdentityConstant()))
                {
                throw new UnsupportedOperationException(
                        "can't ask identity constant of a composite with diff identity constants: "
                        + constId + ", " + sibling.getIdentityConstant());
                }
            }
        return constId;
        }

    @Override
    public Format getFormat()
        {
        // this is a legal request iff all of the siblings have the same format
        Format format = null;
        for (Component sibling : m_siblings)
            {
            if (format == null)
                {
                format = sibling.getFormat();
                }
            else if (format != sibling.getFormat())
                {
                throw new UnsupportedOperationException(
                        "can't ask format of a composite with diff formats: "
                                + format + ", " + sibling.getFormat());
                }
            }
        return format;
        }

    @Override
    public Access getAccess()
        {
        // this is a legal request iff all of the siblings have the same access
        Access access = null;
        for (Component sibling : m_siblings)
            {
            if (access == null)
                {
                access = sibling.getAccess();
                }
            else if (access != sibling.getAccess())
                {
                throw new UnsupportedOperationException(
                        "can't ask access of a composite with diff accesss: "
                                + access + ", " + sibling.getAccess());
                }
            }
        return access;
        }

    @Override
    protected void setAccess(Access access)
        {
        for (Component sibling : m_siblings)
            {
            sibling.setAccess(access);
            }
        }

    @Override
    public boolean isAbstract()
        {
        // this is a legal request iff all of the siblings have the same abstract
        boolean fAbstract = false;
        boolean fFirst    = true;
        for (Component sibling : m_siblings)
            {
            if (fFirst)
                {
                fAbstract = sibling.isAbstract();
                fFirst    = false;
                }
            else if (fAbstract != sibling.isAbstract())
                {
                throw new UnsupportedOperationException(
                        "can't ask abstract of a composite with diff abstract settings");
                }
            }
        return fAbstract;
        }

    @Override
    protected void setAbstract(boolean fAbstract)
        {
        for (Component sibling : m_siblings)
            {
            sibling.setAbstract(fAbstract);
            }
        }

    @Override
    public boolean isStatic()
        {
        // this is a legal request iff all of the siblings have the same static
        boolean fStatic = false;
        boolean fFirst    = true;
        for (Component sibling : m_siblings)
            {
            if (fFirst)
                {
                fStatic = sibling.isStatic();
                fFirst    = false;
                }
            else if (fStatic != sibling.isStatic())
                {
                throw new UnsupportedOperationException(
                        "can't ask static of a composite with diff static settings");
                }
            }
        return fStatic;
        }

    @Override
    protected void setStatic(boolean fStatic)
        {
        for (Component sibling : m_siblings)
            {
            sibling.setStatic(fStatic);
            }
        }

    @Override
    public boolean isSynthetic()
        {
        // this is a legal request iff all of the siblings have the same synthetic
        boolean fSynthetic = false;
        boolean fFirst    = true;
        for (Component sibling : m_siblings)
            {
            if (fFirst)
                {
                fSynthetic = sibling.isSynthetic();
                fFirst    = false;
                }
            else if (fSynthetic != sibling.isSynthetic())
                {
                throw new UnsupportedOperationException(
                        "can't ask synthetic of a composite with diff synthetic settings");
                }
            }
        return fSynthetic;
        }

    @Override
    protected void setSynthetic(boolean fSynthetic)
        {
        for (Component sibling : m_siblings)
            {
            sibling.setSynthetic(fSynthetic);
            }
        }

    @Override
    public String getName()
        {
        // this is a legal request iff all of the siblings have the same name
        String sName = null;
        for (Component sibling : m_siblings)
            {
            if (sName == null)
                {
                sName = sibling.getName();
                }
            else if (sName != sibling.getName())
                {
                throw new UnsupportedOperationException(
                        "can't ask name of a composite with diff names: "
                                + sName + ", " + sibling.getName());
                }
            }
        return sName;
        }

    @Override
    protected Component getEldestSibling()
        {
        return m_siblings.get(0).getEldestSibling();
        }

    @Override
    protected boolean isBodyIdentical(Component that)
        {
        return false;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The siblings represented by this component.
     */
    private List<Component> m_siblings;
    }
