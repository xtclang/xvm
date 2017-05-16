package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * An XVM structure that can contain MultiMethodStructure (and thus MethodStructure) objects.
 * Despite its name, the MethodContainer does not directly contain methods; instead it contains
 * Multi-Methods, which are (like properties and other structures) identified simply by name.
 */
public abstract class MethodContainer
        extends Component
    {
    // ----- constructors --------------------------------------------------

    /**
     * Construct a MethodContainer.
     *
     * @param xsParent  the containing XVM structure
     * @param constId   the identity constant for this XVM structure
     */
    protected MethodContainer(XvmStructure xsParent, Constant constId)
        {
        super(xsParent);
        assert constId != null;
        this.constId = constId;
        }

    // ----- MethodStructure children --------------------------------------

    /**
     * @return a set of method names contained within this MethodContainer; the caller must
     *         treat the set as a read-only object
     */
    public Set<String> methodNames()
        {
        if (multimethodsByName == null)
            {
            return Collections.EMPTY_SET;
            }

        Set<String> names = multimethodsByName.keySet();
        // if assertions are enabled, wrap it as unmodifiable
        assert (names = Collections.unmodifiableSet(names)) != null;
        return names;
        }

    /**
     * Obtain the MultiMethodStructure for the given name. The MultiMethodStructure represents
     * all of the methods that share the same name.
     *
     * @param sName  the method name
     *
     * @return the MultiMethodStructure that represents all of the methods with the specified
     *         name
     */
    public MultiMethodStructure getMultiMethod(String sName)
        {
        return multimethodsByName == null
                ? null
                : multimethodsByName.get(sName);
        }

    /**
     * Obtain an Iterable for all of the methods of a given name.
     *
     * @param sName  a method name
     *
     * @return all of the methods with the specified name
     */
    public Iterable<MethodStructure> methodsByName(String sName)
        {
        if (multimethodsByName == null)
            {
            return Collections.EMPTY_SET;
            }

        MultiMethodStructure multimethod = multimethodsByName.get(sName);
        return multimethod == null
                ? Collections.EMPTY_LIST
                : multimethod.methods();
        }

    /**
     * Remove the specified method from this structure.
     *
     * @param constMethod  the identifier of the method
     */
    protected void deleteMethod(MethodConstant constMethod)
        {
        // this will have to remove it from the multi-method, and if it is the last method in
        // the multi-method, it will have to remove the multi-method as well
        throw new UnsupportedOperationException();
        }

    /**
     * Obtain a read-only map from String name to MultiMethodStructure.
     *
     * @return a non-null Map containing the various MultiMethodStructure objects keyed by name
     */
    public Map<String, MultiMethodStructure> getMethodMap()
        {
        Map<String, MultiMethodStructure> map = multimethodsByName;
        if (map == null)
            {
            return Collections.EMPTY_MAP;
            }

        assert (map = Collections.unmodifiableMap(map)) != null;
        return map;
        }

    /**
     * Obtain a mutable map from name to MultiMethodStructure.
     *
     * @return a non-null Map containing the various MultiMethodStructure objects keyed by
     *         method name
     */
    protected Map<String, MultiMethodStructure> ensureMultiMethodMap()
        {
        Map<String, MultiMethodStructure> map = multimethodsByName;
        if (map == null)
            {
            multimethodsByName = map = new HashMap<>();
            }
        return map;
        }

    /**
     * Obtain (creating if necessary) the multi-method for the specified name.
     *
     * @param sName the (multi-)method name
     *
     * @return the MultiMethodStructure for the specified name
     */
    protected MultiMethodStructure ensureMultiMethodStructure(String sName)
        {
        Map<String, MultiMethodStructure> map = ensureMultiMethodMap();
        MultiMethodStructure multimethod = map.get(sName);
        if (multimethod == null)
            {
            multimethod = new MultiMethodStructure(this,
                    getConstantPool().ensureMultiMethodConstant(getIdentityConstant(), sName));
            map.put(sName, multimethod);
            }
        return multimethod;
        }

    /**
     * Create a MethodStructure with the specified name, but whose identity may not yet be fully
     * realized / resolved.
     *
     * @param fFunction    true if the method is actually a function (not a method)
     * @param access       the access flag for the method
     * @param returnTypes  the return types of the method
     * @param sName        the method name, or null if the name is unknown
     * @param paramTypes   the parameter types for the method
     *
     * @return a new MethodStructure
     */
    public MethodStructure createMethod(boolean fFunction, Access access, TypeConstant[] returnTypes, String sName, TypeConstant[] paramTypes)
        {
        assert sName != null;
        assert access != null;

        MultiMethodStructure multimethod = ensureMultiMethodStructure(sName);
        return multimethod.createMethod(fFunction, access, returnTypes, paramTypes);
        }

    // ----- XvmStructure methods ------------------------------------------

    @Override
    public Constant getIdentityConstant()
        {
        return constId;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return getMethodMap().values().iterator();
        }

    @Override
    public ConditionalConstant getCondition()
        {
        return condition;
        }

    @Override
    protected void setCondition(ConditionalConstant condition)
        {
        this.condition = condition;
        markModified();
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        List<MultiMethodStructure> list = (List<MultiMethodStructure>) disassembleSubStructureCollection(in);
        if (list.isEmpty())
            {
            multimethodsByName = null;
            }
        else
            {
            Map<String, MultiMethodStructure> map = ensureMultiMethodMap();
            map.clear();
            for (MultiMethodStructure struct : list)
                {
                map.put(struct.getName(), struct);
                }
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        constId = pool.register(constId);
        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        assembleSubStructureCollection(getMethodMap().values(), out);
        }

    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("id=")
                .append(getIdentityConstant())
                .append(", condition=")
                .append(getCondition())
                .append(", ")
                .append(super.getDescription())
                .toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());

        dumpStructureMap(out, sIndent, "Methods", multimethodsByName);
        }

    // ----- Object methods ------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof org.xvm.asm.MethodContainer))
            {
            return false;
            }

        org.xvm.asm.MethodContainer that = (org.xvm.asm.MethodContainer) obj;
        return this.constId.equals(that.constId)
                && equalMaps(this.multimethodsByName, that.multimethodsByName);
        }


    // ----- fields --------------------------------------------------------

    /**
     * The identity constant for this XVM structure.
     */
    private Constant constId;

    /**
     * An optional ConditionalConstant that determines under what conditions this XvmStructure
     * will be present after the linking process is finished.
     */
    private ConditionalConstant condition;

    /**
     * A lazily instantiated String-to-MultiMethodStructure lookup table.
     */
    private Map<String, MultiMethodStructure> multimethodsByName;
    }
