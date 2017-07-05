package org.xvm.proto;

import org.xvm.asm.*;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.template.xFunction;

import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xService;
import org.xvm.util.Handy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * A temporary intermediary between the RT, the ConstantPool and ClassStructure
 *
 * @author gg 2017.03.08
 */
public class Adapter
    {
    public final Container f_container;

    // the template composition: name -> the corresponding ClassConstant id
    private Map<String, Integer> m_mapClasses = new HashMap<>();

    public Adapter(Container container)
        {
        f_container = container;
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

                int nTypeId = f_container.f_pool.ensureClassTypeConstant(
                        constType.getClassConstant(), null, getTypeConstants(asType)).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }
        else
            {
            ClassTypeConstant constType = getClassTypeConstant(sName);
            if (constType != null)
                {
                int nTypeId = f_container.f_pool.ensureClassTypeConstant(
                        constType.getClassConstant(), null).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }

        throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
        }

    private TypeConstant[] getTypeConstants(String[] asType)
        {
        int cTypes = asType.length;
        TypeConstant[] aType = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            String sType = asType[i].trim();
            aType[i] = getClassTypeConstant(getClassTypeConstId(sType));
            }
        return aType;
        }

    protected ClassTypeConstant getClassTypeConstant(String sName)
        {
        ClassConstant constClass = (ClassConstant)
                f_container.f_types.getTemplate(sName).f_struct.getIdentityConstant();
        return constClass.asTypeConstant();
        }

    public ClassTypeConstant getClassTypeConstant(int nConstId)
        {
        return (ClassTypeConstant) f_container.f_pool.getConstant(nConstId);
        }

    public int getPropertyConstId(String sClassName, String sPropName)
        {
        try
            {
            return f_container.f_types.getTemplate(sClassName).getProperty(sPropName).
                    getIdentityConstant().getPosition();
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Property is not defined: " + sClassName + '#' + sPropName);
            }
        }

    public int getMethodConstId(String sClassName, String sMethName)
        {
        return getMethodConstId(sClassName, sMethName, null, null);
        }

    public int getMethodConstId(String sClassName, String sMethName, String[] asArgType, String[] asRetType)
        {
        try
            {
            ClassTemplate template = f_container.f_types.getTemplate(sClassName);
            MethodStructure method = template.getMethod(sMethName, asArgType, asRetType);
            while (method == null)
                {
                template = template.getSuper();
                method = template.getMethod(sMethName, asArgType, asRetType);
                }
            return method.getIdentityConstant().getPosition();
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Method is not defined: " + sClassName + '#' + sMethName);
            }
        }

    // FOR SIMULATION ONLY

    public int ensureValueConstantId(Object oValue)
        {
        return ensureValueConstant(oValue).getPosition();
        }

    protected Constant ensureValueConstant(Object oValue)
        {
        if (oValue instanceof Integer || oValue instanceof Long)
            {
            return f_container.f_pool.ensureIntConstant(((Number) oValue).longValue());
            }

        if (oValue instanceof String)
            {
            return f_container.f_pool.ensureCharStringConstant((String) oValue);
            }

        if (oValue instanceof Character)
            {
            return f_container.f_pool.ensureCharConstant(((Character) oValue).charValue());
            }

        if (oValue instanceof Boolean)
            {
            return getClassTypeConstant(
                    ((Boolean) oValue).booleanValue() ?
                            "Boolean.True" : "Boolean.False");
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
            return f_container.f_pool.ensureTupleConstant(aconst);
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

    public ClassStructure getSuper(ClassStructure structure)
        {
        Optional<ClassStructure.Contribution> opt = structure.getContributionsAsList().stream().
                filter((c) -> c.getComposition().equals(ClassStructure.Composition.Extends)).findFirst();
        if (opt.isPresent())
            {
            ClassConstant constClass = opt.get().getClassConstant().getClassConstant();
            try
                {
                return (ClassStructure) constClass.getComponent();
                }
            catch (RuntimeException e)
                {
                // TODO: remove when getComponent() is fixed
                String sName = constClass.getName();
                ClassTemplate templateSuper = f_container.f_types.getTemplate(sName);
                return templateSuper.f_struct;
                }
            }
        else
            {
            switch (structure.getFormat())
                {
                case SERVICE:
                    return xService.INSTANCE.f_struct;

                case CLASS:
                    if (structure.getName().equals("Object"))
                        {
                        return null;
                        }
                    // break through
                case INTERFACE:
                case CONST:
                    return xObject.INSTANCE.f_struct;
                }
            }
        return null;
        }

    public MethodStructure addMethod(ClassStructure structure, String sName,
                                            String[] asArgType, String[] asRetType)
        {
        MultiMethodStructure mms = structure.ensureMultiMethodStructure(sName);
        return mms.createMethod(false, Constants.Access.PUBLIC,
                getTypeConstants(asRetType), getTypeConstants(asArgType));
        }

    public static MethodStructure getMethod(ClassStructure structure, String sName,
                                            String[] asArgType, String[] asRetType)
        {
        MultiMethodStructure mms = (MultiMethodStructure) structure.getChild(sName);

        // TODO: use the types
        return mms == null ? null : (MethodStructure) mms.children().get(0);
        }

    public static MethodStructure getDefaultConstructor(ClassStructure structure)
        {
        return getMethod(structure, "default", ClassTemplate.VOID, ClassTemplate.VOID);
        }

    public static MethodStructure getSuper(MethodStructure structure)
        {
        // TODO:
        return null;
        }

    public int getScopeCount(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm == null ? 1 : tm.m_cScopes;
        }

    public static int getArgCount(MethodStructure method)
        {
        MethodConstant constMethod = method.getIdentityConstant();
        return constMethod.getRawParams().length;
        }

    public int getVarCount(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm == null ? 0 : tm.m_cVars;
        }

    public Op[] getOps(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm == null ? null : tm.m_aop;
        }

    public MethodStructure getFinalizer(MethodStructure constructor)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(constructor);
        return tm == null ? null : tm.m_mtFinally.f_struct;
        }

    public static Type getReturnType(MethodStructure method, int iRet, TypeComposition clzParent)
        {
        // TODO: may need to resolve
        return null;
        }

    public boolean isNative(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm != null && tm.m_fNative;
        }

    private ClassTemplate.MethodTemplate getMethodTemplate(MethodStructure method)
        {
        MultiMethodStructure mms = (MultiMethodStructure) method.getParent();
        Component parent = mms.getParent();

        // the parent is either class or a property
        ClassStructure clazz = (ClassStructure) (parent instanceof ClassStructure ? parent : parent.getParent());
        ClassTemplate template = f_container.f_types.getTemplate((IdentityConstant) clazz.getIdentityConstant());
        return template.getMethodTemplate(method.getIdentityConstant());
        }

    public xFunction.FullyBoundHandle makeFinalizer(MethodStructure constructor, ObjectHandle[] ahArg)
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
        MultiMethodStructure mms = (MultiMethodStructure) property.getChild("get");

        // TODO: use the type
        return mms == null ? null : (MethodStructure) mms.children().get(0);
        }

    public static MethodStructure getSetter(PropertyStructure property)
        {
        MultiMethodStructure mms = (MultiMethodStructure) property.getChild("set");

        // TODO: use the type
        return mms == null ? null : (MethodStructure) mms.children().get(0);
        }
    }
