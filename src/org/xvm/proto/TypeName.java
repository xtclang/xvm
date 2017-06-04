package org.xvm.proto;

import org.xvm.asm.constants.TypeConstant;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * TypeName represents fully qualified generic names for the type system.
 *
 * @author gg 2017.02.27
 */
public interface TypeName
    {
    default boolean isResolved()
        {
        return true;
        }

    String getSimpleName();

    // load the dependant classes of the type for the specified template
    // (some elements may stay "formal" (unresolved)
    void loadDependencies(TypeCompositionTemplate template);

    // return a resolved Type based on the actual types
    Type resolveFormalTypes(TypeComposition clz);

    default TypeName replaceFormalTypes(List<String> listFormalNames, List<TypeName> listActualTypes)
        {
        return this;
        }

    // check if the this TypeName matches the specified TypeConstant
    default boolean isMatch(TypeConstant constType)
        {
        if (isResolved())
            {
            return getSimpleName().equals(constType.getValueString());
            }
        // assume a match for now
        return true;
        }

    // ----- helpers -----

    static String format(TypeName[] at)
        {
        return at.length == 0 ? "Void" : Utils.formatArray(at, "", "", ", ");
        }

    static String getFunctionSignature(String sName, String[] asArgType, String[] asRetType)
        {
        return format(parseNames(asRetType)) + ' ' + sName +
                "(" + format(parseNames(asArgType)) + ')';
        }

    static String getFunctionSignature(String sName, TypeName[] tnArg, TypeName[] tnRet)
        {
        return format(tnRet) + ' ' + sName + "(" + format(tnArg) + ')';
        }

    static String[] NON_GENERIC = new String[0];
    static String THIS_TYPE = "this.Type";

    // any depth and mis of formal/actual is allowed
    static TypeName parseName(String sName)
        {
        Stack<GenericTypeName> stackGeneric = new Stack<>();
        Stack<CompositeTypeName> stackUnion = new Stack<>();

        GenericTypeName genericCurrent = null;
        CompositeTypeName unionCurrent = null;

        int ofStart = 0;
        boolean fLiteral = false;

        for (int of = 0, c = sName.length(); of < c; of++)
            {
            switch (sName.charAt(of))
                {
                case '<':
                    if (fLiteral)
                        {
                        fLiteral = false;
                        String sPart = sName.substring(ofStart, of);

                        GenericTypeName ctn = new GenericTypeName(sPart);

                        if (unionCurrent != null)
                            {
                            unionCurrent.add(ctn);
                            }
                        stackUnion.push(unionCurrent);

                        if (genericCurrent != null)
                            {
                            stackGeneric.push(genericCurrent);
                            }

                        unionCurrent = null;
                        genericCurrent = ctn;
                        }
                    else
                        {
                        throw new RuntimeException("Unexpected '<'");
                        }
                    break;

                case ',':
                    if (fLiteral)
                        {
                        fLiteral = false;
                        String sPart = sName.substring(ofStart, of);
                        genericCurrent.add(new SimpleTypeName(sPart));
                        }

                    break;

                case '>':
                    if (fLiteral)
                        {
                        fLiteral = false;
                        String sPart = sName.substring(ofStart, of);
                        TypeName tn = new SimpleTypeName(sPart);
                        if (unionCurrent == null)
                            {
                            genericCurrent.add(tn);
                            }
                        else
                            {
                            unionCurrent.add(tn);
                            }
                        }
                    unionCurrent = stackUnion.pop();
                    if (!stackGeneric.isEmpty())
                        {
                        genericCurrent = stackGeneric.pop();
                        }
                    break;

                case '|':
                    if (fLiteral)
                        {
                        fLiteral = false;
                        String sPart = sName.substring(ofStart, of);

                        if (unionCurrent == null)
                            {
                            unionCurrent = new UnionTypeName();
                            if (genericCurrent != null)
                                {
                                genericCurrent.add(unionCurrent);
                                }
                            }
                        else
                            {
                            assert unionCurrent instanceof UnionTypeName;
                            }
                        unionCurrent.add(new SimpleTypeName(sPart));
                        }
                    break;

                case '+':
                    if (fLiteral)
                        {
                        fLiteral = false;
                        String sPart = sName.substring(ofStart, of);

                        if (unionCurrent == null)
                            {
                            unionCurrent = new IntersectionTypeName();
                            if (genericCurrent != null)
                                {
                                genericCurrent.add(unionCurrent);
                                }
                            }
                        else
                            {
                            assert unionCurrent instanceof IntersectionTypeName;
                            }
                        unionCurrent.add(new SimpleTypeName(sPart));
                        }
                    break;

                default:
                    if (!fLiteral)
                        {
                        ofStart = of;
                        fLiteral = true;
                        }
                    break;
                }
            }

        if (unionCurrent != null)
            {
            unionCurrent.add(new SimpleTypeName(sName.substring(ofStart)));
            return unionCurrent;
            }
        else if (genericCurrent == null)
            {
            return new SimpleTypeName(sName);
            }
        else
            {
            return genericCurrent;
            }
        }

    static TypeName[] parseNames(String[] asName)
        {
        TypeName[] aTypes;
        aTypes = new TypeName[asName.length];
        for (int i = 0; i < aTypes.length; i++)
            {
            aTypes[i] = TypeName.parseName(asName[i]);
            }
        return aTypes;
        }

    // --- implementing classes ----

    // e.g. "x:String" or "ElementType"
    class SimpleTypeName implements TypeName
        {
        String m_sName; // must be one of the actual types
        boolean m_fActual;
        TypeCompositionTemplate m_template; // cached template

        SimpleTypeName(String sName)
            {
            m_sName = sName;
            }

        @Override
        public String getSimpleName()
            {
            return m_sName;
            }

        @Override
        public void loadDependencies(TypeCompositionTemplate template)
            {
            if (m_sName.equals(THIS_TYPE))
                {
                m_sName = template.f_sName;
                m_template = template;
                m_fActual = true;
                }
            else if (!template.f_listFormalType.contains(m_sName))
                {
                m_sName = template.f_types.replaceAlias(m_sName);

                m_template = template.f_types.ensureTemplate(m_sName);
                m_fActual = true;
                }
            }

        @Override
        public Type resolveFormalTypes(TypeComposition clz)
            {
            if (isResolved())
                {
                return m_template.f_clazzCanonical.ensurePublicType();
                }

            return clz.resolveFormalType(m_sName);
            }

        @Override
        public boolean isResolved()
            {
            return m_fActual;
            }

        @Override
        public TypeName replaceFormalTypes(List<String> listFormalNames, List<TypeName> listActualTypes)
            {
            int index = listFormalNames.indexOf(m_sName);

            if (index >= 0 && index < listActualTypes.size())
                {
                SimpleTypeName tnReplace = (SimpleTypeName) listActualTypes.get(index);
                if (!tnReplace.m_sName.equals(m_sName))
                    {
                    return tnReplace;
                    }
                }
            return this;
            }

        @Override
        public String toString()
            {
            return m_sName;
            }
        }

    abstract class CompositeTypeName implements TypeName
        {
        List<TypeName> m_listTypeName = new LinkedList<>();

        public void add(TypeName typeName)
            {
            m_listTypeName.add(typeName);
            }

        @Override
        public String getSimpleName()
            {
            return toString();
            }

        @Override
        public void loadDependencies(TypeCompositionTemplate template)
            {
            for (TypeName t : m_listTypeName)
                {
                t.loadDependencies(template);
                }
            }

        @Override
        public boolean isResolved()
            {
            for (TypeName t : m_listTypeName)
                {
                if (!t.isResolved())
                    {
                    return false;
                    }
                }
            return true;
            }
        }

    // e.g. "x:collections.Map<x:Int, x:List<ValueType>>"
    class GenericTypeName extends CompositeTypeName
        {
        String m_sActualName; // globally known type composition name (e.g. x:Boolean or x:annotation.AtomicRef)
        TypeCompositionTemplate m_template; // cached template

        public GenericTypeName(String sName)
            {
            m_sActualName = sName;
            }

        @Override
        public void loadDependencies(TypeCompositionTemplate template)
            {
            m_sActualName = template.f_types.replaceAlias(m_sActualName);

            m_template = template.f_types.ensureTemplate(m_sActualName);

            super.loadDependencies(template);
            }

        @Override
        public Type resolveFormalTypes(TypeComposition clz)
            {
            Type[] aType = new Type[m_listTypeName.size()];
            int i = 0;
            for (TypeName tn : m_listTypeName)
                {
                aType[i++] = tn.resolveFormalTypes(clz);
                }
            return m_template.resolve(aType).ensurePublicType();
            }

        @Override
        public String getSimpleName()
            {
            return m_sActualName;
            }

        @Override
        public TypeName replaceFormalTypes(List<String> listFormalNames, List<TypeName> listActualTypes)
            {
            TypeName[] atnReplace = new TypeName[m_listTypeName.size()];
            int i = 0;
            boolean fChanged = false;
            for (TypeName tn : m_listTypeName)
                {
                TypeName tnReplace = tn.replaceFormalTypes(listFormalNames, listActualTypes);
                fChanged |= tnReplace != tn;
                atnReplace[i++] = tnReplace;
                }

            if (fChanged)
                {
                GenericTypeName tnReplace = new GenericTypeName(m_sActualName);
                tnReplace.m_listTypeName = Arrays.asList(atnReplace);
                return tnReplace;
                }
            return this;
            }

        @Override
        public String toString()
            {
            return m_sActualName + Utils.formatArray(m_listTypeName.toArray(), "<", ">", ", ");
            }
        }

    // e.g. (x:Class | x:Method | x:Function | x:Property)
    public class UnionTypeName extends CompositeTypeName
        {
        UnionTypeName()
            {
            }

        @Override
        public Type resolveFormalTypes(TypeComposition clz)
            {
            throw new UnsupportedOperationException("TODO: create a union clazz");
            }

        @Override
        public boolean isMatch(TypeConstant constType)
            {
            // TODO: wait for TypeConstant support
            return true;
            }

        @Override
        public String toString()
            {
            return Utils.formatArray(m_listTypeName.toArray(), "", "", " | ");
            }
        }

    // e.g. (x:Class | x:Method | x:Function | x:Property)
    public class IntersectionTypeName extends CompositeTypeName
        {
        IntersectionTypeName()
            {
            }

        @Override
        public Type resolveFormalTypes(TypeComposition clz)
            {
            throw new UnsupportedOperationException("TODO: create an intersection clazz");
            }

        @Override
        public boolean isMatch(TypeConstant constType)
            {
            // TODO: wait for TypeConstant support
            return true;
            }

        @Override
        public String toString()
            {
            return Utils.formatArray(m_listTypeName.toArray(), "", "", " + ");
            }
        }

    // unit test
    static void main(String[] args)
        {
        System.out.println(parseName("A"));
        System.out.println(parseName("A<B>"));
        System.out.println(parseName("A<B,C>"));
        System.out.println(parseName("A|B"));
        System.out.println(parseName("A<B|C>"));
        System.out.println(parseName("A<B,C|D>"));
        System.out.println(parseName("A<B,C|D|E>"));
        System.out.println(parseName("A<B|C<D|E>>"));
        System.out.println(parseName("A+B"));
        System.out.println(parseName("A<B+C>"));
        System.out.println(parseName("A<B,C+D>"));
        System.out.println(parseName("A<B,C+D+E>"));
        System.out.println(parseName("A<B|C<D+E>>"));
        }
    }
