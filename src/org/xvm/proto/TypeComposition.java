package org.xvm.proto;

import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.proto.template.xTuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String>)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    public final ClassTemplate f_template;

    public final Map<String, Type> f_mapGenericActual; // corresponding to the m_template's GenericTypeName

    private Type m_typePublic;
    private Type m_typeProtected;
    private Type m_typePrivate;
    private Type m_typeStruct;

    // cached call chain (the top-most method first)
    private Map<MethodConstant, List<MethodStructure>> m_mapMethods = new HashMap<>();

    public TypeComposition(ClassTemplate template, Map<String, Type> mapParamsActual)
        {
        assert(mapParamsActual.size() == template.f_struct.getTypeParams().size() ||
              template instanceof xTuple);

        f_template = template;
        f_mapGenericActual = mapParamsActual;
        }

    public TypeComposition getSuper()
        {
        ClassTemplate templateSuper = f_template.getSuper();
        if (templateSuper != null)
            {
            Map<CharStringConstant, TypeConstant> mapFormalTypes =
                    templateSuper.f_struct.getTypeParams();

            if (mapFormalTypes.isEmpty())
                {
                return templateSuper.ensureClass(Collections.EMPTY_MAP);
                }

            Map<String, Type> mapParams = new HashMap<>();
            for (Map.Entry<CharStringConstant, TypeConstant> entryFormal : mapFormalTypes.entrySet())
                {
                String sParamName = entryFormal.getKey().getValue();
                mapParams.put(sParamName, f_mapGenericActual.get(sParamName));
                }

            return templateSuper.ensureClass(mapParams);
            }

        return null;
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Constants.Access access)
        {
        assert handle.f_clazz == this;

        Type typeCurrent = handle.m_type;
        Type typeTarget;

        switch (access)
            {
            case PUBLIC:
                typeTarget = ensurePublicType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PROTECTED:
                typeTarget = ensureProtectedType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PRIVATE:
                typeTarget = ensurePrivateType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case STRUCT:
                typeTarget = ensureStructType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        handle = handle.cloneHandle();
        handle.m_type = typeTarget;
        return handle;
        }

    public synchronized Type ensurePublicType()
        {
        Type type = m_typePublic;
        if (type == null)
            {
            m_typePublic = type = f_template.f_types.createType(
                    f_template, f_mapGenericActual, Constants.Access.PUBLIC);
            }
        return type;
        }
    public synchronized Type ensureProtectedType()
        {
        Type type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_template.f_types.createType(
                    f_template, f_mapGenericActual, Constants.Access.PROTECTED);
            }
        return type;
        }

    public synchronized Type ensurePrivateType()
        {
        Type type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_template.f_types.createType(
                    f_template, f_mapGenericActual, Constants.Access.PRIVATE);
            }
        return type;
        }

    public synchronized Type ensureStructType()
        {
        Type type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_template.f_types.createType(
                    f_template, f_mapGenericActual, Constants.Access.STRUCT);
            }
        return type;
        }

    public boolean isStruct(Type type)
        {
        return type == m_typeStruct;
        }

    // does this class extend that?
    public boolean extends_(TypeComposition that)
        {
        assert that.f_template.f_struct.getFormat() != Component.Format.INTERFACE;

        if (this.f_template.extends_(that.f_template))
            {
            // TODO: check the generic type relationship
            return true;
            }

        return false;
        }

    // retrieve the actual type for the specified formal parameter name
    public Type getFormalType(String sFormalName)
        {
        Type type = f_mapGenericActual.get(sFormalName);
        if (type == null)
            {
            // TODO: check the super class?

            throw new IllegalArgumentException(
                    "Invalid formal name: " + sFormalName + " for " + f_template);
            }
        return type;
        }

    // create a sequence of frames to be called in the inverse order (the base super first)
    public Frame callDefaultConstructors(Frame frame, ObjectHandle hStruct, ObjectHandle[] ahVar,
                                         Supplier<Frame> continuation)
        {
        ClassTemplate template = f_template;
        MethodStructure methodDefault = template.getMethod("default", ClassTemplate.VOID, ClassTemplate.VOID);
        ClassTemplate templateSuper = template.getSuper();

        Frame frameDefault;
        if (methodDefault == null)
            {
            frameDefault = null;
            }
        else
            {
            frameDefault = frame.f_context.createFrame1(frame, methodDefault,
                    hStruct, ahVar, Frame.RET_UNUSED);
            frameDefault.m_continuation = continuation;
            continuation = null;
            }

        Frame frameSuper = null;
        if (templateSuper != null)
            {
            TypeComposition clazzSuper = templateSuper.ensureClass(f_mapGenericActual);
            frameSuper = clazzSuper.callDefaultConstructors(frame, hStruct, ahVar, continuation);
            }

        if (frameSuper == null)
            {
            return frameDefault;
            }

        frameSuper.m_continuation = () -> frameDefault;
        return frameSuper;
        }

    // retrieve the call chain for the specified method
    // TODO: replace MethodConstant with MethodIdConstant
    public List<MethodStructure> getMethodCallChain(MethodConstant constMethod)
        {
        return m_mapMethods.computeIfAbsent(constMethod,
                cm -> collectMethodCallChain(cm, new LinkedList<>()));
        }

    // find a matching method and add to the list
    // TODO: replace MethodConstant with MethodIdConstant
    protected List<MethodStructure> collectMethodCallChain(
            MethodConstant constMethod, List<MethodStructure> list)
        {
        ClassTemplate template = f_template;
        MethodStructure method = template.getMethod(constMethod.getName(), null, null);
        if (method != null)
            {
            list.add(method);
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper != null)
            {
            clzSuper.collectMethodCallChain(constMethod, list);
            }

        // TODO: walk default methods on interfaces
        return list;
        }

    // resolve the super call chain for the specified method
    public MethodStructure resolveSuper(MethodStructure method)
        {
        MethodConstant constMethod = method.getIdentityConstant();

        List<MethodStructure> listMethods = m_mapMethods.computeIfAbsent(constMethod, cm ->
        {
        Component container = method.getParent().getParent();
        return container instanceof PropertyStructure ?
                collectAccessorCallChain(container.getName(), cm, new LinkedList<>()) :
                collectMethodCallChain(cm, new LinkedList<>());
        });

        for (int i = 0, c = listMethods.size(); i < c - 1; i++)
            {
            if (listMethods.get(i).equals(method))
                {
                return listMethods.get(i + 1);
                }
            }
        return null;
        }

    // find a matching property accessor and add to the list
    // TODO: replace MethodConstant with MethodIdConstant
    protected List<MethodStructure> collectAccessorCallChain(
            String sPropName, MethodConstant constMethod, List<MethodStructure> list)
        {
        ClassTemplate template = f_template;
        PropertyStructure property = template.getProperty(sPropName);

        if (property != null)
            {
            MultiMethodStructure mms = (MultiMethodStructure) property.getChild(constMethod.getName());
            if (mms != null)
                {
                // TODO: compare the signature
                list.add((MethodStructure) mms.children().get(0));
                }
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper != null)
            {
            clzSuper.collectAccessorCallChain(sPropName, constMethod, list);
            }

        // TODO: walk default methods on interfaces
        return list;
        }

    // retrieve the property structure for the specified property
    // TODO: replace PropertyConstant with PropertyIdConstant (or String)
    public PropertyStructure getProperty(PropertyConstant constProperty)
        {
        String sPropName = constProperty.getName();
        PropertyStructure property = f_template.getProperty(sPropName);

        if (property != null)
            {
            return property;
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper != null)
            {
            return clzSuper.getProperty(constProperty);
            }

        return null;
        }

    @Override
    public String toString()
        {
        return f_template.f_sName + Utils.formatArray(f_mapGenericActual.values().toArray(), "<", ">", ", ");
        }
    }
