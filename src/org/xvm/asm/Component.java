
package org.xvm.asm;


import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.NamedConstant;

import java.io.DataInput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;

/**
 * The Component data structure is the base class for the module, package, class, property, method,
 * and other data structures that make up the structural components of the module format. The
 * Component encapsulates a number of complex aspects of the XVM module format, most notably the
 * conditional structure of XVM modules.
 * <p/>
 * Normally, an XVM structure has a single parent structure and any number of child structures, but
 * a Component can differ dramatically from this model, in that it can have any number of parent
 * Conponents (only one of which at most is valid for a given condition), and it can have any number
 * of child Components, of which only some are (perhaps none is) appropriate for a given condition.
 * <p/>
 * The Component also tracks modifications.
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
 * <li><i>(Multi-)Method</i> - the {@link MethodContainer MethodContainer}</li>
 * <li><i>(Multi-)Method + Property + Class</i> - the {@link ClassContainer ClassContainer}</li>
 * <li><i>(Multi-)Method + Property + Class + Package</i> - the {@link PackageContainer
 *     PackageContainer}</li>
 * </ul>
 *
 * @author cp 2017.05.02
 */
public abstract class Component
        extends XvmStructure
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
                        boolean fSynthetic, Format format, Constant constId, ConditionalConstant condition)
        {
        this(xsParent, (format.ordinal() << FORMAT_SHIFT) | (access.ordinal() << ACCESS_SHIFT)
                | (fAbstract ? ABSTRACT_BIT : 0) | (fStatic ? STATIC_BIT : 0) | (fSynthetic ? SYNTHETIC_BIT : 0),
                constId, condition);
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
    protected Component(XvmStructure xsParent, int nFlags, Constant constId, ConditionalConstant condition)
        {
        super(xsParent);
        assert (xsParent == null) == (this instanceof FileStructure);   // file doesn't have parent
        assert (constId == null) == (this instanceof FileStructure);    // file doesn't have constid
        assert condition == null || !(this instanceof FileStructure);   // file can't be conditional

        m_nFlags  = (short) nFlags;
        m_constId = constId;
        m_cond    = condition;
        }


    // ----- I/O -----------------------------------------------------------------------------------

    /**
     * Read a component from the stream. This abstracts out all of the various component types that
     * could be encountered in the stream, and all of the complexity surrounding conditions.
     *
     * @param xsParent
     * @param in
     *
     * @return
     *
     * @throws IOException  if there's a problem reading the component
     */
    public static Component readComponent(XvmStructure xsParent, DataInput in)
            throws IOException
        {
        Component component = null;

        // read component body (or bodies)
        int n = in.readUnsignedByte();
        if ((n & CONDITIONAL_BIT) == 0)
            {
            // there isn't a conditional multiple-component list, so this is just the first byte of
            // the two-byte FLAGS value (which is the start of the body) for a single component
            n = (n << 8) | in.readUnsignedByte();
            component = Format.fromFlags(n).instantiate(xsParent, null, n, in);
            }
        else
            {
            // some number of components, each with a condition
            ConstantPool pool        = xsParent.getConstantPool();
            Component    prevSibling = null;
            int          cSiblings   = readMagnitude(in);
            assert cSiblings > 0;
            for (int i = 0; i < cSiblings; ++i)
                {
                ConditionalConstant condition  = (ConditionalConstant) pool.getConstant(readIndex(in));
                int                 nFlags     = in.readUnsignedShort();
                Component           curSibling = Format.fromFlags(nFlags).instantiate(xsParent, condition, nFlags, in);
                if (prevSibling == null)
                    {
                    component = curSibling;
                    }
                else
                    {
                    prevSibling.m_sibling = curSibling;
                    }
                prevSibling = curSibling;
                }
            }

        component.readComponentChildren(in);
        return component;
        }

    /**
     * Read any child components. For a given identity, there may be more than one child component
     * if the child components are conditional. For all components (except for methods i.e. within
     * multi-methods), the children are identified by name.
     *
     * @param in  the DataInput to read from.
     *
     * @throws IOException  if there's a problem reading the component's children
     */
    protected void readComponentChildren(DataInput in)
            throws IOException
        {
        // read component children
        int cKids = readMagnitude(in);
        if (cKids > 0)
            {
            for (int i = 0; i < cKids; ++i)
                {
                Component kid = readComponent(this, in);
                adopt(kid);
                }
            }
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
        Component    parent     = null;
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
        return parent.getParent().getChild(parent.getName());
        }

    /**
     * Each Component is identified by a constant. The one exception is the file structure, which
     * contains components, but is not technically itself a component in the XVM sense.
     *
     * @return  the constant that identifies the component, or null for a File component
     */
    public Constant getIdentityConstant()
        {
        return m_constId;
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
    protected void setAccess(Access access)
        {
        m_nFlags = (short) ((m_nFlags & ~ACCESS_MASK) | (access.ordinal() << ACCESS_SHIFT));
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
    protected void setAbstract(boolean fAbstract)
        {
        m_nFlags = (short) ((m_nFlags & ~ABSTRACT_BIT) | (fAbstract ? ABSTRACT_BIT : 0));
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
    protected void setStatic(boolean fStatic)
        {
        m_nFlags = (short) ((m_nFlags & ~STATIC_BIT) | (fStatic ? STATIC_BIT : 0));
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
    protected void setSynthetic(boolean fSynthetic)
        {
        m_nFlags = (short) ((m_nFlags & ~SYNTHETIC_BIT) | (fSynthetic ? SYNTHETIC_BIT : 0));
        }

    /**
     * Obtain the name of the component. All components have a name, although the purpose of the
     * name varies slightly for several components:
     * <ul>
     * <li>The Package, various Class types (Interface, Class, Const, Enum, Mixin, Trait, and
     *     Service), Property, and MultiMethod all are identified within their parent component by
     *     their name, using a NamedConstant;</li>
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
        // this will need to be overridden by File (file name), Module (qualified module name), and
        // Method (which has a signature for its identity)
        return ((NamedConstant) getIdentityConstant()).getName();
        }

    /**
     * @return assuming that this is one of any number of siblings, obtain a reference to the first
     *         sibling (which may be this); never null
     */
    protected Component getEldestSibling()
        {
        Component parent = getParent();
        if (parent == null)
            {
            return this;
            }

        // this component is either known by its name or by its identity constant (methods only)
        Component sibling;
        if (this instanceof MethodStructure)
            {
            sibling = parent.getMethodByConstantMap().get(getIdentityConstant());
            }
        else
            {
            sibling = parent.getChildByNameMap().get(getName());
            }

        assert sibling != null;
        return sibling;
        }

    /**
     * Obtain a read-only map of all children identified by name.
     * <p/>
     * Note: the returned map does not contain any of the child methods.
     *
     * @return a read-only map from name to child component; never null, even if there are no
     *         children
     */
    protected Map<String, Component> getChildByNameMap()
        {
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
    protected Map<String, Component> ensureChildByNameMap()
        {
        Map<String, Component> map = m_childByName;
        if (map == null)
            {
            map = new HashMap<>(7);

            Component sibling = getEldestSibling();
            while (sibling != null)
                {
                sibling.m_childByName = map;
                sibling = sibling.m_sibling;
                }

            // the corresponding field on this component should now be initialized
            assert m_childByName == map;
            }
        return map;
        }

    /**
     * Obtain a read-only map of all method children identified by method signature constant.
     * <p/>
     * Note: the returned map contains only methods
     *
     * @return a read-only map from method constant to method component; never null, even if there
     *         are no child methods
     */
    protected Map<MethodConstant, MethodStructure> getMethodByConstantMap()
        {
        Map<MethodConstant, MethodStructure> map = m_methodByConstant;
        return map == null ? Collections.EMPTY_MAP : map;
        }

    /**
     * Obtain the actual read/write map of all method children identified by method signature
     * constant.
     * <p/>
     * Note: the returned map contains only methods
     *
     * @return obtain the actual map from method constant to method component, creating the map if
     *         necessary
     */
    protected Map<MethodConstant, MethodStructure> ensureMethodByConstantMap()
        {
        Map<MethodConstant, MethodStructure> map = m_methodByConstant;
        if (map == null)
            {
            map = new HashMap<>(7);

            Component sibling = getEldestSibling();
            while (sibling != null)
                {
                sibling.m_methodByConstant = map;
                sibling = sibling.m_sibling;
                }

            assert m_methodByConstant == map;
            }
        return map;
        }

    /**
     * Adopt the specified component as a child of this component.
     * <p/>
     * Imagine the simplest case, of two identical hierarchies, differing only by version:
     * <p/>
     * <code><pre>
     *   Original            Adoptee        Result
     *   --------            -------        ------
     *   F1                                 F1
     *     |- M1 (V1)        M1 (V2)          |- M1 (V1 | V2)
     *        |- P1            |- P1             |- P1
     *          |- C1            |- C1             |- C1
     * </pre></code>
     * <p/>
     * Now if P1 is modified:
     * <p/>
     * <code><pre>
     *   Original            Adoptee        Result
     *   --------            -------        ------
     *   F1                                 F1
     *     |- M1 (V1)        M1 (V2)          |- M1 (V1 | V2)
     *        |- P1            |- P1'            |- P1 (V1), P1' (V2)
     *          |- C1            |- C1             |- C1
     * </pre></code>
     * <p/>
     * Now if C1 is modified:
     * <p/>
     * <code><pre>
     *   Original            Adoptee        Result
     *   --------            -------        ------
     *   F1                                 F1
     *     |- M1 (V1)        M1 (V2)          |- M1 (V1 | V2)
     *        |- P1            |- P1             |- P1
     *          |- C1            |- C1'            |- C1 (V1), C1' (V2)
     * </pre></code>
     * <p/>
     * Now a more complex example:
     * <p/>
     * <code><pre>
     *   Original                   Adoptee         Result
     *   --------                   -------         ------
     *   F1                                         F1
     *     |- M1 (V1), M1' (V2)     M1'' (V3)         |- M1 (V1), M1' (V2), M1'' (V3)
     *        |- P1 (V1), P1' (V2)    |- P1'            |- P1 (V1), P1' (V2 | V3)
     *          |- C1                   |- C1'             |- C1 (V1 | V2), C1' (V3)
     *                                  |- C2              |- C2 (V3)
     *                                    |- C3              |- C3
     * </pre></code>
     * <p/>
     *
     * @param kid  the component to adopt
     */
    protected void adopt(Component kid)
        {
        adopt(kid, null, null);
        }

    /**
     * TODO
     *
     * @param kid      the child component to adopt
     * @param condOld  the condition that implicitly applies to existing children of this component
     * @param condKid  the condition that implicitly applies to the {@code kid}
     */
    protected void adopt(Component kid, ConditionalConstant condOld, ConditionalConstant condKid)
        {
        Map<Object, Component> kids;
        Object id;
        if (kid instanceof MethodStructure)
            {
            kids = (Map<Object, Component>) (Map) ensureMethodByConstantMap();
            id   = kid.getIdentityConstant();
            }
        else
            {
            kids = (Map<Object, Component>) (Map) ensureChildByNameMap();
            id   = kid.getName();
            }

        Component sibling = kids.get(id);
        if (sibling == null)
            {
            // the only extra thing that needs to be done is to decorate the kid with the implicit
            // condition
            kid.addAndCondition(condKid);
            kids.put(id, kid);
            // TODO set parent
            }
        else
            {
            // can't have two kids with the same identity unless they have conditions declared
            if ((condOld == null && sibling.m_cond == null) || (condKid == null && kid.m_cond == null))
                {
                throw new IllegalStateException("cannot adopt unless existing child and adoptee are both conditional");
                }

            // since there's a collision/overlap in the sibling namespace, we need to take the kid's
            // condition and replicate it onto each of its kids, so that when we merge the trees,
            // the grandkids don't accidentally get fathered by their siblings (insert joke here)
            // TODO

            // it's possible that the kid to adopt is identical to another kid (and by "identical",
            // we're not talking identical twins -- we're talking about being "one and the same
            // kid") ... if that's true, the only thing that needs to change is the conditional on
            // the kid to reflect both the existing kid's conditional and the one we're adopting;
            // however, if that is the case, then we'll have to move the kid's kids over too, and to
            // do that, we'll have to first change their conditions to "and" with the condition of
            // their parent, aka "the kid"
            Component eachSibling = sibling;
            while (true)
                {
                if (eachSibling.isBodyIdentical(kid))
                    {
                    eachSibling.addOrCondition(condKid);
                    break;
                    }

                Component nextSibling = eachSibling.m_sibling;
                if (nextSibling == null)
                    {
                    // add the kid to the end of the sibling chain
                    eachSibling.m_sibling = kid;
                    break;
                    }

                eachSibling = nextSibling;
                }

            // TODO kids
            }
        }

    protected void addAndCondition(ConditionalConstant cond)
        {
        if (cond != null)
            {
            ConditionalConstant condOld = m_cond;
            m_cond = condOld == null ? cond : condOld.addAnd(cond);
            }
        }

    protected void addOrCondition(ConditionalConstant cond)
        {
        if (cond != null)
            {
            ConditionalConstant condOld = m_cond;
            m_cond = condOld == null ? cond : condOld.addOr(cond);
            }
        }


    abstract boolean isBodyIdentical(Component kid);

    // TODO evaluate removal of this
//    /**
//     * This method obtains a list of all of the child Components of this Component. If there are any
//     * conditions to evaluate, the current assembler context is used to evaluate those conditions.
//     * @return
//     */
//    public List<Component> getChildren()
//        {
//        Map<String, Component> kids = m_childByName;
//        if (kids.isEmpty())
//            {
//            return Collections.EMPTY_LIST;
//            }
//
//        List<Component>  list = new ArrayList<>(kids.size());
//        AssemblerContext ctx  = getFileStructure().getContext();
//        for (Component kid : kids.values())
//            {
//            // if the child is unconditional, then put it into the list
//            if (kid.getCondition() == null)
//                {
//                assert kid.m_sibling == null;
//                list.add(kid);
//                continue;
//                }
//
//            // if the child is conditional, then it (or one of its siblings) only goes in the list
//            // if the kid's condition matches the current context
//            do
//                {
//                if (kid.getCondition().evaluate(ctx))
//
//                kid = kid.m_sibling;
//                }
//            while (kid != null);
//            }
//
//        }

    /**
     * TODO
     *
     * @param constId  the constant identifying the child
     *
     * @return the child component
     */
    public Component getChild(Constant constId)
        {
        if (constId instanceof MethodConstant)
            {
            // TODO
            throw new UnsupportedOperationException("look up method");
            }
        else
            {
            // TODO Component child = getFormat().getChild(constId)
            throw new UnsupportedOperationException("look up child by name");
            }
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
        Component child = m_childByName.get(sName);
        if (child == null)
            {
            return null;
            }

        if (child.m_sibling == null && child.m_cond == null)
            {
            return child;
            }

        // TODO - handle conditions
        throw new UnsupportedOperationException("TODO conditions");
        }

    protected void registerChild(Component child)
        {
        // TODO need a way to merge if there's already a child by the same name:
        // 1) validate that there are no collisions, i.e. conditions must not overlap
        // 2) the child maps will need to be merged, and children will need to
        }


    // ----- inner class: Format -------------------------------------------------------------------

    public enum Format
        {
        INTERFACE,
        CLASS,
        CONST,
        ENUM,
        MIXIN,
        TRAIT,
        SERVICE,
        RSVD_7,
        PACKAGE,
        MODULE,
        PROPERTY,
        METHOD,
        RSVD_C,
        RSVD_D,
        MULTIMETHOD,
        FILE;

        static Format fromFlags(int nFlags)
            {
            return valueOf((nFlags & FORMAT_MASK) >>> FORMAT_SHIFT);
            }

        /**
         * Instantiate a component as it is being read from a stream, reading its body (but NOT its
         * children).
         *
         * @param xsParent   the parent component
         * @param condition  the condition under which the component is present, or null
         * @param nFlags     the flags that define the common attributes of the component
         * @param in         the stream from which the component is being read from
         *
         * @return the component
         *
         * @throws IOException if something goes wrong reading from the stream
         */
        Component instantiate(XvmStructure xsParent, ConditionalConstant condition, int nFlags, DataInput in)
                throws IOException
            {
            Component component;
            // TODO sub-classes
//            return new Component(xsParent,
//                    (nFlags & ACCESS_MASK) >>> ACCESS_SHIFT,
//                    (nFlags & ABSTRACT_BIT) != 0,
//                    (nFlags & STATIC_BIT) != 0,
//                    (nFlags & SYNTHETIC_BIT) != 0,
//                    this,
//                    xsParent.getConstantPool().getConstant(readIndex(in)),
//                    condition);
// or ...
//          protected Component(XvmStructure xsParent, int nFlags, Constant constId, ConditionalConstant condition)

            switch (this)
                {
                case FILE:
                    throw new IOException("file is not instantiable");

                case MODULE:
                    component = null;
                    // TODO ModuleStructure
                    break;

                case PACKAGE:
                    component = null;
                    // TODO PackageStructure
                    break;

                case INTERFACE:
                case CLASS:
                case CONST:
                case ENUM:
                case MIXIN:
                case TRAIT:
                case SERVICE:
                    component = null;
                    // TODO ClassStructure
                    break;

                case PROPERTY:
                    component = null;
                    // TODO PropertyStructure
                    break;

                case MULTIMETHOD:
                    component = null;
                    // TODO MultiMethodStructure
                    break;

                case METHOD:
                    component = null;
                    // TODO MethodStructure
                    break;

                default:
                    throw new IOException("uninstantiable format: " + this);
                }

            component.disassemble(in);
            return component;
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
    public static final int CONDITIONAL_BIT = 0x80;

    public static final int FORMAT_MASK     = 0x000F, FORMAT_SHIFT    = 0;
    public static final int ACCESS_MASK     = 0x0300, ACCESS_SHIFT    = 8;
    public static final int ACCESS_PUBLIC   = 0x0100;
    public static final int ACCESS_PROTECTED= 0x0200;
    public static final int ACCESS_PRIVATE  = 0x0300;
    public static final int ABSTRACT_BIT    = 0x0400, ABSTRACT_SHIFT  = 10;
    public static final int STATIC_BIT      = 0x0800, STATIC_SHIFT    = 11;
    public static final int SYNTHETIC_BIT   = 0x1000, SYNTHETIC_SHIFT = 12;


    // ----- fields --------------------------------------------------------------------------------

    /**
     * This is the next youngest sibling that shares a conceptual parent and a name. Components have
     * siblings only when conditions kick in; consider a module that contains a class named "util"
     * in version 1 that is replaced with a package in version 2 and version 3. Some arbitrary first
     * sibling would have the identity of Class:(moduleconstant, "util") and a format of CLASS, with
     * a sibling with the identify of Package:(moduleconstant, "util") and a format of PACKAGE (and
     * possibly one further sibling if there were changes to the package structure between version
     * 2 and 3.)
     */
    private Component m_sibling;

    /**
     * This is the identity constant for the Component. Because the identity constant is of a
     * certain type (e.g. package, class, ...), it may not be shared by all of the siblings with
     * the same name, if they are of different formats.
     */
    private Constant m_constId;

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
     * This holds all of the children of all of the siblings, except for methods (because they are
     * identified by signature, not by name). Because a single child may turn out to be a child of
     * more than one sibling (based on which condition applies), the child can only determine its
     * real parent by asking the assumed parent's assumed parent for the child by the name of the
     * assumed parent. Similarly, the child obtained by name from this map is just the first of the
     * siblings by that name, only one of which (at most) is the child that is existent for a
     * specified name.
     */
    private Map<String, Component> m_childByName;

    private Map<MethodConstant, MethodStructure> m_methodByConstant;
    }
