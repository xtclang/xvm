package org.xvm.runtime;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xType;
import org.xvm.runtime.template.xType.TypeHandle;
import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.types.xMethod;
import org.xvm.runtime.template.types.xMethod.MethodHandle;


/**
 * Type is simply a collection of properties and methods.
 *
 * @author gg 2017.02.16
 */
public class Type
    {
    final public TypeComposition f_clazz;
    final protected Constants.Access f_access;

    private TypeHandle m_hType;
    private int m_nId;

    private MethodStructure[] m_aMethods = null;
    private xArray.GenericArrayHandle m_hMethods = null;
    private boolean m_fImmutable;

    // SUPER == this Type is assignable from; SUB == this type is assignable to
    // *_WEAK == types are assignable, but there are methods that may be incompatible

    public enum Relation {EQUAL, SUB, SUB_WEAK, SUPER, SUPER_WEAK, INCOMPATIBLE};
    private Map<Integer, Relation> m_relations = new HashMap<>(); // cached type relations

    private Map<String, CanonicalMultiMethod> m_mapMultiMethods;
    private Map<String, CanonicalProperty> m_mapProperties;

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

    public boolean isImmutable()
        {
        return m_fImmutable;
        }

    public void markImmutable()
        {
        m_fImmutable = true;
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

    private void collectAll(TypeSet types)
        {
        Map<String, Type> mapActual = f_clazz.f_mapGenericActual;

        Map<String, CanonicalMultiMethod> mapMultiMethods = m_mapMultiMethods = new HashMap<>();
        Map<String, CanonicalProperty> mapProperties = m_mapProperties = new HashMap<>();

        Set<SignatureConstant> setSignatures = new HashSet<>();

        int nAccess = f_access.ordinal(); // 1=public, 2=protected, 3=private

        for (ClassTemplate template : f_clazz.getCallChain())
            {
            for (Component child : template.f_struct.children())
                {
                if (child instanceof MultiMethodStructure)
                    {
                    for (MethodStructure method : ((MultiMethodStructure) child).methods())
                        {
                        if (method.getAccess().ordinal() <= nAccess &&
                            setSignatures.add(method.getIdentityConstant().getSignature()))
                            {
                            Type[] atParam = new Type[method.getParamCount()];
                            for (int i = 0, c = atParam.length; i < c; i++)
                                {
                                TypeConstant constType = method.getParam(i).getType();
                                atParam[i] = types.resolveType(constType, mapActual);
                                }

                            Type[] atReturn = new Type[method.getReturnCount()];
                            for (int i = 0, c = atReturn.length; i < c; i++)
                                {
                                TypeConstant constType = method.getReturn(i).getType();
                                atReturn[i] = types.resolveType(constType, mapActual);
                                }
                            CanonicalMultiMethod cmm = mapMultiMethods.computeIfAbsent(method.getName(),
                                CanonicalMultiMethod::new);
                            cmm.add(new CanonicalMethod(method, atParam, atReturn));
                            }
                        }
                    }
                else if (child instanceof PropertyStructure)
                    {
                    PropertyStructure property = (PropertyStructure) child;
                    if ((Adapter.getGetter(property).getAccess().ordinal() <= nAccess ||
                        Adapter.getSetter(property).getAccess().ordinal() <= nAccess) &&
                        !mapProperties.containsKey(property.getName()))
                        {
                        Type type = types.resolveType(property.getType(), mapActual);
                        mapProperties.put(property.getName(), new CanonicalProperty(property, type));
                        }
                    }
                }

            // private methods are only visible for the top class in the chain
            nAccess = Math.min(2, nAccess);
            }
        }

    public xArray.GenericArrayHandle getAllMethods()
        {
        xArray.GenericArrayHandle hMethods = m_hMethods;
        if (hMethods == null)
            {
            MethodStructure[] aMethods = m_aMethods;
            MethodHandle[] ahMethods = new MethodHandle[aMethods.length];
            for (int i = 0, c = aMethods.length; i < c; i++)
                {
                ahMethods[i] = xMethod.makeHandle(aMethods[i], f_clazz, this);
                }
            hMethods = m_hMethods = xArray.makeHandle(xMethod.TYPE, ahMethods);
            }
        return hMethods;
        }

    /**
     * Determine if values of the specified type will be assignable to values of this type.
     *
     * @param that   the type to match
     *
     * See Type.x # isA()
     */
    public boolean isA(Type that)
        {
        Relation relation = calculateRelation(that);

        // Relation.EQUAL || Relation.SUB || Relation.SUB_WEAK;
        return relation.compareTo(Relation.SUB_WEAK) <= 0;
        }

    public Relation calculateRelation(Type that)
        {
        // quick check for trivial relationships that don't have to be cached

        if (this.equals(that))
            {
            return Relation.EQUAL;
            }

        if (that.isImmutable() && !this.isImmutable())
            {
            return Relation.INCOMPATIBLE;
            }

        if (that == xObject.TYPE)
            {
            return Relation.SUB;
            }

        if (this == xObject.TYPE)
            {
            return Relation.SUPER;
            }

        Relation relation = m_relations.get(that.getId());
        if (relation != null)
            {
            return relation;
            }

        TypeComposition clzThis = f_clazz;
        TypeComposition clzThat = that.f_clazz;

        boolean fIncompatible = false;
        boolean fCheckMethods = false;

        if (clzThat.f_template.isInterface())
            {
            // both are interfaces; need to check the methods
            fCheckMethods = true;
            }
        else if (clzThis.f_template.isInterface())
            {
            // an interface cannot be assignable to a non-interface
            fIncompatible = true;
            }
        else
            {
            if (this.f_access.compareTo(that.f_access) < 0)
                {
                // cannot assign a public "this" to protected "that"
                fIncompatible = true;
                }
            else if (clzThis.getCallChain().contains(clzThat.f_template))
                {
                // "that" template is a part of "this" class composition;
                // make sure the formal parameters are compatible
                for (String sName : clzThat.f_mapGenericActual.keySet())
                    {
                    if (that.producesFormalType(sName))
                        {
                        // "that" is a producer of sName, therefore for "this" class to be assignable
                        // to "that" the actual parameter type of "this" should be assignable to the
                        // actual parameter type of "that"
                        if (!clzThis.getActualParamType(sName).isA(clzThat.getActualParamType(sName)))
                            {
                            fIncompatible = true;
                            break;
                            }
                        }
                    else if (that.consumesFormalType(sName))
                        {
                        // "that" is not a producer of sName, but is a consumer, therefore
                        // for "this" class to be assignable to it the actual parameter type of
                        // "that" should be assignable to the actual parameter type of "this"
                        if (!clzThat.getActualParamType(sName).isA(clzThis.getActualParamType(sName)))
                            {
                            fIncompatible = true;
                            break;
                            }
                        }
                    }
                }
            else
                {
                fIncompatible = true;
                }
            }

        if (fIncompatible)
            {
            this.m_relations.put(that.getId(), Relation.INCOMPATIBLE);
            that.m_relations.put(this.getId(), Relation.INCOMPATIBLE);
            return Relation.INCOMPATIBLE;
            }

        if (fCheckMethods)
            {
            // TODO: compare the methods
            }

        this.m_relations.put(that.getId(), Relation.SUB);
        that.m_relations.put(this.getId(), Relation.SUPER);
        return Relation.SUB;
        }

    /**
     * Determine if this type consumes a formal type with the specified name.
     */
    protected boolean consumesFormalType(String sName)
        {
        return f_clazz.consumesFormalType(sName, f_access);
        }

    /**
     * Determine if this type produces a formal type with the specified name.
     */
    protected boolean producesFormalType(String sName)
        {
        return f_clazz.producesFormalType(sName, f_access);
        }

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
        return f_clazz.toString();
        }


    // ----- debugging support ------

    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Id=").append(m_nId);
        sb.append(" Name=").append(f_clazz);
        return sb.toString();
        }

    protected static class CanonicalMultiMethod
        {
        public CanonicalMultiMethod(String sName)
            {
            f_sName = sName;
            }

        public void add(CanonicalMethod method)
            {
            m_listMethods.add(method);
            }
        protected final String f_sName;
        protected List<CanonicalMethod> m_listMethods = new ArrayList<>(1);
        }

    protected static class CanonicalMethod
        {
        public CanonicalMethod(MethodStructure method, Type[] atParam, Type[] atReturn)
            {
            f_method = method;
            f_atParam = atParam;
            f_atReturn = atReturn;
            }

        protected final MethodStructure f_method;
        protected final Type[] f_atParam;
        protected final Type[] f_atReturn;
        }

    protected static class CanonicalProperty
        {
        public CanonicalProperty(PropertyStructure property, Type type)
            {
            f_property = property;
            f_type = type;
            }

        protected final PropertyStructure f_property;
        protected final Type f_type;
        }
    }
