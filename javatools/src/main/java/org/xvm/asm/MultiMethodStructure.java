package org.xvm.asm;


import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.ListMap;


/**
 * An XVM Structure that represents a multi-method, which is a group of methods that share a name.
 * The multi-method does not have a corresponding development-time analogy; a developer does NOT
 * declare or define a multi-method. Instead, it is a compile-time construction, used to collect
 * together methods that share a name into a group, within which they are identified by a more
 * exacting set of attributes, namely their accessibility and their parameter/return types.
 */
public class MultiMethodStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MultiMethodStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MultiMethodStructure(XvmStructure xsParent, int nFlags, MultiMethodConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        assert Format.fromFlags(nFlags) == Format.MULTIMETHOD;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MultiMethodConstant getIdentityConstant()
        {
        return (MultiMethodConstant) super.getIdentityConstant();
        }

    protected void assembleChildren(DataOutput out)
            throws IOException
        {
        if (getParent().getFormat() == Format.CONST && !m_tloIgnoreNative.get())
            {
            // ensure we don't persist the (funky) Const interface functions created by
            // ClassStructure.synthesizeConstInterface();
            // note that the super.assembleChildren() method uses just two virtual methods:
            //      getChildrenCount(), and children()
            // hence we only need to override those two and ignore native methods when necessary
            m_tloIgnoreNative.set(true);
            try
                {
                super.assembleChildren(out);
                }
            finally
                {
                m_tloIgnoreNative.set(false);
                }
            }
        else
            {
            super.assembleChildren(out);
            }
        }


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public int getChildrenCount()
        {
        ensureChildren();

        Map<MethodConstant, MethodStructure> map = m_methodByConstant;
        return  map == null             ? 0
              : m_tloIgnoreNative.get() ? (int) map.values().stream().filter(m -> !m.isTransient()).count()
              : map.size();
        }

    @Override
    public boolean hasChildren()
        {
        return getChildrenCount() > 0;
        }

    @Override
    protected void replaceChildIdentityConstant(IdentityConstant idOld, IdentityConstant idNew)
        {
        assert idOld instanceof MethodConstant;
        assert idNew instanceof MethodConstant;

        Map<MethodConstant, MethodStructure> map = m_methodByConstant;
        if (map != null)
            {
            MethodStructure child = map.remove(idOld);
            if (child != null)
                {
                map.put((MethodConstant) idNew, child);
                }
            }
        }

    @Override
    protected boolean addChild(Component child)
        {
        // MultiMethodStructure can only hold MethodStructures
        assert child instanceof MethodStructure;

        Map<MethodConstant, MethodStructure> kids    = ensureMethodByConstantMap();
        MethodStructure                      method  = (MethodStructure) child;
        MethodConstant                       id      = method.getIdentityConstant();
        MethodStructure                      sibling = kids.get(id);
        if (sibling == null)
            {
            kids.put(id, method);
            }
        else if (isSiblingAllowed())
            {
            linkSibling(method, sibling);
            }
        else
            {
            return false;
            }

        markModified();
        return true;
        }

    @Override
    protected void adoptChildren(Component that)
        {
        assert that instanceof MultiMethodStructure;

        super.adoptChildren(that);

        m_methodByConstant = ((MultiMethodStructure) that).m_methodByConstant;
        }

    @Override
    public void removeChild(Component child)
        {
        assert child instanceof MethodStructure;
        assert child.getParent() == this;

        Map<MethodConstant, MethodStructure> kids = ensureMethodByConstantMap();

        MethodStructure method = (MethodStructure) child;
        MethodConstant  id     = method.getIdentityConstant();

        MethodStructure sibling = kids.remove(id);

        unlinkSibling(kids, id, child, sibling);
        }

    @Override
    protected boolean areChildrenIdentical(Component that)
        {
        ensureChildren();
        return equalChildMaps(this.getMethodByConstantMap(),
            ((MultiMethodStructure) that).getMethodByConstantMap());
        }

    @Override
    public Component getChild(Constant constId)
        {
        assert constId instanceof MethodConstant;

        MethodStructure firstSibling = getMethodByConstantMap().get(constId);

        return findLinkedChild(constId, firstSibling);
        }

    @Override
    public Collection<? extends Component> children()
        {
        return m_tloIgnoreNative.get()
                ? methods().stream().
                    filter(method -> !method.isTransient()).collect(Collectors.toList())
                : methods();
        }

    @Override
    public List<Component> safeChildren()
        {
        List<Component> list = new ArrayList<>();

        for (MethodConstant id : getMethodByConstantMap().keySet())
            {
            MethodStructure method = (MethodStructure) getChild(id);
            if (method != null)
                {
                list.add(method);
                }
            }

        return list;
        }

    @Override
    protected boolean canBeSeen(Access access)
        {
        for (MethodConstant id : getMethodByConstantMap().keySet())
            {
            MethodStructure method = (MethodStructure) getChild(id);
            if (method.canBeSeen(access))
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public boolean isAutoNarrowingAllowed()
        {
        return getParent().isAutoNarrowingAllowed();
        }

    @Override
    public MethodStructure findMethod(SignatureConstant sig)
        {
        for (MethodStructure method : methods())
            {
            if (method.getIdentityConstant().getSignature().equals(sig))
                {
                return method;
                }
            }

        return null;
        }

    @Override
    protected Component cloneBody()
        {
        MultiMethodStructure that = (MultiMethodStructure) super.cloneBody();

        that.m_methodByConstant = null;

        return that;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Create the method with the specified attributes.
     *
     * @param fFunction    true if the structure being created is a function; false means a method
     * @param access       an access specifier
     * @param annotations  the annotations
     * @param aReturns     the return values (zero or more)
     * @param aParams      the parameters (zero or more)
     * @param fHasCode     true indicates that the method has code
     * @param fUsesSuper   true indicates that the method is known to reference "super"
     *
     * @return a method structure or null if an equivalent structure already exists
     */
    public MethodStructure createMethod(boolean fFunction, Access access, Annotation[] annotations,
            Parameter[] aReturns, Parameter[] aParams, boolean fHasCode, boolean fUsesSuper)
        {
        int nFlags   = Format.METHOD.ordinal() | access.FLAGS | (fFunction ? Component.STATIC_BIT : 0);
        int cReturns = aReturns.length;
        int cParams  = aParams.length;

        if (annotations == null)
            {
            annotations = Annotation.NO_ANNOTATIONS;
            }

        TypeConstant[] aconstReturns = new TypeConstant[cReturns];
        TypeConstant[] aconstParams  = new TypeConstant[cParams ];

        for (int i = 0; i < cReturns; ++i)
            {
            Parameter param = aReturns[i];
            if (param.isConditionalReturn())
                {
                if (i > 0 || !param.getType().isEcstasy("Boolean"))
                    {
                    throw new IllegalArgumentException("only the first return value can be conditional, and it must be a boolean");
                    }
                }
            aconstReturns[i] = param.getType();
            }

        boolean fPastTypeParams = false;
        for (int i = 0; i < cParams; ++i)
            {
            Parameter param = aParams[i];
            if (param.isTypeParameter())
                {
                if (fPastTypeParams)
                    {
                    throw new IllegalArgumentException("type params must come first (" + i + ")");
                    }
                if (!param.getType().isEcstasy("Type"))
                    {
                    throw new IllegalArgumentException("type params must be of type \"Type\" (" + param.getType() + ")");
                    }
                }
            else
                {
                fPastTypeParams = true;
                }
            aconstParams[i] = param.getType();
            }

        MethodConstant constId = getConstantPool().ensureMethodConstant(
                getIdentityConstant(), getName(), aconstParams, aconstReturns);
        MethodStructure struct = new MethodStructure(this, nFlags, constId, null, annotations,
                aReturns, aParams, fHasCode, fUsesSuper);

        return addChild(struct) ? struct : null;
        }

    /**
     * Create the MethodStructure for a lambda.
     *
     * @param atypeParams the type of each declared parameter (optional)
     * @param asParams    the name of each declared parameter (null is permitted if none)
     *
     * @return a MethodStructure for the lambda
     */
    public MethodStructure createLambda(TypeConstant[] atypeParams, String[] asParams)
        {
        assert getName().equals("->");

        int nMax = 0;
        for (MethodConstant id : ensureMethodByConstantMap().keySet())
            {
            nMax = Math.max(nMax, id.getLambdaIndex());
            }

        ConstantPool pool    = getConstantPool();
        int          cParams = asParams == null ? 0 : asParams.length;
        Parameter[]  aParams = Parameter.NO_PARAMS;
        if (cParams > 0)
            {
            aParams = new Parameter[cParams];

            int cTypes = atypeParams == null ? 0 : atypeParams.length;
            assert cTypes == 0 || cTypes == cParams;

            for (int i = 0; i < cParams; ++i)
                {
                TypeConstant type = i < cTypes ? atypeParams[i] : pool.typeObject();
                aParams[i] = new Parameter(pool, type, asParams[i], null, false, i, false);
                }
            }

        MethodConstant id = new MethodConstant(pool, getIdentityConstant(), nMax + 1);
        int nFlags = Format.METHOD.ordinal() | Component.ACCESS_PRIVATE | Component.STATIC_BIT;
        MethodStructure struct = new MethodStructure(this, nFlags, id, null,
                Annotation.NO_ANNOTATIONS, Parameter.NO_PARAMS, aParams, true, false);

        return addChild(struct) ? struct : null;
        }

    /**
     * Helper method to return a collection of methods.
     */
    public Collection<MethodStructure> methods()
        {
        Collection<MethodStructure> methods = getMethodByConstantMap().values();

        assert (methods = Collections.unmodifiableCollection(methods)) != null;
        return methods;
        }

    /**
     * Obtain a read-only map of all method children identified by method signature constant.
     * <p/>
     * Note: the returned map contains only methods
     *
     * @return a read-only map from method constant to method component; never null, even if there
     *         are no child methods
     */
    public Map<MethodConstant, MethodStructure> getMethodByConstantMap()
        {
        ensureChildren();
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
        ensureChildren();

        Map<MethodConstant, MethodStructure> map = m_methodByConstant;
        if (map == null)
            {
            map = new ListMap<>();

            // store the map on every one of the siblings (including this component)
            for (Iterator<Component> siblings = siblings(); siblings.hasNext(); )
                {
                ((MultiMethodStructure) siblings.next()).m_methodByConstant = map;
                }

            // the corresponding field on this component should now be initialized
            assert m_methodByConstant == map;
            }
        return map;
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * This holds all of the method children. See the explanation of Component.m_childByName.
     */
    private Map<MethodConstant, MethodStructure> m_methodByConstant;

    /**
     * The flag used by the serialization logic.
     */
    private static final ThreadLocal<Boolean> m_tloIgnoreNative =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    }