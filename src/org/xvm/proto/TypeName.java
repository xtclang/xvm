package org.xvm.proto;

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

    // resolve the elements of the type for the specified template
    // (some elements may stay "formal" (unresolved)
    void resolve(TypeCompositionTemplate template);

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


    String[] NON_GENERIC = new String[0];

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
        public void resolve(TypeCompositionTemplate template)
            {
            if (m_sName.equals("this.Type"))
                {
                m_fActual = true;
                }
            else if (!Arrays.asList(template.f_asFormalType).contains(m_sName))
                {
                m_sName = template.f_types.replaceAlias(m_sName);

                template.f_types.ensureTemplate(m_sName);
                m_fActual = true;
                }
            }

        @Override
        public boolean isResolved()
            {
            return m_fActual;
            }

        @Override
        public String toString()
            {
            return m_sName;
            }
        }

    abstract class CompositeTypeName implements TypeName
        {
        List<TypeName> m_aTypeName = new LinkedList<>();

        public void add(TypeName typeName)
            {
            m_aTypeName.add(typeName);
            }

        @Override
        public String getSimpleName()
            {
            return toString();
            }

        @Override
        public void resolve(TypeCompositionTemplate template)
            {
            for (TypeName t : m_aTypeName)
                {
                t.resolve(template);
                }
            }

        @Override
        public boolean isResolved()
            {
            for (TypeName t : m_aTypeName)
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

        public GenericTypeName(String sName)
            {
            m_sActualName = sName;
            }

        @Override
        public void resolve(TypeCompositionTemplate template)
            {
            m_sActualName = template.f_types.replaceAlias(m_sActualName);

            template.f_types.ensureTemplate(m_sActualName);

            super.resolve(template);
            }

        @Override
        public String getSimpleName()
            {
            return m_sActualName;
            }

        @Override
        public String toString()
            {
            return m_sActualName + Utils.formatArray(m_aTypeName.toArray(), "<", ">", ", ");
            }
        }

    // e.g. (x:Class | x:Method | x:Function | x:Property)
    public class UnionTypeName extends CompositeTypeName
        {
        UnionTypeName()
            {
            }

        @Override
        public String toString()
            {
            return Utils.formatArray(m_aTypeName.toArray(), "", "", " | ");
            }
        }

    // e.g. (x:Class | x:Method | x:Function | x:Property)
    public class IntersectionTypeName extends CompositeTypeName
        {
        IntersectionTypeName()
            {
            }

        @Override
        public String toString()
            {
            return Utils.formatArray(m_aTypeName.toArray(), "", "", " + ");
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
