package org.xvm.proto;

import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.template.collections.xArray;
import org.xvm.proto.template.types.xMethod;
import org.xvm.proto.template.types.xMethod.MethodHandle;
import org.xvm.proto.template.xType;
import org.xvm.proto.template.xType.TypeHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Type is simply a collection of properties and methods.
 *
 * @author gg 2017.02.16
 */
public class Type
    {
    final public TypeComposition f_clazz; // optional
    final public Constants.Access f_access; // only if f_clazz != null

    private TypeHandle m_hType;
    private int m_nId;

    private xArray.GenericArrayHandle m_methods = null;
    private boolean m_fConstant;

    private Map<Integer, Relation> m_relations = new HashMap<>(); // cached type relations

    public Type(TypeComposition clazz, Constants.Access access)
        {
        f_clazz = clazz;
        f_access = access;
        }

    public int getId()
        {
        return m_nId;
        }

    public void setId(int id)
        {
        assert m_nId == 0;
        m_nId = id;
        }

    public TypeHandle getHandle()
        {
        TypeHandle hType = m_hType;
        if (hType == null)
            {
            hType = m_hType = xType.makeHandle(this);
            }
        return hType;
        }

    public xArray.GenericArrayHandle getAllMethods()
        {
        xArray.GenericArrayHandle methods = m_methods;
        if (methods == null)
            {
            Set<MethodConstant> setMethods = new HashSet<>(); // replace w/ SignatureConstant
            List<MethodHandle> listHandles = new ArrayList<>();
            int nAccess = f_access.ordinal(); // 1=public, 2=protected, 3=private

            for (ClassTemplate template : f_clazz.getCallChain())
                {
                for (Component cc : template.f_struct.children())
                    {
                    if (cc instanceof MultiMethodStructure)
                        {
                        MultiMethodStructure mm = (MultiMethodStructure) cc;
                        for (Component cmm : mm.children())
                            {
                            if (cmm instanceof MethodStructure)
                                {
                                MethodStructure method = (MethodStructure) cmm;
                                if (method.getAccess().ordinal() <= nAccess &&
                                    setMethods.add(method.getIdentityConstant()))
                                    {
                                    listHandles.add(xMethod.makeHandle(method, f_clazz, this));
                                    }
                                }
                            }
                        }
                    else if (cc instanceof PropertyStructure)
                        {
                        PropertyStructure property = (PropertyStructure) cc;
                        MethodStructure getter = Adapter.getGetter(property);
                        MethodStructure setter = Adapter.getSetter(property);

                        }
                    }

                // private methods are only visible for the top class in the chain
                nAccess = Math.min(2, nAccess);
                }
            methods = m_methods = xArray.makeHandle(xMethod.TYPE,
                    listHandles.toArray(new ObjectHandle[listHandles.size()]));
            }
        return methods;
        }

    /**
     * @return  Relation between this and that type
     */
    public Relation calculateRelation(Type that)
        {
        Relation relation = Relation.INCOMPATIBLE;

        // TODO: compare

        if (relation != Relation.EQUAL)
            {
            // cache the results
            m_relations.put(that.getId(), Relation.SUB);
            }

        return relation;
        }

    // SUPER == assignable from; SUB == assignable to
    public enum Relation {EQUAL, SUPER, SUB, INCOMPATIBLE};

    @Override
    public int hashCode()
        {
        assert m_nId != 0;

        return m_nId;
        }

    @Override
    public boolean equals(Object obj)
        {
        assert m_nId != 0;

        Type that = (Type) obj;
        return that.m_nId == this.m_nId;
        }

    @Override
    public String toString()
        {
        return f_clazz == null ? "<no class>" : f_clazz.toString();
        }

    // ----- debugging support ------

    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Id=").append(m_nId);
        if (f_clazz != null)
            {
            sb.append(" Name=").append(f_clazz);
            }
        return sb.toString();
        }
    }
