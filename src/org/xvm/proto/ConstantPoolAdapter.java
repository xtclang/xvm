package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.ModuleConstant;
import org.xvm.asm.ConstantPool.MethodConstant;
import org.xvm.asm.ConstantPool.PackageConstant;
import org.xvm.asm.ConstantPool.PropertyConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import java.util.HashMap;
import java.util.Map;

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
    private Map<String, Integer> m_mapMethods = new HashMap<>();

    public void registerClass(TypeCompositionTemplate template)
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
        m_mapClasses.put(sClassName, constClass.getPosition());
        }

    public void registerInvocable(InvocationTemplate method)
        {
        // TODO: params; returns
        String sName = method.f_sName;
        TypeCompositionTemplate templateClazz = method.getClazzTemplate();
        String sClzName = templateClazz.f_sName;
        ClassConstant constClass = getClassConstant(getClassConstId(sClzName));

        MethodConstant constMethod = m_constantPool.ensureMethodConstant(constClass, sName, null, null, null);

        m_mapMethods.put(sClzName + '#' + sName, constMethod.getPosition());
        }

    public int getClassConstId(String sName)
        {
        return m_mapClasses.get(sName);
        }

    public ClassConstant getClassConstant(int nConstId)
        {
        return (ClassConstant) m_constantPool.getConstant(nConstId);
        }

    public int getPropertyConstId(String sClassName, String sPropName)
        {
        return m_mapProperties.get(sClassName + '#' + sPropName);
        }

    public PropertyConstant getPropertyConstant(int nConstId)
        {
        return (PropertyConstant) m_constantPool.getConstant(nConstId);
        }

    // TODO: parameters, returns
    public int getMethodConstId(String sClassName, String sMethName)
        {
        return m_mapMethods.get(sClassName + '#' + sMethName);
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
    public int ensureConstantValue(Object oValue)
        {
        Constant constant;
        if (oValue instanceof Integer || oValue instanceof Long)
            {
            constant = m_constantPool.ensureIntConstant(((Number) oValue).longValue());
            }
        else if (oValue instanceof String)
            {
            constant = m_constantPool.ensureCharStringConstant((String) oValue);
            }
        else if (oValue instanceof Character)
            {
            constant = m_constantPool.ensureCharConstant(((Character) oValue).charValue());
            }
        else
            {
            throw new IllegalArgumentException();
            }

        return constant.getPosition();
        }

    // helper methods
    public static String getClassName(ClassConstant constClass)
        {
        StringBuilder sb = new StringBuilder(constClass.getName());
        Constant constParent = constClass.getNamespace();

        while (true)
            {
            switch (constParent.getType())
                {
                case Module:
                    sb.insert(0, ':')
                      .insert(0, ((ModuleConstant) constParent).getQualifiedName());
                    return sb.toString();

                case Package:
                    PackageConstant constPackage = ((PackageConstant) constParent);
                    sb.insert(0, ':')
                      .insert(0, constPackage.getName());
                    constParent = constPackage.getNamespace();
                    break;

                default:
                    throw new IllegalStateException();
                }
            }
        }

    // signature resolves and appends the actual type parameters
    public static String getClassSignature(ClassConstant constClass)
        {
        return getClassName(constClass);
        }
    }
