package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.NamedConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypedefConstant;

import org.xvm.asm.MethodStructure.ConcurrencySafety;

import org.xvm.compiler.Constants;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The Component data structure is the base class for the module, package, class, property, method,
 * and other data structures that make up the structural components of the module format. The
 * Component encapsulates a number of complex aspects of the XVM module format, most notably the
 * conditional structure of XVM modules.
 * <p/>
 * The Component has an identity, tracks some standard flags / settings, and keeps track of whether
 * it has been modified.
 * <p/>
 * Here is the Component containment model, with container type on the left and containee type
 * across the top:
 * <p/>
 * <code><pre>
 *           Module  Package  Class  MultiMethod  Method  Property  |  Conditional
 * File        x                                                    |
 * Module              x       x       x                    x       |    x (version only)
 * Package             x       x       x                    x       |    x
 * Class                       x       x                    x       |    x
 * Property                            x                            |    x
 * MultiMethod                                      x               |
 * Method                      x       x                    x       |    x
 * </pre></code>
 * <p/>
 * Based on the containment model, of these types, there are three groups of containment:
 * <ul>
 * <li><i>(Multi-)Method</i></li>
 * <li><i>(Multi-)Method + Property + Class</i></li>
 * <li><i>(Multi-)Method + Property + Class + Package</i></li>
 * </ul>
 * <p/>
 * Normally, an XVM structure has a single parent structure and any number of child structures, but
 * a Component can differ dramatically from this model, in that it can have any number of parent
 * Components (only one of which at most is valid for a given condition), and it can have any number
 * of child Components, of which only some are (perhaps none is) appropriate for a given condition.
 * <p/>
 * The persistent form of a Component is relatively complicated, in order to handle the potentially
 * conditional nature of the component, the ability to have several components (called siblings)
 * that share the same spot in the namespace (based on condition), and the ability to defer the
 * deserialization of the component's children. The first byte of the persistent component either
 * has the {@link #CONDITIONAL_BIT} set or it doesn't; if the bit is not set, then the first byte
 * is actually the first byte of the component's "body", but if it is set, then the next value in
 * the stream is the number of siblings, followed by pairs of condition constant id and component
 * body. After the component body (or after the last component body, if the condition bit was set),
 * there is a length-encoded "children" section, which contains all of the nested components; a
 * length of 0 indicates that there are no children. The children section is composed of the number
 * of child components, followed by a sequence of that many components.
 */
public abstract class Component
        extends XvmStructure
        implements Documentable, Cloneable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a component.
     *
     * @param xsParent    the parent, or null if this is a file structure
     * @param access      the accessibility of this component
     * @param fAbstract   the abstractness of this component
     * @param fStatic     the staticness of this component
     * @param fSynthetic  the syntheticness of this component
     * @param format      the format (meta type for the structure) of this component
     * @param constId     the constant that identifies this component, or null if this is a file
     *                    structure
     * @param condition   the optional condition that mandates the existence of this structure
     */
    protected Component(XvmStructure xsParent, Access access, boolean fAbstract, boolean fStatic,
                        boolean fSynthetic, Format format, IdentityConstant constId, ConditionalConstant condition)
        {
        this(xsParent, (format.ordinal() << FORMAT_SHIFT) | (access.ordinal() << ACCESS_SHIFT)
                | (fAbstract ? ABSTRACT_BIT : 0) | (fStatic ? STATIC_BIT : 0)
                | (fSynthetic ? SYNTHETIC_BIT : 0), constId, condition);
        }

    /**
     * Construct a component.
     *
     * @param xsParent    the parent, or null if this is a file structure
     * @param nFlags      the flags specifying the accessibility, abstractness, staticness,
     *                    syntheticness, and the format of this component
     * @param constId     the constant that identifies this component, or null if this is a file
     *                    structure
     * @param condition   the optional condition that mandates the existence of this structure
     */
    protected Component(XvmStructure xsParent, int nFlags, IdentityConstant constId, ConditionalConstant condition)
        {
        super(xsParent);
        assert (xsParent == null) == (this instanceof FileStructure);   // file doesn't have a parent
        assert (constId == null) == (this instanceof FileStructure);    // file doesn't have constId
        assert condition == null || !(this instanceof FileStructure);   // file can't be conditional

        if (constId != null)
            {
            constId = (IdentityConstant) constId.resolveTypedefs();
            constId.resetCachedInfo();
            }

        m_nFlags  = (short) nFlags;
        m_cond    = condition;
        m_constId = constId;
        }

    /**
     * Package private constructor used by the CompositeComponent.
     *
     * @param parent  the parent of the composite component
     */
    Component(Component parent)
        {
        super(parent);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Each Component has a parent. The one exception is the file structure, which is not contained
     * within another component.
     * <p/>
     * This is a far more complex request than it first appears, because of the possibility of
     * conditions. While an XvmStructure has one and only one Containing XvmStructure, each
     * component can have more than one parent, depending on the condition. Imagine for example
     * a protected class that contains a method, and in a subsequent version the class is modified
     * to be a public class; from the method's point of view, there are two different classes that
     * are its parent, depending on the current assembler context. (It would be an assertion error
     * to ask such a question if the assembler context were not configured accordingly.)
     *
     * @return the parent Component of this Component
     */
    public Component getParent()
        {
        Component    parent;
        XvmStructure containing = getContaining();
        while (true)
            {
            if (containing == null)
                {
                assert getFormat() == Format.FILE;
                return null;
                }

            if (containing instanceof Component)
                {
                parent = (Component) containing;
                break;
                }

            containing = containing.getContaining();
            }

        if (parent.getCondition() == null)
            {
            // if there is no condition on the parent, then there is only one possible parent;
            // note that a file component cannot have a condition
            return parent;
            }
        assert parent.getFormat() != Format.FILE;

        // need to get the grandparent and ask it for the parent
        Component grandparent = parent.getParent();
        return parent instanceof MethodStructure
                ? grandparent.getChild(parent.getIdentityConstant())
                : grandparent.getChild(parent.getName());
        }

    /**
     * @return the first component walking up the parentage chain of this component that is a
     *         ClassStructure, or null if none can be found
     */
    public ClassStructure getContainingClass()
        {
        return getContainingClass(true);
        }

    /**
     * Find the first component walking up the parentage chain of this component that is a
     * ClassStructure, or null if none can be found.
     *
     * @param fAllowAnonymous  if false, skip the anonymous classes
     *
     * @return the first ClassStructure parent component
     */
    public ClassStructure getContainingClass(boolean fAllowAnonymous)
        {
        Component parent = getParent();
        while (parent != null)
            {
            if (parent instanceof ClassStructure &&
                    (fAllowAnonymous || !((ClassStructure) parent).isAnonInnerClass()))
                {
                return (ClassStructure) parent;
                }
            parent = parent.getParent();
            }
        return null;
        }

    /**
     * Each Component is identified by a constant. The one exception is the file structure, which
     * contains components, but is not technically itself a component in the XVM sense.
     *
     * @return  the constant that identifies the component, or null for a File component
     */
    public IdentityConstant getIdentityConstant()
        {
        return m_constId;
        }

    /**
     * @param idNew  the new identity to use for this component
     */
    protected void replaceThisIdentityConstant(IdentityConstant idNew)
        {
        IdentityConstant idOld = m_constId;
        for (Iterator<Component> iter = siblings(); iter.hasNext(); )
            {
            iter.next().m_constId = idNew;
            }
        Component parent = getParent();
        if (parent != null)
            {
            parent.replaceChildIdentityConstant(idOld, idNew);
            }
        }

    /**
     * Replace all references to children that use the old identity with the new identity.
     *
     * @param idOld  the old identity
     * @param idNew  the new identity to use instead of the old identity
     */
    protected void replaceChildIdentityConstant(IdentityConstant idOld, IdentityConstant idNew)
        {
        // nothing to do unless the name changed, which we don't support anyhow
        assert idOld.getName().equals(idNew.getName());
        }

    /**
     * @return the Format that corresponds to this Component
     */
    public Format getFormat()
        {
        return Format.valueOf((m_nFlags & FORMAT_MASK) >>> FORMAT_SHIFT);
        }

    /**
     * @return the Access for this Component
     */
    public Access getAccess()
        {
        return Access.valueOf((m_nFlags & ACCESS_MASK) >>> ACCESS_SHIFT);
        }

    /**
     * Specify the accessibility of the component.
     *
     * @param access  the accessibility of the component
     */
    public void setAccess(Access access)
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~ACCESS_MASK) | (access.ordinal() << ACCESS_SHIFT);
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * @return true iff the component is marked as abstract
     */
    public boolean isAbstract()
        {
        return (m_nFlags & ABSTRACT_BIT) != 0;
        }

    /**
     * Specify whether or not the component is abstract.
     *
     * @param fAbstract  true to specify the component is abstract; false otherwise
     */
    public void setAbstract(boolean fAbstract)
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~ABSTRACT_BIT) | (fAbstract ? ABSTRACT_BIT : 0);
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * @return true iff the component is marked as static
     */
    public boolean isStatic()
        {
        return (m_nFlags & STATIC_BIT) != 0;
        }

    /**
     * Specify whether or not the component is static.
     *
     * @param fStatic  true to specify the component is static; false otherwise
     */
    public void setStatic(boolean fStatic)
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~STATIC_BIT) | (fStatic ? STATIC_BIT : 0);
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * @return true iff the component is marked as synthetic
     */
    public boolean isSynthetic()
        {
        return (m_nFlags & SYNTHETIC_BIT) != 0;
        }

    /**
     * Specify whether or not the component is synthetic.
     *
     * @param fSynthetic  true to specify the component is synthetic; false otherwise
     */
    public void setSynthetic(boolean fSynthetic)
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~SYNTHETIC_BIT) | (fSynthetic ? SYNTHETIC_BIT : 0);
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * @return true iff the component is marked as having a conditional return
     */
    protected boolean isConditionalReturn()
        {
        return (m_nFlags & COND_RET_BIT) != 0;
        }

    /**
     * Specify whether or not the method has a conditional return value.
     *
     * @param fConditional  true to specify the method has a conditional return; false otherwise
     */
    protected void setConditionalReturn(boolean fConditional)
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~COND_RET_BIT) | (fConditional ? COND_RET_BIT : 0);
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * @return true iff the auxiliary flag is set
     */
    protected boolean isAuxiliary()
        {
        return (m_nFlags & AUXILIARY_BIT) != 0;
        }

    /**
     * Set the auxiliary flag. The semantic of this flag depends on the specific component
     * implementation.
     */
    protected void markAuxiliary()
        {
        int nFlagsOld = m_nFlags;
        int nFlagsNew = (nFlagsOld & ~AUXILIARY_BIT) | AUXILIARY_BIT;
        if (nFlagsNew != nFlagsOld)
            {
            m_nFlags = (short) nFlagsNew;
            markModified();
            }
        }

    /**
     * Obtain the name of the component. All components have a name, although the purpose of the
     * name varies slightly for several components:
     * <ul>
     * <li>The Package, various Class types (Interface, Class, Const, Enum, Mixin, and Service),
     *     Property, and MultiMethod all are identified within their parent component by their name,
     *     using a NamedConstant;</li>
     * <li>The Method has a name, but it is identified by its signature, because it is a child of a
     *     MultiMethod component;</li>
     * <li>The Module has a name, but it is a full qualified name (composed of a simple name and a
     *     domain name); and</li>
     * <li>The File has a name, which defaults to the module's simple name plus the ".xtc"
     *     extension (if the actual file name is not known).</li>
     * </ul>
     * @return
     */
    public String getName()
        {
        return getIdentityConstant().getName();
        }

    /**
     * @return the unqualified form of the component name
     */
    public String getSimpleName()
        {
        return getName();
        }

    /**
     * @return true if the identity of this component is visible from anywhere
     */
    public boolean isGloballyVisible()
        {
        Component parent = getParent();
        return parent.isGloballyVisible() && !parent.isChildLessVisible();
        }

    /**
     * @return true if the identity of a child component is less visible than the identity of this
     *         component; this tends to be true when this component is a method
     */
    protected boolean isChildLessVisible()
        {
        return false;
        }

    /**
     * Determine if this component allows any type within it to be auto-narrowing.
     *
     * @return true iff the component can contain auto-narrowing types
     */
    public boolean isAutoNarrowingAllowed()
        {
        return false;
        }

    /**
     * Obtain the class contributions as a list.
     *
     * @return a read-only list of class contributions
     */
    public List<Contribution> getContributionsAsList()
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            return Collections.EMPTY_LIST;
            }
        assert (list = Collections.unmodifiableList(m_listContribs)) != null;
        return list;
        }

    /**
     * @return false iff all of the contributions have fully resolved types
     */
    public boolean containsUnresolvedContribution()
        {
        return m_listContribs != null && m_listContribs
                .stream()
                .anyMatch(Contribution::containsUnresolved);
        }

    /**
     * Find a contribution of a specified type.
     *
     * @param composition  the contribution category
     *
     * @return a first (if more than one) contribution matching the specified category
     *         or null if none found
     */
    public Contribution findContribution(Composition composition)
        {
        List<Contribution> list = m_listContribs;
        if (list != null)
            {
            for (Contribution contrib : list)
                {
                if (contrib.getComposition() == composition)
                    {
                    return contrib;
                    }
                }
            }
        return null;
        }

    /**
     * Add a class type contribution.
     *
     * @param composition  the contribution category
     * @param constType    the contribution class type
     */
    public void addContribution(Composition composition, TypeConstant constType)
        {
        addContribution(new Contribution(composition, constType));
        }

    /**
     * Add a module import contribution.
     *
     * @param composition  the contribution type
     * @param constModule   the contribution class
     */
    public void addImport(Composition composition, ModuleConstant constModule)
        {
        addContribution(new Contribution(composition, constModule));
        }

    /**
     * Add an annotation.
     *
     * @param constAnno    the annotation class type
     * @param aconstParam  the annotation parameters (optional)
     */
    public void addAnnotation(IdentityConstant constAnno, Constant... aconstParam)
        {
        addAnnotation(getConstantPool().ensureAnnotation(constAnno, aconstParam));
        }

    /**
     * Add an annotation.
     *
     * @param annotation  the Annotation
     */
    public void addAnnotation(Annotation annotation)
        {
        addContribution(new Contribution(annotation));
        }

    /**
     * Add an interface delegation.
     *
     * @param constClass  the class type to delegate
     * @param constProp   the property specifying the reference to delegate to
     */
    public void addDelegation(TypeConstant constClass, PropertyConstant constProp)
        {
        addContribution(new Contribution(constClass, constProp));
        }

    /**
     * Add a mixin via an "incorporates" or "incorporates conditional".
     *
     * @param constClass
     * @param mapConstraints
     */
    public void addIncorporates(TypeConstant constClass,
                                Map<String, TypeConstant> mapConstraints)
        {
        ListMap<StringConstant, TypeConstant> map = null;
        if (mapConstraints != null && !mapConstraints.isEmpty())
            {
            ConstantPool pool = getConstantPool();

            map = new ListMap<>();
            for (Map.Entry<String, TypeConstant> entry : mapConstraints.entrySet())
                {
                map.put(pool.ensureStringConstant(entry.getKey()), entry.getValue());
                }
            }
        addContribution(new Contribution(constClass, map));
        }

    /**
     * Helper to add a contribution to the lazily-instantiated list of contributions.
     *
     * @param contrib  the contribution to add to the end of the list
     */
    protected void addContribution(Contribution contrib)
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            m_listContribs = list = new ArrayList<>();
            }
        else if (!list.isEmpty() && (contrib.getComposition() == Composition.Into ||
                                     contrib.getComposition() == Composition.Extends))
            {
            // order is "into" and then "extends" and then everything else
            for (ListIterator<Contribution> listIterator = list.listIterator(); listIterator.hasNext(); )
                {
                Contribution contribNext = listIterator.next();
                if (contribNext.getComposition() != Composition.Into)
                    {
                    listIterator.previous();
                    listIterator.add(contrib);
                    return;
                    }
                }
            }

        list.add(contrib);
        markModified();
        }

    /**
     * Remove a contribution from the list of contributions.
     *
     * @param contrib  the contribution to remove from the list
     */
    protected void removeContribution(Contribution contrib)
        {
        List<Contribution> list = m_listContribs;
        if (list != null)
            {
            list.remove(contrib);
            }
        }

    /**
     * Determine whether multiple different components (versions, alternatives) may exist for the
     * same component identity. It is possible to work with a "flat" component model, i.e. when no
     * component has any siblings and siblings are disallowed. It is also possible to work with a
     * "combined" component model, in which multiple alternatives may exist for the same component
     * identity.
     *
     * <p/>REVIEW - how to toggle this?
     *
     * @return true iff the component allows multiple conditional children to exist for the same
     *         identity
     */
    protected boolean isSiblingAllowed()
        {
        return getParent().isSiblingAllowed();
        }

    /**
     * @return assuming that this is one of any number of siblings, obtain a reference to the first
     *         sibling (which may be this); never null
     */
    protected Component getEldestSibling()
        {
        Component parent = getParent();
        if (parent == null) // || !isSiblingAllowed())
            {
            return this;
            }

        Component sibling = parent.getChildByNameMap().get(getName());
        assert sibling != null;
        return sibling;
        }

    /**
     * @return assuming that this is one of any number of siblings, obtain a reference to the next
     *         sibling, which may be null to indicate no more siblings
     */
    protected Component getNextSibling()
        {
        return isSiblingAllowed() ? m_sibling : null;
        }

    /**
     * Update this component's reference to its next sibling.
     *
     * @param sibling  a reference to the next sibling (null indicates no more siblings)
     */
    private void setNextSibling(Component sibling)
        {
        assert sibling == null || isSiblingAllowed();
        m_sibling = sibling;
        }

    /**
     * Iterate all of the siblings of this component, including this component.
     *
     * @return an Iterator of sibling components, including this component
     */
    protected Iterator<Component> siblings()
        {
        if (!isSiblingAllowed())
            {
            return new Iterator<>()
                {
                private boolean first = true;

                @Override
                public boolean hasNext()
                    {
                    return first;
                    }

                @Override
                public Component next()
                    {
                    if (first)
                        {
                        first = false;
                        return Component.this;
                        }

                    throw new NoSuchElementException();
                    }
                };
            }

        return new Iterator<>()
            {
            private Component nextSibling = getEldestSibling();

            @Override
            public boolean hasNext()
                {
                return nextSibling != null;
                }

            @Override
            public Component next()
                {
                Component sibling = nextSibling;
                if (sibling == null)
                    {
                    throw new NoSuchElementException();
                    }

                nextSibling = sibling.getNextSibling();
                return sibling;
                }
            };
        }

    /**
     * Obtain a read-only map of all children identified by name.
     * <p/>
     * Note: the returned map does not contain any of the child methods.
     *
     * @return a read-only map from name to child component; never null, even if there are no
     *         children
     */
    public Map<String, Component> getChildByNameMap()
        {
        ensureChildren();
        Map<String, Component> map = m_childByName;
        return map == null ? Collections.EMPTY_MAP : map;
        }

    /**
     * Obtain the actual read/write map of all children that are identified by name.
     * <p/>
     * Note: the returned map does not contain any of the child methods.
     *
     * @return obtain the actual map from name to child component, creating the map if necessary
     */
     public synchronized Map<String, Component> ensureChildByNameMap()
        {
        ensureChildren();

        Map<String, Component> map = m_childByName;
        if (map == null)
            {
            map = new ListMap<>();

            // store the map on every one of the siblings (including this component)
            for (Iterator<Component> siblings = siblings(); siblings.hasNext(); )
                {
                siblings.next().m_childByName = map;
                }

            // the corresponding field on this component should now be initialized
            assert m_childByName == map;
            }
        return map;
        }

    /**
     * Make sure that any deferred child deserialization is complete
     */
     protected synchronized void ensureChildren()
        {
        if (m_abChildren != null)
            {
            // first grab the deferred deserialization bytes and then make sure neither this nor any
            // sibling retains hold of it (since it indicates that deserialization is deferred)
            byte[] ab = m_abChildren;
            for (Iterator<Component> siblings = siblings(); siblings.hasNext(); )
                {
                siblings.next().m_abChildren = null;
                }

            // now read in the children
            DataInput in = new DataInputStream(new ByteArrayInputStream(ab));
            try
                {
                disassembleChildren(in, true);
                }
            catch (IOException e)
                {
                throw new IllegalStateException("IOException occurred in " + getIdentityConstant()
                        + " during deferred read of child components", e);
                }
            }
        }

    /**
     * Visitor pattern for children of this component, optionally including all siblings, and
     * optionally recursively through the remainder of the component hierarchy.
     *
     * @param visitor     the consumer to use as the visitor, passing each child component
     * @param fSiblings   true to visit all siblings; false to visit only the eldest sibling
     * @param fRecursive  true to recursively visit the children of the children, and so on
     */
    public void visitChildren(Consumer<Component> visitor, boolean fSiblings, boolean fRecursive)
        {
        for (Component component : children())
            {
            Component componentEldest = component;

            do
                {
                visitor.accept(component);
                component = component.getNextSibling();
                }
            while (fSiblings && component != null);

            if (fRecursive)
                {
                componentEldest.visitChildren(visitor, fSiblings, fRecursive);
                }
            }
        }

    /**
     * Adopt the specified child.
     *
     * @param child  the new child of this component
     *
     * @return true iff the child was successfully added
     */
    protected boolean addChild(Component child)
        {
        // if the child is a method, it can only be contained by a MultiMethodStructure
        assert !(child instanceof MethodStructure);

        Map<String, Component> kids  = ensureChildByNameMap();
        String                 sName = child.getName();

        Component sibling = kids.get(sName);
        if (sibling == null)
            {
            kids.put(sName, child);
            }
        else if (isSiblingAllowed())
            {
            linkSibling(child, sibling);
            }
        else
            {
            return false;
            }

        markModified();
        return true;
        }

    /**
     * Link a sibling to the specified child's chain.
     *
     * @param child    the child component to link
     * @param sibling  the sibling component to link to
     */
    protected void linkSibling(Component child, Component sibling)
        {
        assert isSiblingAllowed();

        // there has to be a condition that sets the new kid apart from its siblings, but that
        // condition might not be available (resolved) when the kid is created, so defer the
        // check for the existence of the condition and the mutual exclusivity of the condition
        // until much later in the assembly
        // if (child.m_cond == null)
        //     {
        //     throw new IllegalStateException("cannot add child with same ID (" + id
        //             + ") if condition == null");
        //     }
        // if (sibling.m_cond == null)
        //     {
        //     throw new IllegalStateException("cannot add child if sibling with same ID (" + id
        //             + ") has condition == null");
        //     }

        // make sure that the parent is set correctly
        child.setContaining(this);

        // the new kid gets put at the end of the linked list of siblings
        Component lastSibling = sibling;
        Component nextSibling = lastSibling.getNextSibling();
        while (nextSibling != null)
            {
            lastSibling = nextSibling;
            nextSibling = lastSibling.getNextSibling();
            }
        lastSibling.setNextSibling(child);

        child.adoptChildren(sibling);
        }

    /**
     * Adopt the children of the specified component
     *
     * @param that
     */
    protected void adoptChildren(Component that)
        {
        // the child can't have any of its own children; that "merge" functionality is simply
        // not supported by this operation
        assert m_abChildren       == null;
        assert m_childByName      == null;

        // make sure that the various sibling-shared fields are configured
        m_abChildren  = that.m_abChildren;
        m_childByName = that.m_childByName;
        }

    /**
     * Remove the specified child.
     *
     * @param child  the child of this component to remove
     */
    public void removeChild(Component child)
        {
        assert child.getParent() == this;

        Map<String, Component> kids  = ensureChildByNameMap();
        String                 sName = child.getName();

        Component sibling = kids.remove(sName);

        unlinkSibling(kids, sName, child, sibling);
        }

    /**
     * Replace the specified first child and all of its siblings (and their children) with the
     * specified second child and all of its siblings (and their children).
     *
     * @param childOld  the child to remove
     * @param childNew  the child to add
     */
    protected void replaceChild(Component childOld, Component childNew)
        {
        assert childOld != null && childNew != null;
        assert childOld.getParent() == this;
        assert childNew.getParent() == this;
        assert childOld.getIdentityConstant().equals(childNew.getIdentityConstant());

        // warning: brute force
        ensureChildByNameMap().put(childNew.getName(), childNew);
        }

    /**
     * Unlink the sibling from the specified child's chain.
     *
     * @param kids     the map of children
     * @param id       the child id
     * @param child    the child component
     * @param sibling  the sibling component to unlink
     */
    protected void unlinkSibling(Map kids, Object id, Component child, Component sibling)
        {
        if (sibling == child && child.getNextSibling() == null)
            {
            // most common case: the specified child is the only sibling with that id
            markModified();
            return;
            }

        if (sibling == null)
            {
            // the child was not there
            return;
            }

        if (sibling == child)
            {
            // the child to remove is in the head of the linked list
            kids.put(id, child.getNextSibling());
            }
        else
            {
            // the child to remove is in the middle of the linked list;
            // put the linked list back first, then find and remove the child
            kids.put(id, sibling);
            do
                {
                if (sibling.getNextSibling() == child)
                    {
                    sibling.setNextSibling(child.getNextSibling());
                    break;
                    }
                sibling = sibling.getNextSibling();
                }
            while (sibling != null);
            }

        markModified();
        }

    /**
     * @return true if this component can contain packages
     */
    public boolean isPackageContainer()
        {
        return false;
        }

    /**
     * Create and register a PackageStructure with the specified package name.
     *
     * @param access  the accessibility of the package to create
     * @param sName   the simple (unqualified) package name to create
     * @param cond    the conditional constant for the class, or null
     */
    public PackageStructure createPackage(Access access, String sName, ConditionalConstant cond)
        {
        assert sName != null;
        assert access != null;

        if (!isPackageContainer())
            {
            throw new IllegalStateException("this (" + this + ") cannot contain a package");
            }

        // the check for duplicates is deferred, since it is possible (e.g. with conditionals) to
        // have multiple components occupying the same location within the namespace at this point
        // in the compilation

        int              nFlags  = Format.PACKAGE.ordinal() | access.FLAGS;
        PackageConstant  constId = getConstantPool().ensurePackageConstant(getIdentityConstant(), sName);
        PackageStructure struct  = new PackageStructure(this, nFlags, constId, cond);

        return addChild(struct) ? struct : null;
        }

    /**
     * @return true if this component can contain classes and properties
     */
    public boolean isClassContainer()
        {
        return false;
        }

    /**
     * See documentation for the synchronicity property at Service.x.
     *
     * @return the safety value for this method, property or class
     */
    public ConcurrencySafety getConcurrencySafety()
        {
        return getParent().getConcurrencySafety();
        }

    /**
     * Create and register a ClassStructure with the specified class name.
     *
     * @param access  the accessibility of the class to create
     * @param format  the category format of the class
     * @param sName   the simple (unqualified) class name to create
     * @param cond    the conditional constant for the class, or null
     */
    public ClassStructure createClass(Access access, Format format, String sName, ConditionalConstant cond)
        {
        assert sName != null;
        assert access != null;

        if (!isClassContainer())
            {
            throw new IllegalStateException("this (" + this + ") cannot contain a class");
            }

        // the check for duplicates is deferred, since it is possible (e.g. with conditionals) to
        // have multiple components occupying the same location within the namespace at this point
        // in the compilation

        int            nFlags  = format.ordinal() | access.FLAGS;
        ClassConstant  constId = getConstantPool().ensureClassConstant(getIdentityConstant(), sName);
        ClassStructure struct  = new ClassStructure(this, nFlags, constId, cond);

        return addChild(struct) ? struct : null;
        }

    /**
     * @return true iff this component contains a virtual child class; note that this method does
     *         not search contributions to see if any contribution contains a virtual child (which
     *         implies that this component would then also have a virtual child)
     */
    public boolean containsVirtualChild()
        {
        for (Component child : getChildByNameMap().values())
            {
            switch (child.getIdentityConstant().getFormat())
                {
                case Class:
                    if (((ClassStructure) child).isVirtualChild())
                        {
                        return true;
                        }
                    break;
                case Property:
                    if (child.containsVirtualChild())
                        {
                        return true;
                        }
                    break;
                }
            }
        return false;
        }

    /**
     * Create and register a PropertyStructure with the specified name.
     *
     * @param fStatic    true if the property is marked as static
     * @param accessRef  the "Ref" accessibility of the property to create
     * @param accessVar  the "Var" accessibility of the property to create
     * @param constType  the type of the property to create
     * @param sName      the simple (unqualified) property name to create
     */
    public PropertyStructure createProperty(boolean fStatic, Access accessRef, Access accessVar,
            TypeConstant constType, String sName)
        {
        assert sName != null;
        assert accessRef != null;
        assert accessVar == null || accessRef.ordinal() <= accessVar.ordinal();
        assert constType != null;

        if (!isClassContainer())
            {
            throw new IllegalStateException("this (" + this + ") cannot contain a property");
            }

        // the check for duplicates is deferred, since it is possible (thanks to the complexity of
        // conditionals) to have multiple components occupying the same location within the
        // namespace at this point in the compilation
        // Component component = getChild(sName);
        // if (component != null)
        //     {
        //     throw new IllegalStateException("cannot add a class \"" + sName
        //             + "\" because a child with that name already exists: " + component);
        //     }

        int               nFlags  = Format.PROPERTY.ordinal() | accessRef.FLAGS | (fStatic ? STATIC_BIT : 0);
        PropertyConstant  constId = getConstantPool().ensurePropertyConstant(getIdentityConstant(), sName);
        PropertyStructure struct  = new PropertyStructure(this, nFlags, constId, null, accessVar, constType);

        return addChild(struct) ? struct : null;
        }

    /**
     * Create and register a TypedefStructure with the specified name.
     *
     * @param access     the accessibility of the typedef to create
     * @param constType  the type of the typedef to create
     * @param sName      the simple (unqualified) typedef name to create
     *
     * @return the new TypedefStructure
     */
    public TypedefStructure createTypedef(Access access, TypeConstant constType, String sName)
        {
        assert sName != null;
        assert access != null;
        assert constType != null;

        if (!isClassContainer())
            {
            throw new IllegalStateException("this (" + this + ") cannot contain a typedef");
            }

        int              nFlags  = Format.TYPEDEF.ordinal() | access.FLAGS;
        TypedefConstant  constId = getConstantPool().ensureTypedefConstant(getIdentityConstant(),
                sName);
        TypedefStructure struct  = new TypedefStructure(this, nFlags, constId, null);
        struct.setType(constType);

        return addChild(struct) ? struct : null;
        }

    /**
     * @return true if this component can contain multi-methods
     */
    public boolean isMethodContainer()
        {
        return false;
        }

    /**
     * Create a MethodStructure with the specified name, but whose identity may not yet be fully
     * realized / resolved.
     *
     * @param fFunction    true if the method is actually a function (not a method)
     * @param access       the access flag for the method
     * @param annotations  an array of annotations, or null
     * @param returnTypes  the return values of the method
     * @param sName        the method name, or null if the name is unknown
     * @param paramTypes   the parameters for the method
     * @param fHasCode     true indicates that the method is known to have a natural body
     * @param fUsesSuper   true indicates that the method is known to reference "super"
     *
     * @return a new MethodStructure or null if the equivalent method already exists
     */
    public MethodStructure createMethod(boolean fFunction, Access access,
            Annotation[] annotations, Parameter[] returnTypes, String sName, Parameter[] paramTypes,
            boolean fHasCode, boolean fUsesSuper)
        {
        assert sName != null;
        assert access != null;

        MultiMethodStructure multimethod = ensureMultiMethodStructure(sName);
        return multimethod == null
                ? null
                : multimethod.createMethod(fFunction, access, annotations, returnTypes, paramTypes,
                        fHasCode, fUsesSuper);
        }

    public MultiMethodStructure ensureMultiMethodStructure(String sName)
        {
        Component sibling = getChildByNameMap().get(sName);
        while (sibling != null)
            {
            if (sibling instanceof MultiMethodStructure)
                {
                return (MultiMethodStructure) sibling;
                }

            sibling = sibling.getNextSibling();
            }

        MultiMethodConstant  constId = getConstantPool().ensureMultiMethodConstant(getIdentityConstant(), sName);
        MultiMethodStructure struct  = new MultiMethodStructure(this, Format.MULTIMETHOD.ordinal(), constId, null);

        return addChild(struct) ? struct : null;
        }

    /**
     * Add the specified version as a condition on this component.
     *
     * @param ver  the version
     */
    protected void addVersion(Version ver)
        {
        ConditionalConstant cond = getCondition();
        if (cond == null)
            {
            setCondition(getConstantPool().ensureVersionedCondition(ver));
            }
        else
            {
            setCondition(cond.addVersion(ver));
            }
        }

    /**
     * Remove the specified version as a condition from this component.
     *
     * @param ver  the version
     */
    protected void removeVersion(Version ver)
        {
        ConditionalConstant cond = getCondition();
        if (cond != null)
            {
            setCondition(cond.removeVersion(ver));
            }
        }

    /**
     * Modify the condition on this component by adding another required condition.
     *
     * @param cond  the condition to require
     */
    protected void addAndCondition(ConditionalConstant cond)
        {
        if (cond != null)
            {
            ConditionalConstant condOld = m_cond;
            m_cond = condOld == null ? cond : condOld.addAnd(cond);
            markModified();
            }
        }

    /**
     * Modify the condition on this component by adding an alternative condition.
     *
     * @param cond  the alternative condition
     */
    protected void addOrCondition(ConditionalConstant cond)
        {
        if (cond != null)
            {
            ConditionalConstant condOld = m_cond;
            m_cond = condOld == null ? cond : condOld.addOr(cond);
            markModified();
            }
        }

    /**
     * Without comparing the child components, compare this component to another component to
     * determine if their state is identical. This method must be overridden by components that
     * have state in addition to that represented by the identity constant and the component's
     * bit flags.
     *
     * @param that  another component to compare to
     *
     * @return true iff this component's "body" is identical to that component's "body"
     */
    protected boolean isBodyIdentical(Component that)
        {
        return this.m_nFlags == that.m_nFlags
            && this.m_constId.equals(that.m_constId);
        }

    /**
     * Comparing only the child components (recursively), determine if this component's children
     * are identical to that component's children.
     *
     * @param that
     *
     * @return
     */
    protected boolean areChildrenIdentical(Component that)
        {
        ensureChildren();
        return equalChildMaps(this.getChildByNameMap(), that.getChildByNameMap());
        }

    protected boolean equalChildMaps(Map<?, ? extends Component> mapThis,
                                     Map<?, ? extends Component> mapThat)
        {
        if (mapThis.size() != mapThat.size())
            {
            return false;
            }

        if (mapThis.isEmpty())
            {
            return true;
            }

        for (Object key : mapThis.keySet())
            {
            Component childThis = mapThis.get(key);
            Component childThat = mapThat.get(key);

            for (Component eachThis = childThis, eachThat = childThat;
                    eachThis != null || eachThat != null;
                    eachThis = eachThis.getNextSibling(), eachThat = eachThat.getNextSibling())
                {
                if (eachThis == null || eachThat == null)
                    {
                    return false;
                    }
                }
            if (childThat == null)
                {
                return false;
                }
            }

        return true;
        }

    /**
     * Obtain the child that is identified by the specified identity. If more than one child is
     * a match, then a component representing the multiple siblings is created to represent the
     * result.
     *
     * @param constId  the constant identifying the child
     *
     * @return the child component, or null
     */
    public Component getChild(Constant constId)
        {
        if (constId instanceof NamedConstant)
            {
            Component firstSibling = getChildByNameMap().get(((NamedConstant) constId).getName());

            return findLinkedChild(constId, firstSibling);
            }
        return null;
        }

    /**
     * Find a child in the chain with the specified id.
     *
     * @param constId       the id to match
     * @param firstSibling  the first child
     *
     * @return the matching child
     */
    protected Component findLinkedChild(Constant constId, Component firstSibling)
        {
        // common result: nothing for that constant
        if (firstSibling == null)
            {
            return null;
            }

        // common result: exactly one non-conditional match
        if (firstSibling.getNextSibling() == null
                && firstSibling.getIdentityConstant().equals(constId)
                && firstSibling.m_cond == null)
            {
            return firstSibling;
            }

        List<Component> matches = selectMatchingSiblings(firstSibling);
        if (matches.isEmpty())
            {
            return null;
            }

        if (matches.size() == 1)
            {
            return matches.get(0);
            }

        return new CompositeComponent(this, matches);
        }

    /**
     * For all but the multi-method, this obtains a child by the specified dot-delimited path.
     * TODO discuss with GG - do we still need this? (use NestedIdentity instead?)
     *
     * @param sPath  dot-delimited child name path
     *
     * @return the child component or null if cannot be found
     */
    public Component getChildByPath(String sPath)
        {
        int       ofStart = 0;
        int       ofEnd   = sPath.indexOf('.');
        Component parent  = this;

        while (ofEnd >= 0)
            {
            String sName = sPath.substring(ofStart, ofEnd);

            parent = parent.getChild(sName);
            if (parent == null)
                {
                return null;
                }
            ofStart = ofEnd + 1;
            ofEnd   = sPath.indexOf('.', ofStart);
            }
        return parent.getChild(sPath.substring(ofStart));
        }

    /**
     * @return an iterator of any contributions that could contain virtual children
     */
    protected Iterator<IdentityConstant> potentialVirtualChildContributors()
        {
        throw new IllegalStateException();
        }

    /**
     * Starting from this component, search for the child that is a super of the specified virtual
     * child.
     *
     * @param idVirtChild  the ide of the virtual child whose super (class or interface) we are
     *                     searching for
     * @param cDepth       the current depth
     * @param setVisited   TODO
     *
     * @return TODO
     */
    protected Object findVirtualChildSuper(
            IdentityConstant        idVirtChild,
            int                     cDepth,
            Set<IdentityConstant>   setVisited)
        {
        if (!setVisited.add(getIdentityConstant()))
            {
            // already checked this component node
            return null;
            }

        if (cDepth == 0)
            {
            return this;
            }

        // first, attempt to navigate down to the desired child
        IdentityConstant idChild = idVirtChild;
        for (int i = 1; i < cDepth; ++i)
            {
            idChild = idChild.getParentConstant();
            }
        Component child = getChild(idChild.getName());
        if (child != null)
            {
            // TODO verify visibility
            // TODO verify compatibility of the two components, e.g. must be either class or property, property must match property, can't have interface->class, etc.
            // TODO the possibility of compatibility issues probably implies that an error listener should be passed in
            Object oResult = child.findVirtualChildSuper(idVirtChild, cDepth-1, setVisited);
            if (oResult != null)
                {
                return oResult;
                }
            }

        // second, attempt to follow the path to a virtual super child via any contributions
        Iterator<IdentityConstant> iter = potentialVirtualChildContributors();
        if (iter == null)
            {
            return false;
            }

        Object oResult = null;
        while (iter.hasNext())
            {
            IdentityConstant idContrib = iter.next();
            if (idContrib.containsUnresolved())
                {
                oResult = false;
                continue;
                }

            Component component = idContrib.getComponent();
            if (component != null)
                {
                Object o = component.findVirtualChildSuper(idVirtChild, cDepth, setVisited);
                if (o != null)
                    {
                    return o;
                    }
                }
            }

        return oResult;
        }

    /**
     * Helper method to find a method by signature.
     *
     * @param sig  the method signature to find
     *
     * @return the specified MethodStructure, or null
     */
    public MethodStructure findMethod(SignatureConstant sig)
        {
        Component child = getChild(sig.getName());
        return child instanceof MultiMethodStructure
                ? child.findMethod(sig)
                : null;
        }

    /**
     * For all but the multi-method, this obtains a child by the specified name. (Unlike all other
     * components, the multi-method identifies its children by method signature information.)
     *
     * @param sName  the child name
     *
     * @return the child component
     */
    public Component getChild(String sName)
        {
        // there are five cases:
        // 1) no child by that name - return null
        // 2) one unconditional child by that name - return the child
        // 3) a number of children by that name, but no conditions match - return null
        // 4) a number of children by that name, one condition matches - return that child
        // 5) a number of children by that name, multiple conditions match - return a composite child

        // most common result: no child by that name
        Component firstSibling = getChildByNameMap().get(sName);
        if (firstSibling == null)
            {
            return null;
            }

        // common result: exactly one non-conditional match
        if (firstSibling.getNextSibling() == null && firstSibling.m_cond == null)
            {
            return firstSibling;
            }

        List<Component> matches = selectMatchingSiblings(firstSibling);
        if (matches.isEmpty())
            {
            return null;
            }

        if (matches.size() == 1)
            {
            return matches.get(0);
            }

        return new CompositeComponent(this, matches);
        }

    /**
     * @return the number of children
     */
    public int getChildrenCount()
        {
        return m_childByName == null ? 0 : m_childByName.size();
        }

    /**
     * @return true iff this component has children
     */
    public boolean hasChildren()
        {
        ensureChildren();
        return m_childByName != null && !m_childByName.isEmpty();
        }

    /**
     * Obtain a collection of the child components contained within this Component.
     *
     * @return an immutable collection of the component's children
     */
    public Collection<? extends Component> children()
        {
        Collection<Component> children = getChildByNameMap().values();

        assert (children = Collections.unmodifiableCollection(children)) != null;
        return children;
        }

    /**
     * Obtain a list of the child components contained within this Component. Note that this is a
     * fairly expensive operation, because each potential child must be evaluated for inclusion as
     * if it were requested explicitly via {@link #getChild(String)} or {@link #getChild(Constant)}.
     *
     * @return a list of the component's children
     */
    public List<Component> safeChildren()
        {
        List<Component> list = new ArrayList<>();

        for (String sName : getChildByNameMap().keySet())
            {
            Component child = getChild(sName);
            if (child != null)
                {
                list.add(child);
                }
            }

        return list;
        }

    protected List<Component> selectMatchingSiblings(Component firstSibling)
        {
        AssemblerContext ctxAsm  = getFileStructure().getContext();
        LinkerContext    ctxLink = ctxAsm == null ? null : ctxAsm.getLinkerContext();
        List<Component>  matches = null;

        // see which siblings will be present based on what has been required in the current
        // assembler context
        for (Component eachSibling = firstSibling; eachSibling != null; eachSibling = eachSibling.getNextSibling())
            {
            if (ctxLink == null || eachSibling.isPresent(ctxLink))
                {
                if (matches == null)
                    {
                    matches = new ArrayList<>();
                    }
                matches.add(eachSibling);
                }
            }

        return matches == null ? Collections.EMPTY_LIST : matches;
        }

    /**
     * Request that the component determine what the specified name is referring to.
     *
     * @param sName      the name to resolve
     * @param access     the accessibility to this component
     * @param collector  the collector to which the potential name matches will be reported
     *
     * @return the resolution result
     */
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector)
        {
        return resolveContributedName(sName, access, collector, true);
        }

    protected boolean canBeSeen(Access access)
        {
        return access.canSee(getAccess());
        }

    /**
     * Read zero or more child components from the DataInput stream. For a given identity, there may
     * be more than one child component if the child components are conditional. For all components
     * (except for methods i.e. within multi-methods), the children are identified by name.
     *
     * @param in     the DataInput containing the components
     * @param fLazy  true to defer the child deserialization until necessary
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected void disassembleChildren(DataInput in, boolean fLazy)
            throws IOException
        {
        // read component children
        int cKids = readMagnitude(in);
        while (cKids-- > 0)
            {
            ConstantPool pool = getConstantPool();
            Component    kid  = null;

            // read component body (or bodies)
            int n = in.readUnsignedByte();
            if ((n & CONDITIONAL_BIT) == 0)
                {
                // there isn't a conditional multiple-component list, so this is just the first byte of
                // the two-byte FLAGS value (which is the start of the body) for a single component
                n = (n << 8) | in.readUnsignedByte();
                kid = Format.fromFlags(n).instantiate(this, pool.getConstant(readMagnitude(in)), n, null);
                kid.disassemble(in);
                }
            else
                {
                // some number of components, each with a condition
                Component    prevSibling = null;
                int          cSiblings   = readMagnitude(in);
                assert cSiblings > 0;
                for (int i = 0; i < cSiblings; ++i)
                    {
                    ConditionalConstant condition  = (ConditionalConstant) pool.getConstant(readIndex(in));
                    int                 nFlags     = in.readUnsignedShort();
                    Component           curSibling = Format.fromFlags(nFlags).instantiate(
                            this, pool.getConstant(readMagnitude(in)), nFlags, condition);

                    // the remainder of the body of the current sibling is at the current point of
                    // the stream (but do NOT disassemble the children)
                    curSibling.disassemble(in);

                    if (prevSibling == null)
                        {
                        kid = curSibling;
                        }
                    else
                        {
                        prevSibling.setNextSibling(curSibling);
                        }
                    prevSibling = curSibling;
                    }
                }

            // register the eldest sibling in the namespace of this component; this has to be done
            // before recursing to the children for disassembly so that the parent/child refs are
            // already in place, just in case a child asks e.g. for its eldest sibling
            addChild(kid);

            int cb = readMagnitude(in);
            if (cb > 0)
                {
                if (fLazy)
                    {
                    // just read the bytes for the children and store it off for later
                    byte[] ab = new byte[cb];
                    in.readFully(ab);
                    for (Component eachSibling = kid; eachSibling != null; eachSibling = eachSibling.getNextSibling())
                        {
                        // note that every sibling has a copy of all the children; this is because
                        // the byte[] serves as both the storage of those children and an indicator that
                        // the deserialization of the children has been deferred
                        eachSibling.m_abChildren = ab;
                        }
                    }
                else
                    {
                    kid.disassembleChildren(in, fLazy);
                    }
                }
            }
        }

    /**
     * Register all constants used by the child components.
     *
     * @param pool  the ConstantPool with which to register each constant referenced by the child
     *              components
     */
    protected void registerChildrenConstants(ConstantPool pool)
        {
        for (Component child : children())
            {
            registerChildConstants(pool, child);
            }
        }

    /**
     * Register the constants for a child AND all of its siblings, and then recursively for the
     * various children of those siblings.
     *
     * @param pool   the constant pool
     * @param child  the eldest sibling of the siblings to recursively register the constants for
     */
    private void registerChildConstants(ConstantPool pool, Component child)
        {
        for (Component eachSibling = child; eachSibling != null; eachSibling = eachSibling.getNextSibling())
            {
            eachSibling.registerConstants(pool);
            }

        // now register the grand-children (the children of the various siblings we just iterated)
        child.registerChildrenConstants(pool);
        }

    /**
     * Write any child components to the DataOutput stream.
     *
     * @param out  the DataOutput to write the child components to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleChildren(DataOutput out)
            throws IOException
        {
        int cKids = getChildrenCount();
        writePackedLong(out, cKids);

        if (cKids > 0)
            {
            int cActual = 0;

            for (Component child : children())
                {
                assembleChild(out, child);
                ++cActual;
                }

            assert cActual == cKids;
            }
        }

    /**
     * Write a child AND all of its siblings to the DataOutput stream, and then recursively for the
     * various children of those siblings.
     *
     * @param out    the DataOutput to write the child components to
     * @param child  the eldest sibling of the siblings to recursively assemble
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    private void assembleChild(DataOutput out, Component child)
            throws IOException
        {
        if (child.getNextSibling() != null || child.m_cond != null)
            {
            // multiple child / conditional format:
            // first is an indicator that we're using the conditional format
            out.writeByte(CONDITIONAL_BIT);

            // second is the number of kids
            int cSiblings = 0;
            for (Component eachSibling = child; eachSibling != null; eachSibling = eachSibling.getNextSibling())
                {
                ++cSiblings;
                }
            writePackedLong(out, cSiblings);

            // last comes a sequence of siblings, each preceded by its condition
            for (Component eachSibling = child; eachSibling != null; eachSibling = eachSibling.getNextSibling())
                {
                writePackedLong(out, Constant.indexOf(eachSibling.m_cond));
                eachSibling.assemble(out);
                }
            }
        else
            {
            // single child format
            child.assemble(out);
            }

        // children nested under these siblings are length-encoded as a group
        if (child.hasChildren())
            {
            ByteArrayOutputStream outNestedRaw = new ByteArrayOutputStream();
            DataOutputStream outNestedData = new DataOutputStream(outNestedRaw);
            child.assembleChildren(outNestedData);
            byte[] abGrandChildren = outNestedRaw.toByteArray();
            writePackedLong(out, abGrandChildren.length);
            out.write(abGrandChildren);
            }
        else
            {
            writePackedLong(out, 0);
            }
        }

    /**
     * Create a temporary clone of this component, and replace this component with the new clone,
     * such that this component is no longer navigable from its parent.
     *
     * @return a clone of this component
     */
    public Component replaceWithTemporary()
        {
        // re-arrange the siblings so that this is the oldest, because we're about to replace
        // all of the siblings with the clone, so re-ordering them prevents the rest of the
        // siblings from being lost
        Component eldest = getEldestSibling();
        if (this != eldest)
            {
            // start by finding this component in the middle (or at the end) of the sibling list
            Component tail = this.getNextSibling(); // null if this is the end of the list
            Component cur = eldest;
            Component next;
            while ((next = cur.getNextSibling()) != this)
                {
                assert next != null;
                cur = next;
                }

            // remove this from the middle of the list and put it at the head of the sibling list
            cur.setNextSibling(tail);
            this.setNextSibling(eldest);
            }

        Component parent = (Component) this.getContaining();
        Component that   = this.cloneBody();
        assert that.getContaining() == parent;

        if (this.hasChildren())
            {
            that.cloneChildren(this.children());
            }

        parent.replaceChild(this, that);
        return that;
        }

    /**
     * Given a component that previously was replaced using {@link #replaceWithTemporary()}, remove
     * the clone and replace it with the original component.
     *
     * @param that  the Component that was previously replaced with a temporary clone component
     */
    public void replaceTemporaryWith(Component that)
        {
        Component parent = (Component) this.getContaining();
        parent.replaceChild(this, that);
        }

    /**
     * Determine if the specified name is referring to a name introduced by any of the contributions
     * for this class.
     * <p/>
     * Note, that this method is used *before* the integrity of the structures is validated,
     * so must be ready for "infinite recursions", that will be reported later.
     *
     * @param sName       the name to resolve
     * @param access      the accessibility to use to determine if the name is visible
     * @param collector   the collector to which the potential name matches will be reported
     * @param fAllowInto  if false, the "into" contributions should not be looked at
     *
     * @return the resolution result is one of: RESOLVED, UNKNOWN or POSSIBLE
     */
    protected ResolutionResult resolveContributedName(
            String sName, Access access, ResolutionCollector collector, boolean fAllowInto)
        {
        assert access != Access.STRUCT;

        Component child = getChild(sName);
        if (child != null && child.canBeSeen(access))
            {
            switch (child.getIdentityConstant().getFormat())
                {
                case Property:
                case Module:
                case Package:
                case Class:
                case Typedef:
                case MultiMethod:
                    collector.resolvedComponent(child);
                    return ResolutionResult.RESOLVED;
                }
            return ResolutionResult.UNKNOWN;
            }

        // no child by that name; check if it was introduced by a contribution
        NextContribution: for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            if (typeContrib.containsUnresolved())
                {
                return ResolutionResult.POSSIBLE;
                }

            switch (contrib.getComposition())
                {
                case Into:
                    if (!fAllowInto)
                        {
                        continue NextContribution;
                        }
                    access = access.minOf(Access.PROTECTED);
                    break;

                case Delegates:
                case Implements:
                    access = Access.PUBLIC;
                    break;

                case Extends:
                    access = access.minOf(Access.PROTECTED);
                    break;

                case Annotation:
                case Incorporates:
                    fAllowInto = false;
                    access = access.minOf(Access.PROTECTED);
                    break;

                default:
                    throw new IllegalStateException();
                }

            // since some components in the graph that we would need to visit in order to answer the
            // question about generic type parameters may not yet be ready to answer those
            // questions, we rely instead on a virtual child's knowledge of its parents' type
            // parameters to short-circuit that search; we know that the virtual child type can
            // answer the question precisely because it exists (they are created no earlier than a
            // certain stage)
            if (typeContrib.isVirtualChild())
                {
                // check the parent's formal type
                TypeConstant typeParent = typeContrib.getParentType();
                TypeConstant typeFormal = typeParent.resolveGenericType(sName);
                if (typeFormal != null)
                    {
                    if (!typeFormal.isGenericType())
                        {
                        ClassStructure clzParent = (ClassStructure)
                                typeParent.getSingleUnderlyingClass(true).getComponent();
                        typeFormal = clzParent.getFormalType().resolveGenericType(sName);
                        }
                    collector.resolvedConstant(typeFormal.getDefiningConstant());
                    return ResolutionResult.RESOLVED;
                    }
                }

            if (typeContrib.isExplicitClassIdentity(true))
                {
                ClassStructure clzContrib =
                        (ClassStructure) typeContrib.getSingleUnderlyingClass(true).getComponent();

                if (m_FVisited != null && m_FVisited.booleanValue() == fAllowInto)
                    {
                    // recursive contribution
                    collector.getErrorListener().log(Severity.FATAL, Constants.VE_CYCLICAL_CONTRIBUTION,
                            new Object[] {getName(), contrib.getComposition().toString().toLowerCase()}, this);
                    return ResolutionResult.ERROR;
                    }

                m_FVisited = fAllowInto;
                ResolutionResult result =
                        clzContrib.resolveContributedName(sName, access, collector, fAllowInto);
                m_FVisited = null;

                if (result != ResolutionResult.UNKNOWN)
                    {
                    return result;
                    }
                }
            else
                {
                return typeContrib.resolveContributedName(sName, access, null, collector);
                }
            }

        return ResolutionResult.UNKNOWN;
        }

    /**
     * Clone this component's body, but not its siblings nor its children.
     *
     * @return a clone of this component, sans siblings and sans children
     */
    protected Component cloneBody()
        {
        Component that;
        try
            {
            that = (Component) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }

        // deep clone the contributions
        List<Contribution> listContribs = m_listContribs;
        if (listContribs != null)
            {
            List<Contribution> listClone = new ArrayList<>(listContribs.size());

            for (int i = 0, c = listContribs.size(); i< c; i++)
                {
                listClone.add((Contribution) listContribs.get(i).clone());
                }
            that.m_listContribs = listClone;
            }

        that.m_sibling     = null;
        that.m_childByName = null;
        that.m_abChildren  = null;

        return that;
        }

    /**
     * Clone the passed collection child components onto this component.
     *
     * @param collThat  a collection of child components to clone
     */
    protected void cloneChildren(Collection<? extends Component> collThat)
        {
        for (Component childThat : collThat)
            {
            this.addChild(this.cloneChild(childThat));
            }
        }

    /**
     * Clone the passed child AND all of its siblings, adding those clones to this component, and
     * then recursively for the various children of those siblings.
     *
     * @param childThat  the eldest sibling of the siblings to recursively clone
     */
    private Component cloneChild(Component childThat)
        {
        // clone the child ...
        Component childThis = childThat.cloneBody();
        childThis.setContaining(this);

        // ... and the rest of the siblings
        Component childThisPrev = childThis;
        Component childThatPrev = childThat;
        Component childThatNext = childThatPrev.getNextSibling();
        while (childThatNext != null)
            {
            Component childThisNext = childThatNext.cloneBody();
            childThisNext.setContaining(this);
            childThisPrev.setNextSibling(childThisNext);

            childThisPrev = childThisNext;
            childThatPrev = childThatNext;
            childThatNext = childThatPrev.getNextSibling();
            }

        // clone the grand-children nested under these siblings
        if (childThat.hasChildren())
            {
            childThis.cloneChildren(childThat.children());
            }

        return childThis;
        }

    /**
     * This method is used during the load and link stage to allow components to create synthetic
     * children necessary for the runtime.
     */
    protected void synthesizeChildren()
        {
        for (Component child : children())
            {
            child.synthesizeChildren();
            }
        }

    /**
     * Collect all injections necessary for this component.
     *
     * @param setInjections  a set to add injection keys to
     */
    public void collectInjections(Set<InjectionKey> setInjections)
        {
        visitChildren(component -> component.collectInjections(setInjections), false, true);
        }


    // ----- Documentable methods ------------------------------------------------------------------

    @Override
    public String getDocumentation()
        {
        return m_sDoc;
        }

    @Override
    public void setDocumentation(String sDoc)
        {
        m_sDoc = sDoc;
        markModified();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return children().iterator();
        }

    @Override
    public boolean isModified()
        {
        for (Component eachSibling = this; eachSibling != null; eachSibling = eachSibling.getNextSibling())
            {
            if (eachSibling.isBodyModified())
                {
                return true;
                }
            }
        return super.isModified();
        }

    protected boolean isBodyModified()
        {
        return m_fModified;
        }

    @Override
    protected void markModified()
        {
        m_fModified = true;
        }

    @Override
    protected void resetModified()
        {
        for (Component eachSibling = this; eachSibling != null; eachSibling = eachSibling.getNextSibling())
            {
            eachSibling.m_fModified = false;
            }
        super.resetModified();
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("name=")
          .append(getName())
          .append(", format=")
          .append(getFormat())
          .append(", access=")
          .append(getAccess());

        if (isAbstract())
            {
            sb.append(", abstract");
            }
        if (isStatic())
            {
            sb.append(", static");
            }
        if (isSynthetic())
            {
            sb.append(", synthetic");
            }
        if (getNextSibling() != null)
            {
            sb.append(", next-sibling");
            }
        if (m_fModified)
            {
            sb.append(", modified");
            }
        return sb.toString();
        }

    /**
     * {@inheritDoc}
     * <p/>
     * For all but the FileStructure Component, this method applies only to the body of the
     * component and not to its children.
     *
     * @see #disassembleChildren(DataInput, boolean)
     */
    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        assert getContaining() == null || getContaining() instanceof Component;

        // read in the "contributions"
        int c = readMagnitude(in);
        if (c > 0)
            {
            ConstantPool       pool = getConstantPool();
            List<Contribution> list = new ArrayList<>();
            for (int i = 0; i < c; ++i)
                {
                list.add(new Contribution(in, pool));
                }
            m_listContribs = list;
            }
        }

    /**
     * {@inheritDoc}
     * <p/>
     * For all but the FileStructure Component, this method applies only to the body of the
     * component and not to its children.
     *
     * @see #registerChildrenConstants(ConstantPool)
     */
    @Override
    protected void registerConstants(ConstantPool pool)
        {
        assert getContaining() == null || getContaining() instanceof Component;

        m_constId = (IdentityConstant   ) pool.register(m_constId);
        m_cond    = (ConditionalConstant) pool.register(m_cond);

        // register the contributions
        List<Contribution> listContribs = m_listContribs;
        if (listContribs != null  && listContribs.size() > 0)
            {
            for (Contribution contribution : listContribs)
                {
                contribution.registerConstants(pool);
                }
            }
        }

    /**
     * {@inheritDoc}
     * <p/>
     * For all but the FileStructure Component, this method applies only to the body of the
     * component and not to its children.
     *
     * @see #assembleChildren(DataOutput)
     */
    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        assert getContaining() == null || getContaining() instanceof Component;

        out.writeShort(m_nFlags);
        writePackedLong(out, m_constId.getPosition());

        // write out the contributions
        List<Contribution> listContribs = m_listContribs;
        int                cContribs    = listContribs == null ? 0 : listContribs.size();
        writePackedLong(out, cContribs);
        if (cContribs > 0)
            {
            for (Contribution contribution : listContribs)
                {
                contribution.assemble(out);
                }
            }
        }

    @Override
    public ConditionalConstant getCondition()
        {
        return m_cond;
        }

    @Override
    public void setCondition(ConditionalConstant condition)
        {
        m_cond = condition;
        markModified();
        }

    /**
     * Split this component into multiple components based on the specified condition. The result
     * is a CompositeComponent.
     *
     * @param condition  the condition which is used to split this component
     *
     * @return a CompositeComponent that contains both the specified condition and its negation
     */
    public CompositeComponent bifurcateConditional(ConditionalConstant condition)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    /**
     * {@inheritDoc}
     * <p/>
     * For all but the FileStructure Component, this method applies only to the body of the
     * component and not to its children.
     *
     * @see #dumpChildren(PrintWriter, String)
     */
    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(this);

        sIndent = nextIndent(sIndent);

        List<Contribution> listContribs = m_listContribs;
        int                cContribs    = listContribs == null ? 0 : listContribs.size();
        if (cContribs > 0)
            {
            for (int i = 0; i < cContribs; ++i)
                {
                out.println(sIndent + '[' + i + "]=" + listContribs.get(i));
                }
            }
        }

    /**
     * Dump all the children of this component, recursively.
     *
     * @param out      the PrintWriter to dump to
     * @param sIndent  the indentation to use for this level
     */
    protected void dumpChildren(PrintWriter out, String sIndent)
        {
        // go through each named and constant-identified child, and dump it, and its siblings
        for (Component child : children())
            {
            dumpChild(child, out, sIndent);
            }
        }

    /**
     * Dump a child and all of its siblings, and then its children under it.
     *
     * @param child    a child (the eldest sibling)
     * @param out      the PrintWriter to dump to
     * @param sIndent  the indentation to use for this level
     */
    private void dumpChild(Component child, PrintWriter out, String sIndent)
        {
        // dump all of the siblings
        for (Component eachSibling = child; eachSibling != null; eachSibling = eachSibling.getNextSibling())
            {
            eachSibling.dump(out, sIndent);
            }

        // dump the shared children
        child.dumpChildren(out, nextIndent(sIndent));
        }

    /**
     * Temporary: Helper for use in debugger to dump specific information. (REMOVE LATER!)
     *
     * @param sType  the type to evaluate
     *
     * @return a String that has the TypeInfo dump in it
     */
    public String dumpType(String sType)
        {
        ErrorList    errs   = new ErrorList(10);
        Parser       parser = new Parser(new Source(sType + ";"), errs);
        TypeConstant type   = parser.parseType(this);
        if (errs.getSeriousErrorCount() > 0)
            {
            return errs.toString();
            }

        if (type == null)
            {
            return "type could not be resolved: " + sType;
            }

        TypeInfo info = type.ensureTypeInfo(errs);
        if (errs.getSeriousErrorCount() > 0)
            {
            return errs.toString();
            }

        return info + "\n\n" + errs;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof Component that)
            {
            if (!isBodyIdentical(that) || !areChildrenIdentical(that))
                {
                return false;
                }

            // contributions (order is considered important)
            List<Contribution> listThisContribs = this.m_listContribs;
            List<Contribution> listThatContribs = that.m_listContribs;
            int cThisContribs = listThisContribs == null ? 0 : listThisContribs.size();
            int cThatContribs = listThatContribs == null ? 0 : listThatContribs.size();
            return !(cThisContribs != cThatContribs ||
                    (cThisContribs > 0 && !listThisContribs.equals(listThatContribs)));
            }

        return false;
        }

    @Override
    public int hashCode()
        {
        return getIdentityConstant().hashCode();
        }


    // ----- inner class: Format -------------------------------------------------------------------

    /**
     * The Format enumeration defines the multiple different binary formats used to store component
     * information.
     * <p/>
     * Those beginning with "RSVD_" are reserved, and must not be used.
     */
    public enum Format
        {
        INTERFACE,
        CLASS,
        CONST,
        ENUM,
        ENUMVALUE,
        MIXIN,
        SERVICE,
        PACKAGE,
        MODULE,
        TYPEDEF,
        PROPERTY,
        METHOD,
        RSVD_C,
        RSVD_D,
        MULTIMETHOD,
        FILE;

        /**
         * Determine the format from a component's bit-flags value.
         *
         * @param nFlags  the 2-byte component bit-flags value
         *
         * @return the Format specified by the bit flags
         */
        static Format fromFlags(int nFlags)
            {
            return valueOf((nFlags & FORMAT_MASK) >>> FORMAT_SHIFT);
            }

        /**
         * Validate that this format can legally extend another format.
         *
         * @param fmtSuper  the format of the class being extended (the "super" class)
         *
         * @return true if legal; otherwise false
         */
        public boolean isExtendsLegal(Format fmtSuper)
            {
            return switch (this)
                {
                case CLASS                        -> fmtSuper == CLASS;
                case CONST, ENUM, PACKAGE, MODULE -> fmtSuper == CONST || fmtSuper == CLASS;
                case ENUMVALUE                    -> fmtSuper == ENUM;
                case MIXIN                        -> fmtSuper == MIXIN;
                case SERVICE                      -> fmtSuper == SERVICE || fmtSuper == CLASS;
                default                           -> false;
                };
            }

        /**
         * Instantiate a component as it is being read from a stream, reading its body (but NOT its
         * children).
         *
         * @param xsParent   the parent component
         * @param constId    the constant for the new component's identity
         * @param nFlags     the flags that define the common attributes of the component
         * @param condition  the condition under which the component is present, or null
         *
         * @return the new component
         */
        Component instantiate(XvmStructure xsParent, Constant constId, int nFlags, ConditionalConstant condition)
            {
            if (xsParent == null)
                {
                throw new IllegalStateException("parent required");
                }

            return switch (this)
                {
                case MODULE ->
                    new ModuleStructure(xsParent, nFlags, (ModuleConstant) constId, condition);

                case PACKAGE ->
                    new PackageStructure(xsParent, nFlags, (PackageConstant) constId, condition);

                case INTERFACE, CLASS, CONST, ENUM, ENUMVALUE, MIXIN, SERVICE ->
                    new ClassStructure(xsParent, nFlags, (ClassConstant) constId, condition);

                case TYPEDEF ->
                    new TypedefStructure(xsParent, nFlags, (TypedefConstant) constId, condition);

                case PROPERTY ->
                    new PropertyStructure(xsParent, nFlags, (PropertyConstant) constId, condition);

                case MULTIMETHOD ->
                    new MultiMethodStructure(xsParent, nFlags, (MultiMethodConstant) constId, condition);

                case METHOD ->
                    new MethodStructure(xsParent, nFlags, (MethodConstant) constId, condition);

                default ->
                    throw new IllegalStateException("uninstantiable format: " + this);
                };
            }

        public boolean isImplicitlyStatic()
            {
            return switch (this)
                {
                case MODULE, PACKAGE, ENUM, ENUMVALUE ->
                    true;

                case INTERFACE, CLASS, CONST, MIXIN, SERVICE, PROPERTY, MULTIMETHOD, METHOD, TYPEDEF ->
                    false;

                default ->
                    throw new IllegalStateException("unsupported format: " + this);
                };
            }

        public boolean isAutoNarrowingAllowed()
            {
            return switch (this)
                {
                case MODULE, PACKAGE, ENUM, ENUMVALUE, PROPERTY, MULTIMETHOD, METHOD, TYPEDEF ->
                    false;

                case MIXIN, INTERFACE, CLASS, CONST, SERVICE ->
                    true;

                default ->
                    throw new IllegalStateException("unsupported format: " + this);
                };
            }

        /**
         * @return true iff a component of this format has no further ability to resolve by name
         */
        public boolean isDeadEnd()
            {
            return this.compareTo(PROPERTY) > 0;
            }

        /**
         * Look up a Format enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Format enum for the specified ordinal
         */
        public static Format valueOf(int i)
            {
            return FORMATS[i];
            }

        /**
         * All of the Format enums.
         */
        private static final Format[] FORMATS = Format.values();
        }


    // ----- enumeration: Component Composition ----------------------------------------------------

    /**
     * Types of composition.
     */
    public enum Composition
        {
        /**
         * Represents an annotation.
         * <p/>
         * The constant is a TypeConstant. (It could be a ClassConstant, but TypeConstant
         * was selected to keep it compatible with the other compositions.) An annotation has
         * optional annotation parameters, each of which is also a constant from the ConstantPool.
         */
        Annotation,
        /**
         * Represents class inheritance.
         * <p/>
         * The constant is a TypeConstant for the class.
         */
        Extends,
        /**
         * Represents interface inheritance.
         * <p/>
         * The constant is a TypeConstant.
         */
        Implements,
        /**
         * Represents interface inheritance plus default delegation of interface functionality.
         * <p/>
         * The constant is a TypeConstant. A "delegates" composition must specify a property that
         * provides the reference to which it delegates; this is represented by a PropertyConstant.
         */
        Delegates,
        /**
         * Represents that the class being composed is a mixin that applies to the specified type.
         * <p/>
         * The constant is a TypeConstant.
         */
        Into,
        /**
         * Represents the combining-in of a mix-in.
         * <p/>
         * The constant is a TypeConstant.
         */
        Incorporates,
        /**
         * Synthetic (transient) rebasing of a class onto a new category.
         */
        RebasesOnto,
        /**
         * Represents that the package being composed represents an optional module.
         * <p/>
         * The constant is a ModuleConstant.
         */
        ImportOptional,
        /**
         * Represents that the package being composed represents an optional-but-desired module.
         * <p/>
         * The constant is a ModuleConstant.
         */
        ImportDesired,
        /**
         * Represents that the package being composed represents a required module.
         * <p/>
         * The constant is a ModuleConstant.
         */
        ImportRequired,
        /**
         * Represents that the package being composed represents an embedded module.
         * <p/>
         * The constant is a ModuleConstant.
         */
        ImportEmbedded,
        /**
         * Synthetic (transient) composition indicating an equivalency.
         * <p/>
         * The constant is a ClassConstant.
         */
        Equal,
        ;

        /**
         * Look up a Composition enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Composition enum for the specified ordinal
         */
        public static Composition valueOf(int i)
            {
            return COMPOSITIONS[i];
            }

        /**
         * All of the Composition enums.
         */
        private static final Composition[] COMPOSITIONS = Composition.values();
        }


    // ----- inner class: Component Contribution ---------------------------------------------------

    /**
     * Represents one contribution to the definition of a class. A class (with the term used in the
     * abstract sense, meaning any class, interface, mixin, const, enum, or service) can be composed
     * of any number of contributing components.
     */
    public static class Contribution
            implements Cloneable
        {
        /**
         * @see XvmStructure#disassemble(DataInput)
         */
        protected Contribution(DataInput in, ConstantPool pool)
                throws IOException
            {
            m_composition = Composition.valueOf(in.readUnsignedByte());
            Constant constContrib = pool.getConstant(readIndex(in));
            assert constContrib != null;

            switch (m_composition)
                {
                case Extends:
                case Implements:
                case Into:
                case RebasesOnto:
                    m_typeContrib = (TypeConstant) constContrib;
                    break;

                case Annotation:
                    m_typeContrib = (TypeConstant) constContrib;
                    m_annotation  = (Annotation) pool.getConstant(readIndex(in));
                    break;

                case Delegates:
                    m_typeContrib = (TypeConstant) constContrib;
                    m_constProp   = (PropertyConstant) pool.getConstant(readIndex(in));
                    break;

                case Incorporates:
                    m_typeContrib = (TypeConstant) constContrib;
                    int cParams = readMagnitude(in);
                    if (cParams > 0)
                        {
                        ListMap<StringConstant, TypeConstant> map = new ListMap<>();
                        for (int i = 0; i < cParams; ++i)
                            {
                            int iName = readMagnitude(in);
                            int iType = readMagnitude(in);
                            map.put((StringConstant) pool.getConstant(iName),
                                    iType == 0 ? null : (TypeConstant) pool.getConstant(iType));
                            }
                        m_mapParams = map;
                        }
                    break;

                case ImportOptional:
                case ImportDesired:
                case ImportRequired:
                case ImportEmbedded:
                    m_constModule = (ModuleConstant) constContrib;
                    break;

                default:
                    throw new UnsupportedOperationException("composition=" + m_composition);

                }
            }

        /**
         * Construct an Annotation, Extends, Implements, Into, Incorporates, or Enumerates
         * Contribution.
         *
         * @param composition  specifies the type of composition; one of Annotation, Extends,
         *                     Implements, Into, Incorporates, or Enumerates
         * @param constType    specifies the class type being contributed
         */
        public Contribution(Composition composition, TypeConstant constType)
            {
            assert composition != null;

            switch (composition)
                {
                case Annotation:
                case Extends:
                case Implements:
                case Into:
                case Incorporates:
                case RebasesOnto:
                    if (constType == null)
                        {
                        throw new IllegalArgumentException("type is required");
                        }

                case Equal:
                    break;

                case Delegates:
                    throw new IllegalArgumentException("delegates uses the constructor with a PropertyConstant");

                case ImportOptional:
                case ImportDesired:
                case ImportRequired:
                case ImportEmbedded:
                    throw new IllegalArgumentException("imports uses the constructor with a ModuleConstant");

                default:
                    throw new UnsupportedOperationException("composition=" + composition);
                }

            m_composition = composition;
            m_typeContrib = constType;
            }

        /**
         * Construct an import Contribution.
         *
         * @param composition  specifies the type of composition; one of ImportOptional,
         *                     ImportDesired, ImportRequired, or ImportEmbedded
         * @param constModule  specifies the module being imported
         */
        protected Contribution(Composition composition, ModuleConstant constModule)
            {
            assert composition != null && constModule != null;

            switch (composition)
                {
                case ImportOptional:
                case ImportDesired:
                case ImportRequired:
                case ImportEmbedded:
                    if (constModule == null)
                        {
                        throw new IllegalArgumentException("module is required");
                        }
                    break;

                case Annotation:
                case Extends:
                case Implements:
                case Into:
                case Incorporates:
                case RebasesOnto:
                    throw new IllegalArgumentException(composition + " uses the constructor with a TypeConstant");

                case Delegates:
                    throw new IllegalArgumentException("delegates uses the constructor with a PropertyConstant");

                default:
                    throw new UnsupportedOperationException("composition=" + composition);
                }

            m_composition = composition;
            m_constModule = constModule;
            }

        /**
         * Construct a delegation Contribution.
         *
         * @param constant  specifies the class being contributed
         * @param delegate  for a Delegates composition, this is the property that provides the
         *                  delegate reference
         */
        public Contribution(TypeConstant constant, PropertyConstant delegate)
            {
            assert constant != null && delegate != null;

            m_composition = Composition.Delegates;
            m_typeContrib = constant;
            m_constProp   = delegate;
            }

        /**
         * Construct an annotation Contribution.
         *
         * @param annotation  the annotation
         */
        public Contribution(Annotation annotation)
            {
            this(annotation, annotation.getAnnotationType());
            }

        /**
         * Construct an annotation Contribution.
         *
         * @param annotation  the annotation
         * @param type        the specific type to use for the annotation
         */
        public Contribution(Annotation annotation, TypeConstant type)
            {
            assert annotation != null;
            assert type != null;

            m_composition = Composition.Annotation;
            m_typeContrib = type;
            m_annotation  = annotation;
            }

        /**
         * Construct an "incorporates conditional" Contribution.
         *
         * @param constType       the type of the mixin
         * @param mapConstraints  the type constraints that make the mixin conditional
         */
        public Contribution(TypeConstant constType,
                               ListMap<StringConstant, TypeConstant> mapConstraints)
            {
            this(Composition.Incorporates, constType);

            m_mapParams = mapConstraints;
            }

        /**
         * Obtain the form of composition represented by this contribution.
         *
         * @return the Composition type for this contribution
         */
        public Composition getComposition()
            {
            return m_composition;
            }

        /**
         * Obtain the constant identifying the module being imported by this contribution.
         *
         * @return the ModuleConstant for this contribution, or null if this is not an "imports"
         *         contribution
         */
        public ModuleConstant getModuleConstant()
            {
            return m_constModule;
            }

        /**
         * @return false iff all the contribution's elements are resolved
         */
        public boolean containsUnresolved()
            {
            if (getTypeConstant().containsUnresolved())
                {
                return true;
                }

            PropertyConstant constProp = m_constProp;
            if (constProp != null)
                {
                if (constProp.containsUnresolved())
                    {
                    return true;
                    }
                }

            Annotation anno = m_annotation;
            if (anno != null)
                {
                // annotations parameters don't need to be resolved for the compilation to move
                // forward; they will be checked later as a part of the annotation type resolution
                if (anno.getAnnotationClass().containsUnresolved())
                    {
                    return true;
                    }
                }

            Map<StringConstant, TypeConstant> mapParams = m_mapParams;
            if (mapParams != null)
                {
                for (TypeConstant type : mapParams.values())
                    {
                    if (type != null && type.containsUnresolved())
                        {
                        return true;
                        }
                    }
                }

            return false;
            }

        /**
         * Obtain the constant identifying the class type being contributed by this contribution.
         *
         * @return the TypeConstant for this contribution
         */
        public TypeConstant getTypeConstant()
            {
            return m_typeContrib;
            }

        /**
         * Update the type being contributed by this contribution to a narrower one.
         *
         * @param  type  the updated TypeConstant for this contribution
         */
        public void narrowType(TypeConstant type)
            {
            assert m_typeContrib != null && type.isA(m_typeContrib);
            m_typeContrib = type;
            }

        /**
         * @return the PropertyConstant specifying the reference to delegate to; never null
         */
        public PropertyConstant getDelegatePropertyConstant()
            {
            return m_constProp;
            }

        /**
         * @return the annotation (if this is an annotation contribution)
         */
        public Annotation getAnnotation()
            {
            return m_annotation;
            }

        /**
         * Obtain the type constraints for the conditional mixin.
         *
         * @return a read-only map of type parameter name to type constraint, or null if the
         *         Composition is not "incorporates conditional"
         */
        public Map<StringConstant, TypeConstant> getTypeParams()
            {
            Map<StringConstant, TypeConstant> map = m_mapParams;
            if (map == null)
                {
                return null;
                }
            assert (map = Collections.unmodifiableMap(map)) != null;
            return map;
            }

        /**
         * Resolve this contribution type based on the specified resolver.
         *
         * @param pool      the ConstantPool to place a potentially created new constant into
         * @param resolver  the resolver
         *
         * @return the transformed type or null if the conditional incorporation
         *         does not apply for the resulting type
         */
        public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
            {
            TypeConstant typeContrib = getTypeConstant();
            boolean      fNormalize  = true;

            if (typeContrib.isExplicitClassIdentity(true) && !typeContrib.isParamsSpecified())
                {
                IdentityConstant id  = typeContrib.getSingleUnderlyingClass(true);
                ClassStructure   clz = (ClassStructure) id.getComponent();
                if (clz.isParameterized())
                    {
                    // check if generic type parameters were implicitly added
                    // (see TypeCompositionStatement.addImplicitTypeParameters)
                    // and only then resolve them accordingly
                    boolean fSynthetic = false;
                    for (StringConstant constName : clz.getTypeParams().keySet())
                        {
                        if (clz.getChild(constName.getValue()).isSynthetic())
                            {
                            fSynthetic = true;
                            break;
                            }
                        }

                    if (fSynthetic)
                        {
                        TypeConstant typeContribNew = clz.getFormalType().resolveGenerics(pool, resolver);

                        if (typeContrib.isAccessSpecified())
                            {
                            typeContribNew = pool.ensureAccessTypeConstant(typeContribNew, typeContrib.getAccess());
                            }
                        if (typeContrib.isImmutabilitySpecified())
                            {
                            typeContribNew = pool.ensureImmutableTypeConstant(typeContribNew);
                            }
                        typeContrib = typeContribNew;
                        fNormalize  = false;
                        }
                    }
                }

            if (fNormalize)
                {
                typeContrib = typeContrib.normalizeParameters().resolveGenerics(pool, resolver);
                }

            return getComposition() != Composition.Incorporates ||
                    checkConditionalIncorporate(typeContrib) ?
                typeContrib : null;
            }

        /**
         * Resolve the type of this contribution based on the specified list of actual types.
         *
         * @param pool        the ConstantPool to place a potentially created new constant into
         * @param clzParent   the parent class structure
         * @param listActual  the actual type list
         *
         * @return the resolved contribution type or null if the conditional incorporation
         *         does not apply for the specified types
         */
        protected TypeConstant resolveType(ConstantPool pool, ClassStructure clzParent,
                                           List<TypeConstant> listActual)
            {
            TypeConstant typeContrib = getTypeConstant();

            assert typeContrib.isSingleDefiningConstant();

            typeContrib = typeContrib.normalizeParameters();
            if (!typeContrib.isParameterizedDeep())
                {
                return typeContrib;
                }

            GenericTypeResolver resolver = clzParent.new SimpleTypeResolver(pool, listActual);

            typeContrib = typeContrib.resolveGenerics(pool, resolver);

            return getComposition() != Composition.Incorporates ||
                        checkConditionalIncorporate(typeContrib)
                    ? typeContrib
                    : null;
            }

        /**
         * Resolve the type of this contribution based on the specified actual type.
         *
         * @param pool        the ConstantPool to place a potentially created new constant into
         * @param typeActual  the actual type
         *
         * @return the resolved contribution type or null if the conditional incorporation
         *         does not apply for the specified type
         */
        protected TypeConstant resolveType(ConstantPool pool, ClassStructure clzParent,
                                           TypeConstant typeActual)
            {
            TypeConstant typeContrib = getTypeConstant();

            assert typeContrib.isSingleDefiningConstant();

            typeContrib = typeContrib.normalizeParameters();
            if (!typeContrib.isParameterizedDeep())
                {
                return typeContrib;
                }

            // Note: this method is called by ClassStructure.getGenericParamTypeImpl(),
            //       that iterates over all the contributions; as a result, to avoid the infinite
            //       recursion, the resolver must check only the specified class's formal types
            GenericTypeResolver resolver = typeActual.isVirtualChild()
                ? typeActual
                : clzParent.new SimpleTypeResolver(pool, typeActual.getParamTypes());

            typeContrib = typeContrib.resolveGenerics(pool, resolver);

            return getComposition() != Composition.Incorporates ||
                        checkConditionalIncorporate(typeContrib)
                    ? typeContrib
                    : null;
            }

        /**
         * Check if this "incorporate" contribution is conditional and if so,
         * whether or not it applies to this type
         *
         * @param typeContrib  the actual (resolved) contribution type
         *
         * @return true iff the contribution is unconditional or applies to this type
         */
        protected boolean checkConditionalIncorporate(TypeConstant typeContrib)
            {
            assert getComposition() == Composition.Incorporates;

            TypeConstant[] atypeParams = typeContrib.getParamTypesArray();

            Map<StringConstant, TypeConstant> mapConditional = getTypeParams();
            if (mapConditional != null && !mapConditional.isEmpty())
                {
                // conditional incorporation; check if the actual parameters apply
                assert atypeParams.length == mapConditional.size();

                Iterator<TypeConstant> iterConstraint = mapConditional.values().iterator();
                for (TypeConstant typeParam : atypeParams)
                    {
                    TypeConstant typeConstraint = iterConstraint.next();

                    if (typeConstraint != null && !typeParam.isA(typeConstraint))
                        {
                        // this contribution doesn't apply
                        return false;
                        }
                    }
                }
            return true;
            }

        /**
         * @see XvmStructure#registerConstants(ConstantPool)
         */
        protected void registerConstants(ConstantPool pool)
            {
            m_constModule  = (ModuleConstant)   pool.register(m_constModule);
            m_typeContrib  = (TypeConstant)     pool.register(m_typeContrib);
            m_constProp    = (PropertyConstant) pool.register(m_constProp);
            m_annotation   = (Annotation)       pool.register(m_annotation);

            ListMap<StringConstant, TypeConstant> mapOld = m_mapParams;
            if (mapOld != null)
                {
                ListMap<StringConstant, TypeConstant> mapNew = new ListMap<>();
                for (Map.Entry<StringConstant, TypeConstant> entry : mapOld.asList())
                    {
                    StringConstant constName = entry.getKey();
                    TypeConstant   type      = entry.getValue();

                    mapNew.put((StringConstant) pool.register(constName),
                               (TypeConstant)   (type == null ? null : pool.register(type)));
                    }
                m_mapParams = mapNew;
                }
            }

        /**
         * @see XvmStructure#registerConstants(ConstantPool)
         */
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(m_composition.ordinal());
            writePackedLong(out, (m_typeContrib == null ? m_constModule : m_typeContrib).getPosition());

            switch (m_composition)
                {
                case Annotation:
                    writePackedLong(out, Constant.indexOf(m_annotation));
                    break;

                case Delegates:
                    writePackedLong(out, Constant.indexOf(m_constProp));
                    break;

                case Incorporates:
                    ListMap<StringConstant, TypeConstant> map = m_mapParams;
                    if (map == null)
                        {
                        writePackedLong(out, 0);
                        }
                    else
                        {
                        writePackedLong(out, map.size());
                        for (Map.Entry<StringConstant, TypeConstant> entry : map.entrySet())
                            {
                            StringConstant constName = entry.getKey();
                            TypeConstant   type      = entry.getValue();

                            writePackedLong(out, constName.getPosition());
                            writePackedLong(out, type == null ? 0 : type.getPosition());
                            }
                        }
                    break;
                }
            }

        @Override
        protected Object clone()
            {
            try
                {
                return super.clone();
                }
            catch (CloneNotSupportedException e)
                {
                throw new IllegalStateException(e);
                }
            }

        @Override
        public boolean equals(Object obj)
            {
            if (this == obj)
                {
                return true;
                }

            if (!(obj instanceof Contribution that))
                {
                return false;
                }

            if (this.m_composition == that.m_composition
                    && Handy.equals(this.m_constModule, that.m_constModule)
                    && Handy.equals(this.m_typeContrib, that.m_typeContrib)
                    && Handy.equals(this.m_constProp, that.m_constProp)
                    && Handy.equals(this.m_annotation, that.m_annotation))
                {
                ListMap<StringConstant, TypeConstant> mapThis = this.m_mapParams;
                ListMap<StringConstant, TypeConstant> mapThat = that.m_mapParams;
                if (mapThis == null && mapThat == null)
                    {
                    return true;
                    }
                if (mapThis != null && mapThat != null)
                    {
                    return Handy.equals(mapThis.keySet().toArray(), mapThat.keySet().toArray())
                        && Handy.equals(mapThis.values().toArray(), mapThat.values().toArray());
                    }
                }
            return false;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            switch (m_composition)
                {
                case Annotation:
                    sb.append('@');
                    break;

                case Equal:
                    sb.append(m_composition)
                      .append(' ');
                    break;

                default:
                    sb.append(m_composition.toString().toLowerCase())
                      .append(' ');
                    break;
                }

            if (m_composition == Composition.Incorporates && m_mapParams != null)
                {
                TypeConstant constMixin = m_typeContrib;
                sb.append("conditional ")
                  .append(constMixin.getDefiningConstant())
                  .append('<');

                boolean fFirst = true;
                for (TypeConstant constParam : constMixin.getParamTypesArray())
                    {
                    if (fFirst)
                        {
                        fFirst = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }

                    sb.append(constParam.getValueString());

                    // extract the type name from the type param
                    if (constParam.isSingleDefiningConstant()
                            && constParam.getDefiningConstant() instanceof PropertyConstant)
                        {
                        StringConstant constName = ((PropertyConstant)
                            constParam.getDefiningConstant()).getNameConstant();
                        TypeConstant   constConstraint = m_mapParams.get(constName);
                        if (constConstraint != null)
                            {
                            sb.append(" extends ")
                                    .append(constConstraint.getValueString());
                            }
                        }
                    }

                sb.append('>');
                }
            else
                {
                if (m_constModule != null)
                    {
                    sb.append(m_constModule.getDescription());
                    }
                else if (m_typeContrib != null)
                    {
                    sb.append(m_typeContrib.getDescription());
                    }

                if (m_composition == Composition.Annotation && m_annotation != null)
                    {
                    Constant[] aconstArgs = m_annotation.getParams();
                    if (aconstArgs.length > 0)
                        {
                        sb.append('(');

                        boolean fFirst = true;
                        for (Constant constParam : aconstArgs)
                            {
                            if (fFirst)
                                {
                                fFirst = false;
                                }
                            else
                                {
                                sb.append(", ");
                                }

                            sb.append(constParam.getValueString());
                            }

                        sb.append(')');
                        }
                    }
                else if (m_composition == Composition.Delegates)
                    {
                    sb.append('(')
                      .append(m_constProp.getDescription())
                      .append(')');
                    }
                }

            return sb.toString();
            }

        /**
         * Defines the form of composition that this component contributes to the class.
         */
        private final Composition m_composition;

        /**
         * Defines the module (ModuleConstant) that was used as part of the Contribution.
         */
        private ModuleConstant m_constModule;

        /**
         * Defines the class (TypeConstant) that was used as part of the Contribution.
         */
        private TypeConstant m_typeContrib;

        /**
         * The property specifying the delegate, if this Contribution represents a "delegates"
         * clause.
         */
        private PropertyConstant m_constProp;

        /**
         * The annotation data, if this Contribution represents an annotation.
         */
        private Annotation m_annotation;

        /**
         * The name-to-type information for "incorporates conditional" constraints.
         */
        private ListMap<StringConstant, TypeConstant> m_mapParams;
        }


    // ----- interface: ResolutionCollector --------------------------------------------------------

    public enum ResolutionResult
        {
        UNKNOWN, RESOLVED, POSSIBLE, ERROR;

        /**
         * Combine this result with the specified one to produce better information.
         *
         * @param that  another result
         *
         * @return a combined result
         */
        public ResolutionResult combine(ResolutionResult that)
            {
            return switch (this)
                {
                case POSSIBLE, ERROR -> this;
                default              -> that;
                };
            }
        }

    /**
     * A callback interface used by the name resolution functionality of the Component.
     */
    public interface ResolutionCollector
        {
        /**
         * Invoked when a name resolves to a child component.
         *
         * @param component  the child component (which may be a composite)
         */
        ResolutionResult resolvedComponent(Component component);

        /**
         * Invoked when a name resolves to something that is a constant, such as a property
         * constant of a parameterized type or of a method.
         *
         * @param constant  either a PropertyConstant or a TypeParameterConstant
         */
        ResolutionResult resolvedConstant(Constant constant);

        /**
         * Provide an AstNode to report resolution issues for.
         */
        default AstNode getNode()
            {
            return null;
            }

        /**
         * Provide an ErrorListener to report resolution issues to.
         */
        default ErrorListener getErrorListener()
            {
            return ErrorListener.BLACKHOLE;
            }
        }

    /**
     * A simple ResolutionCollector implementation.
     */
    public static class SimpleCollector
            implements ResolutionCollector
        {
        public SimpleCollector(ErrorListener errs)
            {
            m_errs = errs;
            }

        @Override
        public ResolutionResult resolvedComponent(Component component)
            {
            m_constant = component.getIdentityConstant();
            return ResolutionResult.RESOLVED;
            }

        @Override
        public ResolutionResult resolvedConstant(Constant constant)
            {
            m_constant = constant;
            return ResolutionResult.RESOLVED;
            }

        @Override
        public ErrorListener getErrorListener()
            {
            return m_errs;
            }

        /**
         * @return the resolved constant
         */
        public Constant getResolvedConstant()
            {
            return m_constant;
            }


        // ----- data fields -----------------------------------------------------------------------

        /**
         * The resolved constant.
         */
        private Constant m_constant;

        /**
         * The error listener.
         */
        private final ErrorListener m_errs;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * If the leading byte of the flags contains a conditional bit, then it isn't actually the
     * leading byte of the flags, and instead is an indicator that the conditional format is being
     * used, possibly with more than one component of the same name. Specifically, if that leading
     * byte has the CONDITIONAL_BIT set, then that byte is followed by a packed integer specifying
     * the number of components of the same name, and for each component there is a packed integer
     * for the conditional constant ID followed by the body of the component. (The children that go
     * with the various conditional components occur in the stream after the <b>last</b> body.)
     */
    public static final int CONDITIONAL_BIT  =   0x80;

    public static final int FORMAT_MASK      = 0x000F, FORMAT_SHIFT     = 0;
    public static final int ACCESS_MASK      = 0x0300, ACCESS_SHIFT     = 8;
    public static final int ACCESS_PUBLIC    = 0x0100;
    public static final int ACCESS_PROTECTED = 0x0200;
    public static final int ACCESS_PRIVATE   = 0x0300;
    public static final int ABSTRACT_BIT     = 0x0400, ABSTRACT_SHIFT   = 10;
    public static final int STATIC_BIT       = 0x0800, STATIC_SHIFT     = 11;
    public static final int SYNTHETIC_BIT    = 0x1000, SYNTHETIC_SHIFT  = 12;
    public static final int COND_RET_BIT     = 0x2000, COND_RET_SHIFT   = 13;
    public static final int AUXILIARY_BIT    = 0x4000, AUXILIARY_SHIFT  = 14;


    // ----- fields --------------------------------------------------------------------------------

    /**
     * This is the next youngest sibling that shares a conceptual parent and a name. Components have
     * siblings only when conditions kick in; consider a module that contains a class named "util"
     * in version 1 that is replaced with a package in version 2 and version 3. Some arbitrary first
     * sibling would have the identity of Class:(ModuleConstant, "util") and a format of CLASS, with
     * a sibling with the identity of Package:(ModuleConstant, "util") and a format of PACKAGE (and
     * possibly one further sibling if there were changes to the package structure between version
     * 2 and 3.)
     */
    private Component m_sibling;

    /**
     * This is the identity constant for the Component. Because the identity constant is of a
     * certain type (e.g. package, class, ...), it may not be shared by all of the siblings with
     * the same name, if they are of different formats.
     */
    private IdentityConstant m_constId;

    /**
     * The condition for this component that specifies under which conditions this component will
     * exist.
     */
    private ConditionalConstant m_cond;

    /**
     * This numeric value encodes all sorts of information, including access, abstract, static,
     * synthetic, and the component format.
     */
    private short m_nFlags;

    /**
     * The contributions that make up this class.
     */
    private List<Contribution> m_listContribs;

    /**
     * The documentation.
     * TODO convert this to a doc annotation/contribution?
     */
    private String m_sDoc;

    /**
     * This is a non-deserialized form of all of the children. When a Component is read from disk,
     * it can optionally lazily deserialize its children. This is possible because the "children"
     * block is length-encoded.
     */
    private byte[] m_abChildren;

    /**
     * This holds all of the children of all of the siblings, except for methods (because they are
     * identified by signature, not by name). Because a single child may turn out to be a child of
     * more than one sibling (based on which condition applies), the child can only determine its
     * real parent by asking the assumed parent's assumed parent for the child by the name of the
     * assumed parent. Similarly, the child obtained by name from this map is just the first of the
     * siblings by that name, only one of which (at most) is the child that is existent for a
     * specified name.
     */
    private Map<String, Component> m_childByName;

    /**
     * For XVM structures that can be modified, this flag tracks whether or not
     * a modification has occurred.
     */
    private boolean m_fModified;

    /**
     * Recursion check for {@link #resolveContributedName}. Not thread-safe.
     */
    private Boolean m_FVisited;
    }
