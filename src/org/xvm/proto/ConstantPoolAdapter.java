package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.*;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.PropertyTemplate;
import org.xvm.util.Handy;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


/**
 * A temporary intermediary between the RT and the ConstantPool
 *
 * @author gg 2017.03.08
 */
public class ConstantPoolAdapter
    {
    public ConstantPool m_constantPool = new ConstantPool(null);

    // the template composition: name -> the corresponding ClassConstant id
    private Map<String, Integer> m_mapClasses = new HashMap<>();

    // the property template: fully qualified name (x:collections.Map#size) -> the corresponding PropertyConstant id
    private Map<String, Integer> m_mapProperties = new HashMap<>();

    // the method template fully qualified name (x:collections.Map#get[KeyType]) -> the corresponding MethodConstant id
    private Map<String, Integer> m_mapMethods = new TreeMap<>();

    public void registerTemplate(TypeCompositionTemplate template)
        {
        String sClassName = template.f_sName;

        int ofModule = sClassName.indexOf(':');
        String sModule = sClassName.substring(0, ofModule);

        Constant constParent = m_constantPool.ensureModuleConstant(sModule);

        int ofStart = ofModule + 1;
        for (int ofPackage = sClassName.indexOf('.', ofStart); ofPackage > 0;
             ofStart = ofPackage + 1, ofPackage = sClassName.indexOf('.', ofStart))
            {
            String sPackage = sClassName.substring(ofStart, ofPackage);
            constParent = m_constantPool.ensurePackageConstant(constParent, sPackage);
            }

        for (int ofChild = sClassName.indexOf('$', ofStart); ofChild > 0;
             ofStart = ofChild + 1, ofChild = sClassName.indexOf('$', ofStart))
            {
            String sClz = sClassName.substring(ofStart, ofChild);
            constParent = m_constantPool.ensureClassConstant(constParent, sClz);
            }

        ClassConstant constClass =
                m_constantPool.ensureClassConstant(constParent, sClassName.substring(ofStart));
        ClassTypeConstant constType =
                m_constantPool.ensureClassTypeConstant(constClass, null);
        m_mapClasses.put(sClassName, constType.getPosition());
        }

    public void registerProperty(TypeCompositionTemplate templateClazz, PropertyTemplate templateProperty)
        {
        String sName    = templateProperty.f_sName;
        String sClzName = templateClazz.f_sName;

        ClassTypeConstant constClass = getClassTypeConstant(getClassTypeConstId(sClzName));
        ClassTypeConstant constType  = getClassTypeConstant(getClassTypeConstId("x:Object")); // TODO
        PropertyConstant constProperty = m_constantPool.ensurePropertyConstant(constClass.getClassConstant(), sName);

        m_mapProperties.put(sClzName + '#' + sName, constProperty.getPosition());
        }

    public void registerInvocable(TypeCompositionTemplate templateClazz, InvocationTemplate templateMethod)
        {
        String sName    = templateMethod.f_sName;
        String sClzName = templateClazz.f_sName;

        ClassTypeConstant constClass = getClassTypeConstant(getClassTypeConstId(sClzName));

        TypeName[] atnArg = templateMethod.m_argTypeName;
        TypeConstant[] atcArg = new TypeConstant[atnArg.length];
        for (int i = 0, c = atnArg.length; i < c; i++)
            {
            atcArg[i] = getTypeConstant(atnArg[i], templateClazz);
            }

        TypeName[] atnRet = templateMethod.m_retTypeName;
        TypeConstant[] atcRet = new TypeConstant[atnRet.length];
        for (int i = 0, c = atnRet.length; i < c; i++)
            {
            atcRet[i] = getTypeConstant(atnRet[i], templateClazz);
            }

        MethodConstant constMethod = m_constantPool.ensureMethodConstant(
                constClass.getClassConstant(), sName, templateMethod.m_access, atcArg, atcRet);

        m_mapMethods.put(sClzName + '#' + sName, constMethod.getPosition());
        m_mapMethods.put(sClzName + '#' + templateMethod.getSignature(), constMethod.getPosition());
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

                int nTypeId = m_constantPool.ensureClassTypeConstant(
                        constType.getClassConstant(), null, aType).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }

        throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
        }

    protected TypeConstant getTypeConstant(TypeName tn, TypeCompositionTemplate template)
        {
        String sName = tn.getSimpleName();
        TypeConstant constType = getClassTypeConstant(sName);
        if (constType != null)
            {
            return constType;
            }

        if (sName.equals(TypeName.THIS_TYPE))
            {
            return getClassTypeConstant(template.f_sName);
            }

        // TODO: composite types (e.g.) T1 | T2
        return getClassTypeConstant("x:Object");
        }

    protected ClassTypeConstant getClassTypeConstant(String sName)
        {
        Integer IClass = m_mapClasses.get(sName);
        return IClass == null ? null : getClassTypeConstant(IClass.intValue());
        }

    public ClassTypeConstant getClassTypeConstant(int nConstId)
        {
        return (ClassTypeConstant) m_constantPool.getConstant(nConstId);
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
        return (PropertyConstant) m_constantPool.getConstant(nConstId);
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
        return (MethodConstant) m_constantPool.getConstant(nConstId);
        }

    public Constant getConstantValue(int nConstId)
        {
        return m_constantPool.getConstant(nConstId);
        }

    // FOR SIMULATION ONLY
    public ModuleConstant ensureModuleConstant(String sModule)
        {
        return m_constantPool.ensureModuleConstant(sModule);
        }

    public int ensureValueConstantId(Object oValue)
        {
        return ensureValueConstant(oValue).getPosition();
        }

    protected Constant ensureValueConstant(Object oValue)
        {
        if (oValue instanceof Integer || oValue instanceof Long)
            {
            return m_constantPool.ensureIntConstant(((Number) oValue).longValue());
            }

        if (oValue instanceof String)
            {
            return m_constantPool.ensureCharStringConstant((String) oValue);
            }

        if (oValue instanceof Character)
            {
            return m_constantPool.ensureCharConstant(((Character) oValue).charValue());
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
            return m_constantPool.ensureTupleConstant(aconst);
            }

        throw new IllegalArgumentException();
        }

    // helper methods
    public static String getClassName(Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            return getClassName((ClassConstant) constant);
            }

        if (constant instanceof ClassTypeConstant)
            {
            return getClassName(((ClassTypeConstant) constant).getClassConstant());
            }

        if (constant instanceof MethodConstant)
            {
            return getClassName(((MethodConstant) constant).getNamespace());
            }

        throw new IllegalArgumentException("Unsupported constant type: " + constant);
        }

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
                      .insert(0, ((ModuleConstant) constParent).getQualifiedName());
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

    // signature resolves and appends the actual type parameters
    public static String getClassSignature(ClassTypeConstant constClass)
        {
        return constClass.getValueString();
        }
    }
