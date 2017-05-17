package org.xvm.asm;


import java.util.Map;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * An XVM structure that can contain ClassStructure and PropertyStructure objects, in addition
 * to MethodStructure objects.
 */
public abstract class ClassContainer
        extends MethodContainer
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassContainer.
     *
     * @param xsParent   the containing XVM structure
     * @param nFlags     the Component bit flags
     * @param constId    the identity constant for this XVM structure
     * @param condition  the optional condition for this ModuleStructure
     */
    protected ClassContainer(XvmStructure xsParent, int nFlags, Constant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- ClassStructure children ---------------------------------------------------------------

    // TODO do we need these?
//    /**
//     * Get an iterator over the class names that are contained
//     * immediately within this structure.
//     *
//     * @return an Iterator of class names
//     */
//    public Iterator<String> classNames()
//        {
//        final Map<String, ClassStructure> mapClass = m_mapClass;
//        return mapClass == null
//                ? Collections.emptyIterator()
//                : mapClass.keySet().iterator();
//        }
//
//    /**
//     * Find the class with the specified name.
//     *
//     * @param sName  the name of the class
//     *
//     * @return the ClassStructure for the specified class, or null if
//     *         it does not exist
//     */
//    public ClassStructure getClass(String sName)
//        {
//        final Map<String, ClassStructure> mapClass = m_mapClass;
//        return mapClass == null ? null : mapClass.get(sName);
//        }
//
//
//    /**
//     * Find the class with the specified name, creating it if necessary.
//     *
//     * @param sName  the name of the class
//     *
//     * @return the ClassStructure for the specified class
//     */
//    public ClassStructure ensureClass(String sName)
//        {
//        // see if the class already exists
//        final ClassStructure structclass = getClass(sName);
//        return structclass == null
//                ? createClass(sName)
//                : structclass;
//        }
//
//    /**
//     * Remove the specified class from this structure.
//     *
//     * @param sName  the name of the class
//     */
//    public void deleteClass(String sName)
//        {
//        final Map<String, ClassStructure> mapClass = m_mapClass;
//        if (mapClass != null)
//            {
//            mapClass.remove(sName);
//            }
//        }
//
//    /**
//     * Obtain a read-only map from String name to ClassStructure.
//     *
//     * @return a non-null Map containing the various ClassStructure
//     *         objects keyed by their names
//     */
//    protected Map<String, ClassStructure> getClassMap()
//        {
//        final Map<String, ClassStructure> mapClass = m_mapClass;
//        return mapClass == null ? Collections.EMPTY_MAP : mapClass;
//        }
//
//    /**
//     * Obtain a mutable map from String name to ClassStructure.
//     *
//     * @return a non-null Map containing the various ClassStructure
//     *         objects keyed by their non-qualified (simple) names
//     */
//    protected Map<String, ClassStructure> ensureClassMap()
//        {
//        Map<String, ClassStructure> mapClass = m_mapClass;
//        if (mapClass == null)
//            {
//            m_mapClass = mapClass = new HashMap<>();
//            }
//        return mapClass;
//        }

    /**
     * Create and register a ClassStructure with the specified class name.
     *
     * @param sName  the simple (unqualified) class name to create
     */
    public ClassStructure createClass(String sName)
        {
        assert sName != null;

        Map<String, ClassStructure> mapClass = ensureClassMap();
        assert !mapClass.containsKey(sName);

        final ConstantPool   pool = getConstantPool();
        final Constant       constthis = getIdentityConstant();
        final ClassConstant constclass = pool.ensureClassConstant(constthis, sName);
        final ClassStructure structclass = new ClassStructure(this, constclass);

        mapClass.put(sName, structclass);
        return structclass;
        }

    // ----- PropertyStructure children ------------------------------------------------------------

    // TODO
//    /**
//     * Get an iterator over the property names that are contained
//     * immediately within this structure.
//     *
//     * @return an Iterator of property names
//     */
//    public Iterator<String> propertyNames()
//        {
//        final Map<String, PropertyStructure> mapProperty = m_mapProperty;
//        return mapProperty == null
//                ? Collections.emptyIterator()
//                : mapProperty.keySet().iterator();
//        }
//
//    /**
//     * Find the property with the specified name.
//     *
//     * @param sName  the name of the property
//     *
//     * @return the PropertyStructure for the specified property, or null if
//     *         it does not exist
//     */
//    public PropertyStructure getProperty(String sName)
//        {
//        final Map<String, PropertyStructure> mapProperty = m_mapProperty;
//        return mapProperty == null ? null : mapProperty.get(sName);
//        }
//
//    /**
//     * Remove the specified property from this structure.
//     *
//     * @param sName  the name of the property
//     */
//    public void deleteProperty(String sName)
//        {
//        final Map<String, PropertyStructure> mapProperty = m_mapProperty;
//        if (mapProperty != null)
//            {
//            mapProperty.remove(sName);
//            }
//        }
//
//    /**
//     * Obtain a read-only map from String name to PropertyStructure.
//     *
//     * @return a non-null Map containing the various PropertyStructure
//     *         objects keyed by their names
//     */
//    protected Map<String, PropertyStructure> getPropertyMap()
//        {
//        final Map<String, PropertyStructure> mapProperty = m_mapProperty;
//        return mapProperty == null ? Collections.EMPTY_MAP : mapProperty;
//        }
//
//    /**
//     * Obtain a mutable map from String name to PropertyStructure.
//     *
//     * @return a non-null Map containing the various PropertyStructure
//     *         objects keyed by their non-qualified (simple) names
//     */
//    protected Map<String, PropertyStructure> ensurePropertyMap()
//        {
//        Map<String, PropertyStructure> mapProperty = m_mapProperty;
//        if (mapProperty == null)
//            {
//            m_mapProperty = mapProperty = new HashMap<>();
//            }
//        return mapProperty;
//        }

    /**
     * Create and register a PropertyStructure with the specified property type and name.
     *
     * @param fConst  true if the property is a constant
     * @param access  the accessibility of the property to create
     * @param type    the type of the property to create
     * @param sName   the simple (unqualified) property name to create
     */
    public PropertyStructure createProperty(boolean fConst, Access access, TypeConstant type, String sName)
        {
        assert access != null;
        assert type != null;
        assert sName != null;

        Map<String, PropertyStructure> mapProperty = ensurePropertyMap();
        assert !mapProperty.containsKey(sName);

        ConstantPool pool = getConstantPool();
        Constant constThis = getIdentityConstant();
        PropertyConstant constproperty = pool.ensurePropertyConstant(constThis, sName);
        PropertyStructure structproperty = new PropertyStructure(this, constproperty, fConst, access, type);

        mapProperty.put(sName, structproperty);
        return structproperty;
        }
    }
