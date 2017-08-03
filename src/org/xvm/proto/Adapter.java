package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.util.Handy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * A temporary intermediary between the RT, the ConstantPool and ClassStructure;
 * FOR SIMULATION ONLY
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

            ClassConstant constClass = getClassConstant(sSimpleName);
            if (constClass != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                ClassTemplate template = f_container.f_types.getTemplate(constClass);
                int nTypeId = f_container.f_pool.ensureClassTypeConstant(constClass, null,
                        getTypeConstants(template, asType)).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }
        else
            {
            ClassConstant constClass = getClassConstant(sName);
            if (constClass != null)
                {
                int nTypeId = f_container.f_pool.
                        ensureClassTypeConstant(constClass, null).getPosition();

                m_mapClasses.put(sName, nTypeId);

                return nTypeId;
                }
            }

        throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
        }

    public int getMethodConstId(String sClassName, String sMethName)
        {
        return getMethodConstId(sClassName, sMethName, null, null);
        }

    public int getMethodConstId(String sClassName, String sMethName, String[] asArgType, String[] asRetType)
        {
        return getMethod(sClassName, sMethName, asArgType, asRetType)
                .getIdentityConstant().getPosition();
        }

    public MethodStructure getMethod(String sClassName, String sMethName, String[] asArgType, String[] asRetType)
        {
        ClassTemplate template = f_container.f_types.getTemplate(sClassName);
        TypeConstant[] atArg = getTypeConstants(template, asArgType);
        TypeConstant[] atRet = getTypeConstants(template, asRetType);

        MethodStructure method;
        do
            {
            method = template.getDeclaredMethod(sMethName, atArg, atRet);
            if (method != null)
                {
                return method;
                }

            ClassTemplate templateCategory = template.f_templateCategory;
            if (templateCategory != null)
                {
                method = templateCategory.getDeclaredMethod(sMethName, atArg, atRet);
                if (method != null)
                    {
                    return method;
                    }
                }

            template = template.getSuper();
            }
        while (template != null);

        if (method == null && asArgType != null)
            {
            method = getMethod(sClassName, sMethName, null, null);

            System.out.println("\n******** parameter mismatch at " + sClassName + "#" + sMethName);
            System.out.println("         arguments " + Arrays.toString(atArg));
            System.out.println("         return " + Arrays.toString(atRet));
            System.out.println("         found " + method.getIdentityConstant() + "\n");

            if (method != null)
                {
                return method;
                }
            }

        throw new IllegalArgumentException("Method is not defined: " + sClassName + '#' + sMethName);
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

    public int ensureValueConstantId(Object oValue)
        {
        return ensureValueConstant(oValue).getPosition();
        }

    public TypeConstant[] getTypeConstants(ClassTemplate template, String[] asType)
        {
        if (asType == null)
            {
            return null;
            }

        ConstantPool pool = f_container.f_pool;
        int cTypes = asType.length;
        TypeConstant[] aType = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            String sType = asType[i].trim();
            if (template.isGenericType(sType))
                {
                aType[i] = template.f_struct.getTypeParams().get(pool.ensureCharStringConstant(sType));
                }
            else
                {
                aType[i] = (ClassTypeConstant) pool.getConstant(getClassTypeConstId(sType));
                }
            }
        return aType;
        }

    private Parameter[] getTypeParameters(String[] asType, boolean fReturn)
        {
        ConstantPool pool = f_container.f_pool;
        int cTypes = asType.length;
        Parameter[] aType = new Parameter[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            String sType = asType[i].trim();
            aType[i] = new Parameter(pool, (ClassTypeConstant) pool.getConstant(getClassTypeConstId(sType)),
                    (fReturn ?"r":"p")+i, null, fReturn, i, false);
            }
        return aType;
        }

    private ClassConstant getClassConstant(String sClassName)
        {
        return (ClassConstant)
                f_container.f_types.getTemplate(sClassName).f_struct.getIdentityConstant();
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
            ClassConstant constBoolean = getClassConstant("Boolean");

            return f_container.f_pool.ensureClassConstant(constBoolean,
                    ((Boolean) oValue).booleanValue() ? "True" : "False");
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

        if (oValue == null)
            {
            return f_container.f_pool.ensureClassConstant(
                    getClassConstant("Nullable"), "Null");
            }

        throw new IllegalArgumentException();
        }

    public MethodStructure addMethod(Component structure, String sName,
                                            String[] asArgType, String[] asRetType)
        {
        MultiMethodStructure mms = structure.ensureMultiMethodStructure(sName);
        return mms.createMethod(false, Constants.Access.PUBLIC,
                getTypeParameters(asRetType, true), getTypeParameters(asArgType, false));
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
        return tm == null || tm.m_fNative ? // this can only be a constructor
                method.getIdentityConstant().getRawParams().length:
                tm.m_cVars;
        }

    public Op[] getOps(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm == null ? null : tm.m_aop;
        }

    public MethodStructure getFinalizer(MethodStructure constructor)
        {
        ClassTemplate.MethodTemplate tmConstruct = getMethodTemplate(constructor);
        ClassTemplate.MethodTemplate tmFinally = tmConstruct == null ? null : tmConstruct.m_mtFinally;
        return tmFinally == null ? null : tmFinally.f_struct;
        }

    public boolean isNative(MethodStructure method)
        {
        ClassTemplate.MethodTemplate tm = getMethodTemplate(method);
        return tm != null && tm.m_fNative;
        }

    private ClassTemplate.MethodTemplate getMethodTemplate(MethodStructure method)
        {
        MultiMethodStructure mms = (MultiMethodStructure) method.getParent();
        Component container = mms.getParent();

        // the container is either a class or a property
        ClassStructure clazz = (ClassStructure) (container instanceof ClassStructure ? container : container.getParent());
        ClassTemplate template = f_container.f_types.getTemplate(clazz.getIdentityConstant());
        return template.getMethodTemplate(method.getIdentityConstant());
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

    public ClassTypeConstant resolveType(PropertyStructure property)
        {
        TypeConstant constType = property.getType();

        if (constType instanceof UnresolvedTypeConstant)
            {
            constType = ((UnresolvedTypeConstant) constType).getResolvedConstant();
            }

        if (constType instanceof ClassTypeConstant)
            {
            return (ClassTypeConstant) constType;
            }

        throw new UnsupportedOperationException("Unsupported type: " + constType + " for " + property);
        }
    }
