package org.xvm.asm;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.ConditionalConstant;
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
     * @param xsParent   the containing XVM structure
     * @param nFlags     the Component bit flags
     * @param constId    the identity constant for this XVM structure
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MethodContainer(XvmStructure xsParent, int nFlags, Constant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- MethodStructure children --------------------------------------

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

    // TODO - do we need these
//    /**
//     * @return a set of method names contained within this MethodContainer; the caller must
//     *         treat the set as a read-only object
//     */
//    public Set<String> methodNames()
//        {
//        if (multimethodsByName == null)
//            {
//            return Collections.EMPTY_SET;
//            }
//
//        Set<String> names = multimethodsByName.keySet();
//        // if assertions are enabled, wrap it as unmodifiable
//        assert (names = Collections.unmodifiableSet(names)) != null;
//        return names;
//        }
//
//    /**
//     * Obtain the MultiMethodStructure for the given name. The MultiMethodStructure represents
//     * all of the methods that share the same name.
//     *
//     * @param sName  the method name
//     *
//     * @return the MultiMethodStructure that represents all of the methods with the specified
//     *         name
//     */
//    public MultiMethodStructure getMultiMethod(String sName)
//        {
//        return multimethodsByName == null
//                ? null
//                : multimethodsByName.get(sName);
//        }
//
//    /**
//     * Obtain an Iterable for all of the methods of a given name.
//     *
//     * @param sName  a method name
//     *
//     * @return all of the methods with the specified name
//     */
//    public Iterable<MethodStructure> methodsByName(String sName)
//        {
//        if (multimethodsByName == null)
//            {
//            return Collections.EMPTY_SET;
//            }
//
//        MultiMethodStructure multimethod = multimethodsByName.get(sName);
//        return multimethod == null
//                ? Collections.EMPTY_LIST
//                : multimethod.methods();
//        }
//
//    /**
//     * Obtain a read-only map from String name to MultiMethodStructure.
//     *
//     * @return a non-null Map containing the various MultiMethodStructure objects keyed by name
//     */
//    public Map<String, MultiMethodStructure> getMethodMap()
//        {
//        Map<String, MultiMethodStructure> map = multimethodsByName;
//        if (map == null)
//            {
//            return Collections.EMPTY_MAP;
//            }
//
//        assert (map = Collections.unmodifiableMap(map)) != null;
//        return map;
//        }
//
//    /**
//     * Obtain a mutable map from name to MultiMethodStructure.
//     *
//     * @return a non-null Map containing the various MultiMethodStructure objects keyed by
//     *         method name
//     */
//    protected Map<String, MultiMethodStructure> ensureMultiMethodMap()
//        {
//        Map<String, MultiMethodStructure> map = multimethodsByName;
//        if (map == null)
//            {
//            multimethodsByName = map = new HashMap<>();
//            }
//        return map;
//        }

    /**
     * Obtain (creating if necessary) the multi-method for the specified name.
     *
     * @param sName the (multi-)method name
     *
     * @return the MultiMethodStructure for the specified name
     */
    protected MultiMethodStructure ensureMultiMethodStructure(String sName)
        {
        MultiMethodStructure multimethod = (MultiMethodStructure) getChild(sName);
        if (multimethod == null)
            {
            ConditionalConstant cond = null; // TODO get condition from assembler context
            multimethod = new MultiMethodStructure(this, Component.SYNTHETIC_BIT,
                    getConstantPool().ensureMultiMethodConstant(getIdentityConstant(), sName), cond);
            addChild(multimethod);
            }
        return multimethod;
        }
    }
