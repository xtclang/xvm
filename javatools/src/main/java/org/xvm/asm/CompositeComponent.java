package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.PrintWriter;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;

import org.xvm.util.IdentityArrayList;


/**
 * A Component representing more than one sibling by the same name or identity constant.
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
        f_siblings = siblings;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a read-only list of components that are represented by this composite component
     */
    public List<Component> components()
        {
        List<Component> list = f_siblings;
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public IdentityConstant getIdentityConstant()
        {
        // this is a legal request iff all of the siblings have the same identity constant
        IdentityConstant constId = null;
        for (Component sibling : f_siblings)
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

    /**
     * @return true if the composite component refers to more than one identity
     */
    public boolean isAmbiguous()
        {
        IdentityConstant constId = null;
        for (Component sibling : f_siblings)
            {
            if (constId == null)
                {
                constId = sibling.getIdentityConstant();
                }
            else if (!constId.equals(sibling.getIdentityConstant()))
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public Format getFormat()
        {
        // this is a legal request iff all of the siblings have the same format
        Format format = null;
        for (Component sibling : f_siblings)
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
        for (Component sibling : f_siblings)
            {
            if (access == null)
                {
                access = sibling.getAccess();
                }
            else if (access != sibling.getAccess())
                {
                throw new UnsupportedOperationException(
                        "can't ask access of a composite with diff access: "
                                + access + ", " + sibling.getAccess());
                }
            }
        return access;
        }

    @Override
    public void setAccess(Access access)
        {
        for (Component sibling : f_siblings)
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
        for (Component sibling : f_siblings)
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
    public void setAbstract(boolean fAbstract)
        {
        for (Component sibling : f_siblings)
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
        for (Component sibling : f_siblings)
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
    public void setStatic(boolean fStatic)
        {
        for (Component sibling : f_siblings)
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
        for (Component sibling : f_siblings)
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
    public void setSynthetic(boolean fSynthetic)
        {
        for (Component sibling : f_siblings)
            {
            sibling.setSynthetic(fSynthetic);
            }
        }

    @Override
    public String getName()
        {
        // this is a legal request iff all of the siblings have the same name
        String sName = null;
        for (Component sibling : f_siblings)
            {
            if (sName == null)
                {
                sName = sibling.getName();
                }
            else if (!sName.equals(sibling.getName()))
                {
                throw new UnsupportedOperationException(
                        "can't ask name of a composite with diff names: "
                                + sName + ", " + sibling.getName());
                }
            }
        return sName;
        }

    @Override
    public boolean isGloballyVisible()
        {
        boolean fVisible = true;
        for (Component sibling : f_siblings)
            {
            if (!sibling.isGloballyVisible())
                {
                fVisible = false;
                break;
                }
            }
        return fVisible;
        }

    @Override
    protected boolean isChildLessVisible()
        {
        boolean fAsVisible = true;
        for (Component sibling : f_siblings)
            {
            if (!sibling.isChildLessVisible())
                {
                fAsVisible = false;
                break;
                }
            }
        return !fAsVisible;
        }

    @Override
    protected Component getEldestSibling()
        {
        return f_siblings.get(0).getEldestSibling();
        }

    @Override
    public boolean isPackageContainer()
        {
        for (Component sibling : f_siblings)
            {
            if (!sibling.isPackageContainer())
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean isClassContainer()
        {
        for (Component sibling : f_siblings)
            {
            if (!sibling.isClassContainer())
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        for (Component sibling : f_siblings)
            {
            if (!sibling.isMethodContainer())
                {
                return false;
                }
            }
        return true;
        }

    @Override
    public Map<String, Component> getChildByNameMap()
        {
        return f_siblings.get(0).getChildByNameMap();
        }

    @Override
    public Map<String, Component> ensureChildByNameMap()
        {
        return f_siblings.get(0).ensureChildByNameMap();
        }

    @Override
    protected boolean addChild(Component child)
        {
        // TODO - figure out how to add a child to multiple components
        throw new UnsupportedOperationException();
        }

    @Override
    protected void addAndCondition(ConditionalConstant cond)
        {
        for (Component sibling : f_siblings)
            {
            sibling.addAndCondition(cond);
            }
        }

    @Override
    protected void addOrCondition(ConditionalConstant cond)
        {
        for (Component sibling : f_siblings)
            {
            sibling.addOrCondition(cond);
            }
        }

    @Override
    protected boolean isBodyIdentical(Component component)
        {
        if (component instanceof CompositeComponent that)
            {
            List<Component> listThis = this.f_siblings;
            List<Component> listThat = that.f_siblings;
            if (listThis.size() == listThat.size())
                {
                for (int i = 0, c = listThis.size(); i < c; ++i)
                    {
                    if (!listThis.get(i).isBodyIdentical(listThat.get(i)))
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
    public Component getChild(Constant constId)
        {
        IdentityArrayList<Component> listChild = new IdentityArrayList<>();
        for (Component sibling : f_siblings)
            {
            Component child = sibling.getChild(constId);
            if (child != null)
                {
                if (child instanceof CompositeComponent composite)
                    {
                    for (Component eachChild : composite.f_siblings)
                        {
                        listChild.addIfAbsent(eachChild);
                        }
                    }
                else
                    {
                    listChild.addIfAbsent(child);
                    }
                }
            }

        if (listChild.isEmpty())
            {
            return null;
            }

        if (listChild.size() == 1)
            {
            return listChild.get(0);
            }

        return new CompositeComponent(this, listChild);
        }

    @Override
    public Component getChild(String sName)
        {
        IdentityArrayList<Component> listChild = new IdentityArrayList<>();
        for (Component sibling : f_siblings)
            {
            Component child = sibling.getChild(sName);
            if (child != null)
                {
                if (child instanceof CompositeComponent composite)
                    {
                    for (Component eachChild : composite.f_siblings)
                        {
                        listChild.addIfAbsent(eachChild);
                        }
                    }
                else
                    {
                    listChild.addIfAbsent(child);
                    }
                }
            }

        if (listChild.isEmpty())
            {
            return null;
            }

        if (listChild.size() == 1)
            {
            return listChild.get(0);
            }

        return new CompositeComponent(this, listChild);
        }

    @Override
    protected void disassembleChildren(DataInput in, boolean fLazy)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void registerChildrenConstants(ConstantPool pool)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void assembleChildren(DataOutput out)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected Component cloneBody()
        {
        // this can be implemented, but it's not immediately clear what would be explicitly shared
        // vs. not shared as the result of the clone
        throw new UnsupportedOperationException();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        // TODO this is not correct, if some of the structures have additional "contained" structures, i.e. need Component.getBodyContained()
        return f_siblings.get(0).getContained();
        }

    @Override
    public boolean isModified()
        {
        for (Component sibling : f_siblings)
            {
            if (sibling.isModified())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    protected boolean isBodyModified()
        {
        for (Component sibling : f_siblings)
            {
            if (sibling.isBodyModified())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    protected void markModified()
        {
        for (Component sibling : f_siblings)
            {
            sibling.markModified();
            }
        }

    @Override
    protected void resetModified()
        {
        for (Component sibling : f_siblings)
            {
            sibling.resetModified();
            }
        }

    @Override
    protected void disassemble(DataInput in)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void assemble(DataOutput out)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(this);

        for (Component sibling : f_siblings)
            {
            sibling.dump(out, nextIndent(sIndent));
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int n = 0;
        for (Component sibling : f_siblings)
            {
            n ^= sibling.hashCode();
            }
        return n;
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof CompositeComponent that)
            {
            return this.f_siblings.equals(that.f_siblings);
            }
        return false;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("CompositeComponent{");

        List<Component> list = f_siblings;
        for (int i = 0, c = list.size(); i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }

            sb.append('[')
              .append(i)
              .append("]=")
              .append(list.get(i));
            }

        sb.append('}');
        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The siblings represented by this component.
     */
    private final List<Component> f_siblings;
    }