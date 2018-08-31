package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * TODO
 */
public class TypeCollector
    {
    public void addVoid()
        {
        add((TypeConstant) null);
        }

    public void add(TypeConstant type)
        {
        if (isMulti())
            {
            add(new TypeConstant[] {type});
            }
        else
            {
            // TODO
            }
        }

    public void add(TypeConstant[] aTypes)
        {
        int cTypes = aTypes == null ? 0 : aTypes.length;
        if (cTypes <= 1 && !isMulti())
            {

            }
        }

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
     * A list of single types, with null representing unknown.
     */
    private ArrayList<TypeConstant> m_listSingle;

    /**
     * A list of type arrays, with an empty array representing void, and null representing unknown.
     */
    private ArrayList<TypeConstant[]> m_listMulti;
    }
