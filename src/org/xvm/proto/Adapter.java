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
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypedefConstant;

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
        return getClassTypeConstId(sName, null);
        }

    // get a type id for the specified name in the context of the
    // specified template
    public int getClassTypeConstId(String sName, ClassTemplate template)
        {
        if (m_mapClasses.containsKey(sName))
            {
            return m_mapClasses.get(sName);
            }

        TypeConstant constType = getClassType(sName, template);

        int nTypeId = constType.getPosition();

        m_mapClasses.put(sName, nTypeId);

        return nTypeId;
        }

    // get a class type for the specified name in the context of the
    // specified template
    public TypeConstant getClassType(String sName, ClassTemplate template)
        {
        ConstantPool pool = f_container.f_pool;

        boolean fNullable = sName.endsWith("?");
        if (fNullable)
            {
            sName = sName.substring(0, sName.length() - 1);
            }

        TypeConstant constType = null;

        int ofTypeParam = sName.indexOf('<');
        if (ofTypeParam >= 0)
            {
            String sParam = sName.substring(ofTypeParam + 1, sName.length() - 1);
            String sSimpleName = sName.substring(0, ofTypeParam);

            ClassConstant constClass = f_container.f_types.getClassConstant(sSimpleName);
            if (constClass != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                TypeConstant[] acType = getTypeConstants(template, asType);

                constType = pool.ensureClassTypeConstant(constClass, null, acType);
                }
            }
        else
            {
            Component component = f_container.f_module.getChildByPath(sName);
            if (component != null)
                {
                IdentityConstant constId = component.getIdentityConstant();
                if (constId instanceof ClassConstant)
                    {
                    constType = ((ClassConstant) constId).asTypeConstant();
                    }
                else if (constId instanceof TypedefConstant)
                    {
                    constType = ((TypedefStructure) component).getType();
                    }
                }
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
            }

        return fNullable ? pool.ensureNullableTypeConstant(constType) : constType;
        }

    public int ensureEnumConstId(String sEnum)
        {
        return f_container.f_types.getClassConstant(sEnum).getPosition();
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
            ClassConstant constClass = f_container.f_types.getClassConstant(sClassName);
            ClassStructure struct = (ClassStructure) constClass.getComponent();
            PropertyStructure prop = (PropertyStructure) struct.getChild(sPropName);
            return prop.getIdentityConstant().getPosition();
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
            if (template != null && template.isGenericType(sType))
                {
//                ClassTypeConstant constClass = template.getTypeConstant();
//                aType[i] = pool.ensureParameterTypeConstant(constClass, sType);
// TODO: review
                PropertyStructure prop = template.getProperty(sType);
                aType[i] = pool.ensureClassTypeConstant(prop.getIdentityConstant(), Constants.Access.PUBLIC);
                }
            else
                {
                aType[i] = getClassType(sType, template);
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
            aType[i] = new Parameter(pool, (ParameterizedTypeConstant) pool.getConstant(getClassTypeConstId(sType)),
                    (fReturn ?"r":"p")+i, null, fReturn, i, false);
            }
        return aType;
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
            return f_container.f_types.getClassConstant(
                    ((Boolean) oValue).booleanValue() ? "Boolean.True" : "Boolean.False");
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
            TypeConstant type = f_container.f_pool.ensureEcstasyTypeConstant("Tuple"); // TODO need type-params
            return f_container.f_pool.ensureTupleConstant(type, aconst);
            }

        if (oValue == null)
            {
            return f_container.f_types.getClassConstant("Nullable.Null");
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
        ClassTemplate.MethodInfo tm = method.getInfo();
        return tm == null ? 1 : tm.m_cScopes;
        }

    public static int getArgCount(MethodStructure method)
        {
        MethodConstant constMethod = method.getIdentityConstant();
        return constMethod.getRawParams().length;
        }

    public int getVarCount(MethodStructure method)
        {
        ClassTemplate.MethodInfo tm = method.getInfo();
        return tm == null || tm.m_fNative ? // this can only be a constructor
                method.getIdentityConstant().getRawParams().length:
                tm.m_cVars;
        }

    public Op[] getOps(MethodStructure method)
        {
        ClassTemplate.MethodInfo tm = method.getInfo();
        return tm == null ? null : tm.m_aop;
        }

    public MethodStructure getFinalizer(MethodStructure constructor)
        {
        ClassTemplate.MethodInfo tmConstruct = constructor.getInfo();
        ClassTemplate.MethodInfo tmFinally = tmConstruct == null ? null : tmConstruct.m_mtFinally;
        return tmFinally == null ? null : tmFinally.f_struct;
        }

    public boolean isNative(MethodStructure method)
        {
        ClassTemplate.MethodInfo tm = method.getInfo();
        return tm != null && tm.m_fNative;
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

    public TypeConstant resolveType(PropertyStructure property)
        {
        TypeConstant constType = property.getType();

        if (constType instanceof ParameterizedTypeConstant)
            {
            return constType;
            }

        throw new UnsupportedOperationException("Unsupported type: " + constType + " for " + property);
        }
    }
