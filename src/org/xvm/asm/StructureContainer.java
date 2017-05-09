package org.xvm.asm;


import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.util.LinkedIterator;
import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The StructureContainer abstract class is an XvmStructure that is intended to nest other
 * XvmStructures. Specifically, it tracks modifications and it supports conditional inclusion.
 * <p/>
 * Containment model, with container type on the left and containee type across the top:
 * <p/>
 * <code><pre>
 *           Module  Package  Class  Method  Property  |  Conditional
 * File        x                                       |
 * Module              x       x       x       x       |       x (ver)
 * Package             x       x       x       x       |       x
 * Class                       x       x       x       |       x
 * Property                            x               |       x
 * Method                      x       x       x       |       x
 * </pre></code>
 * <p/>
 * Based on the containment model, of these types, there are three groups of containment:
 * <ul>
 * <li><i>Method</i> - the {@link MethodContainer MethodContainer}</li>
 * <li><i>Method + Property + Class</i> - the {@link ClassContainer ClassContainer}</li>
 * <li><i>Method + Property + Class + Package</i> - the {@link PackageContainer PackageContainer}</li>
 * </ul>
 *
 * @author cp 2016.09.22
 */
public abstract class StructureContainer
        extends XvmStructure
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an XVM structure.
     *
     * @param xsParent  the containing XVM structure
     */
    protected StructureContainer(XvmStructure xsParent)
        {
        super(xsParent);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public abstract Iterator<? extends XvmStructure> getContained();

    @Override
    public boolean isModified()
        {
        return m_fModified || super.isModified();
        }

    @Override
    protected void markModified()
        {
        m_fModified = true;
        }

    @Override
    protected void resetModified()
        {
        super.resetModified();
        m_fModified = false;
        }

    @Override
    public String getDescription()
        {
        return "modified=" + m_fModified;
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create a new TypeConstant whose exact type will eventually be resolved.
     *
     * @param sType  the String representation of the type
     *
     * @return a new UnresolvedTypeConstant
     */
    public UnresolvedTypeConstant createUnresolvedType(String sType)
        {
        return getConstantPool().createUnresolvedTypeConstant(sType);
        }

    /**
     * Helper method to read a collection of XVM sub-structures from the DataInput stream.
     *
     * @param in  the DataInput containing the XVM structures
     *
     * @return a List of XvmStructure objects
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected List<? extends XvmStructure> disassembleSubStructureCollection(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);
        if (c == 0)
            {
            return Collections.EMPTY_LIST;
            }

        XvmStructure[] astruct = new XvmStructure[c];
        for (int i = 0; i < c; ++i)
            {
            astruct[i] = disassembleSubStructure(in);
            }
        return Arrays.asList(astruct);
        }

    /**
     * Helper method to read an XVM sub-structure from the DataInput stream.
     *
     * @param in  the DataInput containing the XVM structure
     *
     * @return an XvmStructure
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected XvmStructure disassembleSubStructure(DataInput in)
            throws IOException
        {
        // read in the identity of this file structure
        Constant constId = getConstantPool().getConstant(readMagnitude(in));

        if (constId.getFormat().isLengthEncoded())
            {
            // skip over length encoding
            readMagnitude(in);
            }

        XvmStructure structSub = constId.instantiate(this);
        structSub.disassemble(in);
        return structSub;
        }

    /**
     * Helper method to read a collection of type parameters.
     *
     * @param in  the DataInput containing the type parameters
     *
     * @return null if there are no type parameters, otherwise a map from CharStringConstant to the
     *         type constraint for each parameter
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected ListMap<CharStringConstant, TypeConstant> disassembleTypeParams(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);
        if (c <= 0)
            {
            assert c == 0;
            return null;
            }

        final ListMap<CharStringConstant, TypeConstant> map = new ListMap<>();
        final ConstantPool pool = getConstantPool();
        for (int i = 0; i < c; ++i)
            {
            CharStringConstant constName = (CharStringConstant) pool.getConstant(readIndex(in));
            TypeConstant       constType = (TypeConstant)       pool.getConstant(readIndex(in));
            assert !map.containsKey(constName);
            map.put(constName, constType);
            }
        return map;
        }

    /**
     * Helper method to write an XVM sub-structure to the DataInput stream.
     *
     * @param coll  the collection of XVM structure to assemble
     * @param out   the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleSubStructureCollection(Collection<? extends XvmStructure> coll, DataOutput out)
            throws IOException
        {
        int c = coll.size();
        writePackedLong(out, c);
        if (c == 0)
            {
            return;
            }

        XvmStructure[] astruct = coll.toArray(new XvmStructure[c]);
        Arrays.sort(astruct, IDENTITY_CONSTANT_COMPARATOR);

        for (int i = 0; i < c; ++i)
            {
            assembleSubStructure(astruct[i], out);
            }
        }

    /**
     * Helper method to write an XVM sub-structure to the DataInput stream.
     *
     * @param structSub  the XVM structure to assemble
     * @param out        the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleSubStructure(XvmStructure structSub, DataOutput out)
            throws IOException
        {
        Constant constId = structSub.getIdentityConstant();
        writePackedLong(out, constId.getPosition());

        if (constId.getFormat().isLengthEncoded())
            {
            // length-encode the sub-structure (allowing a reader to optionally
            // skip over it)
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            structSub.assemble(new DataOutputStream(outBuf));
            byte[] ab = outBuf.toByteArray();
            writePackedLong(out, ab.length);
            out.write(ab);
            }
        else
            {
            structSub.assemble(out);
            }
        }

    /**
     * Helper method to write type parameters to the DataOutput stream.
     *
     * @param map  the type parameters
     * @param out  the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleTypeParams(ListMap<CharStringConstant, TypeConstant> map, DataOutput out)
            throws IOException
        {
        int c = map == null ? 0 : map.size();
        writePackedLong(out, c);

        if (c == 0)
            {
            return;
            }

        for (Map.Entry<CharStringConstant, TypeConstant> entry : map.entrySet())
            {
            writePackedLong(out, entry.getKey().getPosition());
            writePackedLong(out, entry.getValue().getPosition());
            }
        }

    /**
     * Compare two lazily instantiated maps for equality.
     *
     * @param mapThis  a map, or null
     * @param mapThat  a map, or null
     *
     * @return false iff at least one map is non-null and non-empty, and the other map is null or
     *         their contents do not match
     */
    protected static boolean equalMaps(Map mapThis, Map mapThat)
        {
        int cThis = mapThis == null ? 0 : mapThis.size();
        int cThat = mapThat == null ? 0 : mapThat.size();
        return cThis == cThat && (cThis == 0 || mapThis.equals(mapThat));
        }


    // ----- inner class: MethodContainer ----------------------------------------------------------

    /**
     * An XVM structure that can contain MultiMethodStructure (and thus MethodStructure) objects.
     * Despite its name, the MethodContainer does not directly contain methods; instead it contains
     * Multi-Methods, which are (like properties and other structures) identified simply by name.
     */
    public abstract static class MethodContainer
            extends StructureContainer
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

            if (!(obj instanceof MethodContainer))
                {
                return false;
                }

            MethodContainer that = (MethodContainer) obj;
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


    // ----- inner class: ClassContainer -----------------------------------------------------------

    /**
     * An XVM structure that can contain ClassStructure and PropertyStructure objects, in addition
     * to MethodStructure objects.
     */
    public abstract static class ClassContainer
            extends MethodContainer
        {
        // ----- constructors ----------------------------------------------

        /**
         * Construct a ClassContainer.
         *
         * @param xsParent  the containing XVM structure
         * @param constId   the identity constant for this XVM structure
         */
        protected ClassContainer(XvmStructure xsParent, Constant constId)
            {
            super(xsParent, constId);
            }

        // ----- XvmStructure methods --------------------------------------

        @Override
        public Iterator<? extends XvmStructure> getContained()
            {
            return new LinkedIterator(
                    super.getContained(),
                    getClassMap().values().iterator(),
                    getPropertyMap().values().iterator());
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            super.disassemble(in);

            List<ClassStructure> listStructClass =
                    (List<ClassStructure>) disassembleSubStructureCollection(in);
            if (listStructClass.isEmpty())
                {
                m_mapClass = null;
                }
            else
                {
                Map<String, ClassStructure> mapClass = ensureClassMap();
                mapClass.clear();
                for (ClassStructure struct : listStructClass)
                    {
                    mapClass.put(struct.getClassConstant().getName(), struct);
                    }
                }

            List<PropertyStructure> listStructProperty =
                    (List<PropertyStructure>) disassembleSubStructureCollection(in);
            if (listStructProperty.isEmpty())
                {
                m_mapProperty = null;
                }
            else
                {
                Map<String, PropertyStructure> mapProperty = ensurePropertyMap();
                mapProperty.clear();
                for (PropertyStructure struct : listStructProperty)
                    {
                    mapProperty.put(struct.getPropertyConstant().getName(), struct);
                    }
                }
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            super.assemble(out);
            assembleSubStructureCollection(getClassMap().values(), out);
            assembleSubStructureCollection(getPropertyMap().values(), out);
            }

        @Override
        protected void dump(PrintWriter out, String sIndent)
            {
            super.dump(out, sIndent);
            dumpStructureMap(out, sIndent, "Properties", m_mapProperty);
            dumpStructureMap(out, sIndent, "Classes", m_mapClass);
            }

        // ----- Object methods --------------------------------------------

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (!(obj instanceof ClassContainer && super.equals(obj)))
                {
                return false;
                }

            // compare classes & properties
            ClassContainer that = (ClassContainer) obj;
            return equalMaps(this.m_mapClass, that.m_mapClass)
                    && equalMaps(this.m_mapProperty, that.m_mapProperty);
            }

        // ----- ClassStructure children -----------------------------------

        /**
         * Get an iterator over the class names that are contained
         * immediately within this structure.
         *
         * @return an Iterator of class names
         */
        public Iterator<String> classNames()
            {
            final Map<String, ClassStructure> mapClass = m_mapClass;
            return mapClass == null
                    ? Collections.emptyIterator()
                    : mapClass.keySet().iterator();
            }

        /**
         * Find the class with the specified name.
         *
         * @param sName  the name of the class
         *
         * @return the ClassStructure for the specified class, or null if
         *         it does not exist
         */
        public ClassStructure getClass(String sName)
            {
            final Map<String, ClassStructure> mapClass = m_mapClass;
            return mapClass == null ? null : mapClass.get(sName);
            }


        /**
         * Find the class with the specified name, creating it if necessary.
         *
         * @param sName  the name of the class
         *
         * @return the ClassStructure for the specified class
         */
        public ClassStructure ensureClass(String sName)
            {
            // see if the class already exists
            final ClassStructure structclass = getClass(sName);
            return structclass == null
                    ? createClass(sName)
                    : structclass;
            }

        /**
         * Remove the specified class from this structure.
         *
         * @param sName  the name of the class
         */
        public void deleteClass(String sName)
            {
            final Map<String, ClassStructure> mapClass = m_mapClass;
            if (mapClass != null)
                {
                mapClass.remove(sName);
                }
            }

        /**
         * Obtain a read-only map from String name to ClassStructure.
         *
         * @return a non-null Map containing the various ClassStructure
         *         objects keyed by their names
         */
        protected Map<String, ClassStructure> getClassMap()
            {
            final Map<String, ClassStructure> mapClass = m_mapClass;
            return mapClass == null ? Collections.EMPTY_MAP : mapClass;
            }

        /**
         * Obtain a mutable map from String name to ClassStructure.
         *
         * @return a non-null Map containing the various ClassStructure
         *         objects keyed by their non-qualified (simple) names
         */
        protected Map<String, ClassStructure> ensureClassMap()
            {
            Map<String, ClassStructure> mapClass = m_mapClass;
            if (mapClass == null)
                {
                m_mapClass = mapClass = new HashMap<>();
                }
            return mapClass;
            }

        /**
         * Create and register a ClassStructure with the specified class name.
         *
         * @param sName  the simple (unqualified) class name to create
         */
        protected ClassStructure createClass(String sName)
            {
            assert sName != null;

            Map<String, ClassStructure> mapClass = ensureClassMap();
            assert !mapClass.containsKey(sName);

            final ConstantPool   pool = getConstantPool();
            final Constant       constthis = getIdentityConstant();
            final ClassConstant  constclass = pool.ensureClassConstant(constthis, sName);
            final ClassStructure structclass = new ClassStructure(this, constclass);

            mapClass.put(sName, structclass);
            return structclass;
            }

        // ----- PropertyStructure children --------------------------------

        /**
         * Get an iterator over the property names that are contained
         * immediately within this structure.
         *
         * @return an Iterator of property names
         */
        public Iterator<String> propertyNames()
            {
            final Map<String, PropertyStructure> mapProperty = m_mapProperty;
            return mapProperty == null
                    ? Collections.emptyIterator()
                    : mapProperty.keySet().iterator();
            }

        /**
         * Find the property with the specified name.
         *
         * @param sName  the name of the property
         *
         * @return the PropertyStructure for the specified property, or null if
         *         it does not exist
         */
        public PropertyStructure getProperty(String sName)
            {
            final Map<String, PropertyStructure> mapProperty = m_mapProperty;
            return mapProperty == null ? null : mapProperty.get(sName);
            }

        /**
         * Remove the specified property from this structure.
         *
         * @param sName  the name of the property
         */
        public void deleteProperty(String sName)
            {
            final Map<String, PropertyStructure> mapProperty = m_mapProperty;
            if (mapProperty != null)
                {
                mapProperty.remove(sName);
                }
            }

        /**
         * Obtain a read-only map from String name to PropertyStructure.
         *
         * @return a non-null Map containing the various PropertyStructure
         *         objects keyed by their names
         */
        protected Map<String, PropertyStructure> getPropertyMap()
            {
            final Map<String, PropertyStructure> mapProperty = m_mapProperty;
            return mapProperty == null ? Collections.EMPTY_MAP : mapProperty;
            }

        /**
         * Obtain a mutable map from String name to PropertyStructure.
         *
         * @return a non-null Map containing the various PropertyStructure
         *         objects keyed by their non-qualified (simple) names
         */
        protected Map<String, PropertyStructure> ensurePropertyMap()
            {
            Map<String, PropertyStructure> mapProperty = m_mapProperty;
            if (mapProperty == null)
                {
                m_mapProperty = mapProperty = new HashMap<>();
                }
            return mapProperty;
            }

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

        // ----- data members ----------------------------------------------

        /**
         * A lazily instantiated name-to-class lookup table.
         */
        private Map<String, ClassStructure> m_mapClass;

        /**
         * A lazily instantiated name-to-propertylookup table.
         */
        private Map<String, PropertyStructure> m_mapProperty;
        }


    // ----- inner class: PackageContainer ---------------------------------------------------------

    /**
     * An XVM structure that can contain PackageStructure objects, in addition to ClassStructure,
     * PropertyStructure, and MethodStructure objects.
     */
    public abstract static class PackageContainer
            extends ClassContainer
        {
        // ----- constructors ----------------------------------------------

        /**
         * Construct a PackageContainer.
         *
         * @param xsParent  the containing XVM structure
         * @param constId   the identity constant for this XVM structure
         */
        protected PackageContainer(XvmStructure xsParent, Constant constId)
            {
            super(xsParent, constId);
            }

        // ----- XvmStructure methods --------------------------------------

        @Override
        public Iterator<? extends XvmStructure> getContained()
            {
            final Map map = m_mapPackage;
            final Iterator iter = super.getContained();
            return map == null
                    ? iter
                    : new LinkedIterator<>(iter, map.values().iterator());
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            super.disassemble(in);

            List<PackageStructure> listStruct = (List<PackageStructure>) disassembleSubStructureCollection(in);
            if (listStruct.isEmpty())
                {
                m_mapPackage = null;
                }
            else
                {
                Map<String, PackageStructure> mapPackage = ensurePackageMap();
                mapPackage.clear();
                for (PackageStructure struct : listStruct)
                    {
                    mapPackage.put(struct.getPackageConstant().getName(), struct);
                    }
                }
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            super.assemble(out);
            assembleSubStructureCollection(getPackageMap().values(), out);
            }

        @Override
        protected void dump(PrintWriter out, String sIndent)
            {
            super.dump(out, sIndent);
            dumpStructureMap(out, sIndent, "Packages", m_mapPackage);
            }

        // ----- Object methods --------------------------------------------

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (!(obj instanceof PackageContainer && super.equals(obj)))
                {
                return false;
                }

            // compare packages
            PackageContainer that = (PackageContainer) obj;
            return equalMaps(this.m_mapPackage, that.m_mapPackage);
            }

        // ----- PackageStructure children ---------------------------------

        /**
         * Get an iterator over the package names that are contained immediately
         * within this structure.
         *
         * @return an Iterator of package names
         */
        public Iterator<String> packageNames()
            {
            final Map<String, PackageStructure> mapPackage = m_mapPackage;
            return mapPackage == null
                    ? Collections.emptyIterator()
                    : mapPackage.keySet().iterator();
            }

        /**
         * Find the package with the specified name.
         * <p>
         * This method allows a composite (dot-delimited) package name to be
         * specified.
         *
         * @param sName  the name of the package
         *
         * @return the PackageStructure for the specified package, or null if it
         *         does not exist
         */
        public PackageStructure getPackage(String sName)
            {
            // handle the case in which the name is a dot-delimited package name
            String sRemainder = null;
            int ofDot = sName.indexOf('.');
            if (ofDot >= 0)
                {
                sRemainder = sName.substring(ofDot + 1);
                sName = sName.substring(0, ofDot);
                }

            // see if the package already exists
            PackageStructure structpackage = lookupPackage(sName);
            return structpackage == null || sRemainder == null
                    ? structpackage
                    : structpackage.getPackage(sRemainder);
            }

        /**
         * Find the package with the specified name, creating it if necessary.
         * <p>
         * This method allows a composite (dot-delimited) package name to be
         * specified.
         *
         * @param sName  the name of the package
         *
         * @return the PackageStructure for the specified package
         */
        public PackageStructure ensurePackage(String sName)
            {
            // handle the case in which the name is a dot-delimited package name
            String sRemainder = null;
            int ofDot = sName.indexOf('.');
            if (ofDot >= 0)
                {
                sRemainder = sName.substring(ofDot + 1);
                sName = sName.substring(0, ofDot);
                }

            // see if the package already exists
            PackageStructure structpackage = lookupPackage(sName);
            if (structpackage == null)
                {
                // create & register the package
                structpackage = createPackage(sName);
                }

            return sRemainder == null ? structpackage :
                    structpackage.ensurePackage(sRemainder);
            }

        /**
         * Remove the specified package from this structure.
         * <p>
         * This method allows a composite (dot-delimited) package name to be
         * specified.
         *
         * @param sName  the name of the package
         */
        public void deletePackage(String sName)
            {
            final Map<String, PackageStructure> mapPackage = m_mapPackage;
            if (mapPackage != null)
                {
                // handle the case in which the name is a dot-delimited package name
                final int ofDot = sName.indexOf('.');
                if (ofDot >= 0)
                    {
                    PackageStructure structpackage = lookupPackage(sName.substring(0, ofDot));
                    if (structpackage != null)
                        {
                        structpackage.deletePackage(sName.substring(ofDot + 1));
                        }
                    }
                else
                    {
                    mapPackage.remove(sName);
                    }
                }
            }

        /**
         * Obtain a read-only map from String name to PackageStructure.
         *
         * @return a non-null Map containing the various PackageStructure
         *         objects keyed by their non-qualified (simple) names
         */
        protected Map<String, PackageStructure> getPackageMap()
            {
            final Map<String, PackageStructure> mapPackage = m_mapPackage;
            return mapPackage == null ? Collections.EMPTY_MAP : mapPackage;
            }

        /**
         * Obtain a mutable map from String name to PackageStructure.
         *
         * @return a non-null Map containing the various PackageStructure
         *         objects keyed by their non-qualified (simple) names
         */
        protected Map<String, PackageStructure> ensurePackageMap()
            {
            Map<String, PackageStructure> mapPackage = m_mapPackage;
            if (mapPackage == null)
                {
                m_mapPackage = mapPackage = new HashMap<>();
                }
            return mapPackage;
            }

        /**
         * Find the package with the specified name.
         *
         * @param sName  the simple (unqualified) package name to find
         *
         * @return the PackageStructure for the specified package, or null if no
         *         package with that name is contained immediately within this
         *         structure
         */
        protected PackageStructure lookupPackage(String sName)
            {
            final Map<String, PackageStructure> mapPackage = m_mapPackage;
            return mapPackage == null ? null : mapPackage.get(sName);
            }

        /**
         * Create and register a PackageStructure with the specified package name.
         *
         * @param sName  the simple (unqualified) package name to create
         */
        protected PackageStructure createPackage(String sName)
            {
            assert sName != null;

            Map<String, PackageStructure> mapPackage = ensurePackageMap();
            assert !mapPackage.containsKey(sName);

            final ConstantPool     pool         = getConstantPool();
            final Constant         constthis    = getIdentityConstant();
            final PackageConstant  constpackage = pool.ensurePackageConstant(constthis, sName);
            final PackageStructure structpackage = new PackageStructure(this, constpackage);

            mapPackage.put(sName, structpackage);
            return structpackage;
            }

        // ----- data members ----------------------------------------------

        /**
         * A lazily instantiated name-to-package lookup table.
         */
        private Map<String, PackageStructure> m_mapPackage;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A Comparator that compares two XvmStructure object for sorting purposes based on their
     * identity constants.
     */
    private static final Comparator<? super XvmStructure> IDENTITY_CONSTANT_COMPARATOR = new Comparator<XvmStructure>()
        {
        @Override
        public int compare(XvmStructure o1, XvmStructure o2)
            {
            return o1.getIdentityConstant().compareTo(o2.getIdentityConstant());
            }

        @Override
        public boolean equals(Object obj)
            {
            return this == obj;
            }
        };


    // ----- fields --------------------------------------------------------------------------------

    /**
     * For XVM structures that can be modified, this flag tracks whether or not
     * a modification has occurred.
     */
    private boolean m_fModified;
    }
