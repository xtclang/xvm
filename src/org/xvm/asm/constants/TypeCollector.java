package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Op;


/**
 * A TypeCollector is used to collect a number of types, such as would occur from return statements
 * within a lambda, in order to infer a type from that collection of types.
 */
public class TypeCollector
    {
    /**
     * Construct a TypeCollector.
     *
     * @param pool  the ConstantPool to use
     */
    public TypeCollector(ConstantPool pool)
        {
        f_pool = pool;
        }

    /**
     * Add a void (an absence of any types) to the collection.
     */
    public void addVoid()
        {
        add((TypeConstant) null);
        }

    /**
     * Add a specified type to the collection.
     *
     * @param type  a TypeConstant to add to the collection, or null to specify that a type could
     *              not be determined
     */
    public void add(TypeConstant type)
        {
        if (isMulti())
            {
            add(new TypeConstant[] {type});
            }
        else
            {
            ensureSingle().add(type);
            }
        }

    /**
     * Add a specified array of types (indicating a multi-value) to the collection.
     *
     * @param aTypes  an array of zero or more TypeConstants to add (as a unit) to the collection,
     *                or null to specify that a type could not be determined
     */
    public void add(TypeConstant[] aTypes)
        {
        if (!isMulti() && (aTypes == null || aTypes.length == 1))
            {
            add(aTypes == null ? null : aTypes[0]);
            }
        else
            {
            ensureMulti().add(aTypes);
            }
        }

    /**
     * @return the size of the type collection, which is the number of times that add() has been
     *         called
     */
    public int size()
        {
        ArrayList<TypeConstant> listSingle = m_listSingle;
        if (listSingle != null)
            {
            return listSingle.size();
            }

        ArrayList<TypeConstant[]> listMulti = m_listMulti;
        if (listMulti != null)
            {
            return listMulti.size();
            }

        return 0;
        }

    /**
     * @return true if any of the calls to add() specified a null, which indicates an unknown type
     */
    public boolean containsUnknown()
        {
        ArrayList list = m_listMulti;
        if (list == null)
            {
            list = m_listSingle;
            }
        if (list != null)
            {
            for (Object o : list)
                {
                if (o == null)
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    /**
     * @return true iff all of the collected types are of the same arity, and none is unknown
     */
    public boolean isUniform()
        {
        ArrayList<TypeConstant> listSingle = m_listSingle;
        if (listSingle != null)
            {
            // this is just the opposite of constainsUnknown(), since we know that otherwise the
            // single-format list is uniform
            for (TypeConstant type : listSingle)
                {
                if (type == null)
                    {
                    return false;
                    }
                }
            return true;
            }

        ArrayList<TypeConstant[]> listMulti = m_listMulti;
        if (listMulti != null)
            {
            int cTypes   = -1;
            for (TypeConstant[] aTypes : listMulti)
                {
                if (aTypes == null)
                    {
                    return false;
                    }

                if (cTypes != aTypes.length)
                    {
                    if (cTypes < 0)
                        {
                        // first time in the loop
                        cTypes = aTypes.length;
                        }
                    else
                        {
                        // not uniform
                        return false;
                        }
                    }
                }
            return true;
            }

        return true;
        }

    /**
     * @return true iff all of the collected type information thus far has had exactly one type (or
     *         was unknown)
     */
    public boolean isSingle()
        {
        return m_listMulti == null;
        }

    /**
     * @return the read-only list of collected type information in single format
     */
    public List<TypeConstant> getSingle()
        {
        ArrayList<TypeConstant> listSingle = m_listSingle;
        if (listSingle != null)
            {
            return listSingle;
            }

        assert m_listMulti == null;
        return Collections.EMPTY_LIST;
        }

    /**
     * @return the read/write list of collected types in a single-format
     */
    private ArrayList<TypeConstant> ensureSingle()
        {
        // assume that a call to ensure is a mutating call; clear the cached conditional calculation
        m_FConditional = false;

        ArrayList<TypeConstant> listSingle = m_listSingle;
        if (listSingle == null)
            {
            assert m_listMulti == null;
            m_listSingle = listSingle = new ArrayList<>();
            }

        return listSingle;
        }

    /**
     * @return true iff any of the collected type information has had more than one type
     */
    public boolean isMulti()
        {
        return m_listMulti != null;
        }

    /**
     * @return the read-only list of collected type information in multi-format
     */
    public List<TypeConstant[]> getMulti()
        {
        ArrayList<TypeConstant[]> list = m_listMulti;
        if (list != null)
            {
            return list;
            }

        return m_listSingle == null
                ? Collections.EMPTY_LIST
                : ensureMulti();
        }

    /**
     * @return the list of collected types in a multi-format
     */
    private ArrayList<TypeConstant[]> ensureMulti()
        {
        // assume that a call to ensure is a mutating call; clear the cached conditional calculation
        m_FConditional = false;

        ArrayList<TypeConstant[]> listMulti = m_listMulti;
        if (listMulti != null)
            {
            return listMulti;
            }

        m_listMulti = listMulti = new ArrayList<>();

        // convert any existing data in the "single" format to the "multi" format
        ArrayList<TypeConstant> listSingle = m_listSingle;
        if (listSingle != null)
            {
            for (TypeConstant type : listSingle)
                {
                listMulti.add(type == null
                        ? null
                        : new TypeConstant[] {type});
                }
            m_listSingle = null;
            }

        return listMulti;
        }

    /**
     * Determine if the types collected by the TypeConstant indicates a particular common type.
     *
     * @param typeRequired (optional) required type
     *
     * @return the inferred common type (including potentially requiring conversion), or null if no
     *         common type can be determined
     */
    public TypeConstant inferSingle(TypeConstant typeRequired)
        {
        assert !isMulti();

        // single type is never an @Conditional
        m_FConditional = false;

        List<TypeConstant> listTypes = getSingle();
        int cTypes = listTypes.size();
        if (cTypes == 0 || listTypes.stream().anyMatch(e -> e == null))
            {
            return null;
            }

        TypeConstant typeCommon = inferFrom(listTypes.toArray(new TypeConstant[cTypes]), f_pool);

        return Op.selectCommonType(typeCommon, typeRequired, ErrorListener.BLACKHOLE);
        }

    /**
     * Determine if the types collected by the TypeConstant indicates a particular common type.
     * After calling this method, it is also possible to determine if the resulting type is
     * <i>conditional</i>, by calling {@link #isConditional()}.
     *
     * @param atypeRequired (optional) an array of required types
     *
     * @return the inferred common type (including potentially requiring conversion), or null if no
     *         common type can be determined
     */
    public TypeConstant[] inferMulti(TypeConstant[] atypeRequired)
        {
        if (!isMulti())
            {
            TypeConstant type = inferSingle(
                    atypeRequired == null || atypeRequired.length == 0 ? null : atypeRequired[0]);
            return type == null
                    ? null
                    : new TypeConstant[] {type};
            }

        // assume that it's not a @Conditional until we prove otherwise
        m_FConditional = false;

        List<TypeConstant[]> listTypes = getMulti();
        int                  cElements = listTypes.size();
        if (cElements == 0)
            {
            return null;
            }

        // do a quick scan to determine the "shape" of the result
        int          cHeight      = 0;
        int          cWidth       = -1;
        boolean      fConditional = true;
        for (TypeConstant[] aTypes : m_listMulti)
            {
            if (aTypes == null)
                {
                return null;
                }

            int cTypes = aTypes.length;
            if (cTypes == 1)
                {
                // the only possibility for a one-column multi is a "conditional" type, so if that
                // is not the case, then we're done
                if (!fConditional || !aTypes[0].equals(f_pool.typeFalse()))
                    {
                    return null;
                    }
                }
            else
                {
                if (cWidth < 0)
                    {
                    if (cTypes == 0)
                        {
                        fConditional = false;
                        }

                    cWidth = cTypes;
                    }
                else if (cTypes != cWidth)
                    {
                    // illegal variation in the number of types
                    return null;
                    }

                ++cHeight;
                }
            }

        // a void return is easy to recognize
        if (cWidth == 0)
            {
            return cHeight == cElements
                    ? TypeConstant.NO_TYPES
                    : null;
            }

        // at this point, we know the width and height of the result types, and whether a
        // conditional type is still a possibility
        int            cRequired = atypeRequired == null ? 0 : atypeRequired.length;
        TypeConstant[] aResult   = new TypeConstant[cWidth];
        if (cHeight < cElements)
            {
            assert fConditional;

            // test the type of the first column; it must be boolean (otherwise the result cannot
            // be conditional)
            TypeConstant[] aColType = new TypeConstant[cElements];
            for (int iRow = 0; iRow < cElements; ++iRow)
                {
                aColType[iRow] = listTypes.get(iRow)[0];
                }

            // conditional results MUST be a boolean for the first value
            TypeConstant typeResult = inferFrom(aColType, f_pool);
            if (typeResult == null || !typeResult.equals(f_pool.typeBoolean()))
                {
                return null;
                }
            aResult[0] = typeResult;

            // determine the types of each of the other columns
            aColType = new TypeConstant[cHeight];
            for (int iCol = 1; iCol < cWidth; ++iCol)
                {
                for (int iElement = 0, iRow = 0; iElement < cElements; ++iElement)
                    {
                    TypeConstant[] aRowType = listTypes.get(iElement);
                    if (aRowType.length != 1)
                        {
                        aColType[iRow++] = aRowType[iCol];
                        }
                    }

                typeResult = inferFrom(aColType, f_pool);
                if (typeResult == null)
                    {
                    return null;
                    }
                TypeConstant typeRequired = iCol < cRequired ? atypeRequired[iCol] : null;
                aResult[iCol] = Op.selectCommonType(typeResult, typeRequired, ErrorListener.BLACKHOLE);
                }
            }
        else
            {
            fConditional = false;

            // determine the types for each column
            TypeConstant[] aColType = new TypeConstant[cHeight];
            for (int iCol = 0; iCol < cWidth; ++iCol)
                {
                for (int iRow = 0; iRow < cHeight; ++iRow)
                    {
                    aColType[iRow++] = listTypes.get(iRow)[iCol];
                    }
                TypeConstant typeResult = inferFrom(aColType, f_pool);
                if (typeResult == null)
                    {
                    return null;
                    }
                aResult[iCol] = typeResult;

                TypeConstant typeRequired = iCol < cRequired ? atypeRequired[iCol] : null;
                aResult[iCol] = Op.selectCommonType(typeResult, typeRequired, ErrorListener.BLACKHOLE);
                }
            }

        m_FConditional = fConditional;
        return aResult;
        }

    /**
     * @return true iff the resulting type is determinable, has more than one type, the first is
     *         a boolean, and the additional types are not guaranteed to be present when the first
     *         is false
     */
    public boolean isConditional()
        {
        if (m_FConditional == null)
            {
            inferMulti(null);
            assert m_FConditional != null;
            }

        return m_FConditional;
        }

    /**
     * Determine if the passed array of types indicates a particular common type.
     *
     * @param aTypes  an array of types, which can be null and which can contain nulls
     * @param pool    the constant pool to use
     *
     * @return the inferred common type (including potentially requiring conversion), or null if no
     *         common type can be determined
     */
    public static TypeConstant inferFrom(TypeConstant[] aTypes, ConstantPool pool)
        {
        if (aTypes == null)
            {
            return null;
            }

        int cTypes = aTypes.length;
        if (cTypes == 0)
            {
            return null;
            }

        TypeConstant typeCommon = aTypes[0];
        if (typeCommon == null)
            {
            return null;
            }

        boolean fConvApplied = false;
        boolean fImmutable   = typeCommon.isImmutable();
        for (int i = 1; i < cTypes; ++i)
            {
            TypeConstant type = aTypes[i];
            if (type == null)
                {
                return null;
                }

            if (!type.isA(typeCommon))
                {
                if (typeCommon.isA(type))
                    {
                    typeCommon = type;
                    fImmutable = fImmutable && type.isImmutable();
                    continue;
                    }

                if (type.getConverterTo(typeCommon) != null)
                    {
                    fConvApplied = true;
                    continue;
                    }

                if (!fConvApplied)
                    {
                    MethodConstant idConv = typeCommon.getConverterTo(type);
                    if (idConv != null)
                        {
                        fConvApplied = true;
                        typeCommon   = type;
                        fImmutable   = fImmutable && type.isImmutable();
                        continue;
                        }
                    }

                typeCommon = Op.selectCommonType(type, typeCommon, ErrorListener.BLACKHOLE);
                if (typeCommon == null)
                    {
                    // no obvious common type
                    return null;
                    }
                }
            }

        return fImmutable ? typeCommon.ensureImmutable(pool) : typeCommon;
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The constant pool used by this TypeCollector.
     */
    private final ConstantPool f_pool;

    /**
     * A list of single types, with null representing unknown.
     */
    private ArrayList<TypeConstant> m_listSingle;

    /**
     * A list of type arrays, with an empty array representing void, and null representing unknown.
     */
    private ArrayList<TypeConstant[]> m_listMulti;

    /**
     * Holds a value indicating whether the common type is a "conditional" type.
     * <p/>
     * This is a cached result of analyzing the collector for a common type.
     */
    private transient Boolean m_FConditional;
    }
