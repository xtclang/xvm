package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.template.xFunction;
import org.xvm.util.Handy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


/**
 * A temporary intermediary between the RT and the ConstantPool
 *
 * @author gg 2017.03.08
 */
public class ConstantPoolAdapter
    {
    public ConstantPool m_pool;

    // the template composition: name -> the corresponding ClassConstant id
    private Map<String, Integer> m_mapClasses = new HashMap<>();

    // the property template: fully qualified name (x:collections.Map#size) -> the corresponding PropertyConstant id
    private Map<String, Integer> m_mapProperties = new HashMap<>();

    // the method template fully qualified name (x:collections.Map#get[KeyType]) -> the corresponding MethodConstant id
    private Map<String, Integer> m_mapMethods = new TreeMap<>();

    public ConstantPoolAdapter(ConstantPool pool)
        {
        m_pool = pool;
        }

    public int getClassTypeConstId(String sName)
        {
        if (m_mapClasses.containsKey(sName))
            {
            return m_mapClasses.get(sName);
            }

        int ofTypeParam = sName.indexOf('<');
        if (ofTypeParam >= 0)
            {
            String sParam = sName.substring(ofTypeParam + 1, sName.length() - 1);
            String sSimpleName = sName.substring(0, ofTypeParam);

            ClassTypeConstant constType = getClassTypeConstant(sSimpleName);
            if (constType != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                int cTypes = asType.length;
                TypeConstant[] aType = new TypeConstant[cTypes];
                for (int i = 0; i < cTypes; i++)
                    {
                    String sType = asType[i].trim();
                    aType[i] = getClassTypeConstant(getClassTypeConstId(sType));
                    }

                int nTypeId = m_pool.ensureClassTypeConstant(
                        constType.getClassConstant(), null, aType).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }

        throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
        }

    protected ClassTypeConstant getClassTypeConstant(String sName)
        {
        Integer IClass = m_mapClasses.get(sName);
        return IClass == null ? null : getClassTypeConstant(IClass.intValue());
        }

    public ClassTypeConstant getClassTypeConstant(int nConstId)
        {
        return (ClassTypeConstant) m_pool.getConstant(nConstId);
        }

    public int getPropertyConstId(String sClassName, String sPropName)
        {
        try
            {
            return m_mapProperties.get(sClassName + '#' + sPropName);
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Property is not defined: " + sClassName + '#' + sPropName);
            }
        }

    public PropertyConstant getPropertyConstant(int nConstId)
        {
        return (PropertyConstant) m_pool.getConstant(nConstId);
        }

    public int getMethodConstId(String sClassName, String sMethName)
        {
        try
            {
            return m_mapMethods.get(sClassName + '#' + sMethName);
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Method is not defined: " + sClassName + '#' + sMethName);
            }
        }

    public MethodConstant getMethodConstant(int nConstId)
        {
        return (MethodConstant) m_pool.getConstant(nConstId);
        }

    // FOR SIMULATION ONLY
    public ModuleConstant ensureModuleConstant(String sModule)
        {
        return m_pool.ensureModuleConstant(sModule);
        }

    public int ensureValueConstantId(Object oValue)
        {
        return ensureValueConstant(oValue).getPosition();
        }

    protected Constant ensureValueConstant(Object oValue)
        {
        if (oValue instanceof Integer || oValue instanceof Long)
            {
            return m_pool.ensureIntConstant(((Number) oValue).longValue());
            }

        if (oValue instanceof String)
            {
            return m_pool.ensureCharStringConstant((String) oValue);
            }

        if (oValue instanceof Character)
            {
            return m_pool.ensureCharConstant(((Character) oValue).charValue());
            }

        if (oValue instanceof Boolean)
            {
            return getClassTypeConstant(
                    ((Boolean) oValue).booleanValue() ?
                            "x:Boolean$True" : "x:Boolean$False");
            }

        if (oValue instanceof Object[])
            {
            Object[] ao = (Object[]) oValue;
            int c = ao.length;
            Constant[] aconst = new Constant[c];
            for (int i = 0; i < c; i++)
                {
                aconst[i] = ensureValueConstant(ao[i]);
                }
            return m_pool.ensureTupleConstant(aconst);
            }

        throw new IllegalArgumentException();
        }

    // static helpers

    public static String getClassName(ClassConstant constClass)
        {
        StringBuilder sb = new StringBuilder(constClass.getName());
        Constant constParent = constClass.getNamespace();

        while (true)
            {
            switch (constParent.getFormat())
                {
                case Module:
                    sb.insert(0, ':')
                      .insert(0, ((ModuleConstant) constParent).getName());
                    return sb.toString();

                case Package:
                    PackageConstant constPackage = ((PackageConstant) constParent);
                    sb.insert(0, '.')
                      .insert(0, constPackage.getName());
                    constParent = constPackage.getNamespace();
                    break;

                case Class:
                    ClassConstant constParentClass = ((ClassConstant) constParent);
                    sb.insert(0, '$')
                      .insert(0, constParentClass.getName());
                    constParent = constParentClass.getNamespace();
                    break;

                default:
                    throw new IllegalStateException();
                }
            }
        }

    public static ClassStructure getSuper(ClassStructure structure)
        {
        Optional<ClassStructure.Contribution> opt = structure.getContributionsAsList().stream().
                filter((c) -> c.getComposition().equals(ClassStructure.Composition.Extends)).findFirst();
        return (ClassStructure) (opt.isPresent() ? opt.get().getClassConstant().getComponent() : null);
        }

    public static MethodStructure getMethod(ClassStructure structure, String sName, String[] asArgType)
        {
        // TODO:
        return null;
        }

    public static MethodStructure getDefaultConstructor(ClassStructure structure)
        {
        // TODO:
        return null;
        }

    public static MethodStructure getSuper(MethodStructure structure)
        {
        // TODO:
        return null;
        }

    public static int getScopeCount(MethodStructure method)
        {
        // TODO:
        return 5;
        }

    public static int getArgCount(MethodStructure method)
        {
        // TODO:
        return 3;
        }

    public static int getVarCount(MethodStructure method)
        {
        // TODO:
        return 20;
        }

    public static Op[] getOps(MethodStructure method)
        {
        // TODO:
        return null;
        }

    public static MethodStructure getFinalizer(MethodStructure constructor)
        {
        // TODO:
        return null;
        }

    public static Type getReturnType(MethodStructure method, int iRet, TypeComposition clzParent)
        {
        // TODO: may need to resolve
        return null;
        }

    public static boolean isNative(MethodStructure method)
        {
        // TODO:
        return false;
        }

    public static xFunction.FullyBoundHandle makeFinalizer(MethodStructure constructor, ObjectHandle[] ahArg)
        {
        MethodStructure methodFinally = getFinalizer(constructor);

        return methodFinally == null ? null : xFunction.makeHandle(methodFinally).bindAll(ahArg);
        }

    public static boolean isAtomic(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    public static boolean isReadOnly(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    public static boolean isInjectable(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    public static boolean isRef(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    public static boolean isGenericType(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    public static ClassTemplate getRefTemplate(TypeSet types, PropertyStructure property)
        {
        return null;
        }

    public static MethodStructure getGetter(PropertyStructure property)
        {
        return null;
        }

    public static MethodStructure getSetter(PropertyStructure property)
        {
        return null;
        }
    }
