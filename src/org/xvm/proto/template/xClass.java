package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xClass
        extends TypeCompositionTemplate
    {
    public static xClass INSTANCE;

    public xClass(TypeSet types)
        {
        super(types, "x:Class<ClassType>", "x:Object", Shape.Interface);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    @ro Class | Method | Function | Property | ... ? parent
        //    @ro String name;
        //    @ro Type PublicType;
        //    @ro Type ProtectedType;
        //    @ro Type PrivateType;
        //
        //    @ro Map<String, Class | MultiMethod | Property | MultiFunction> children;
        //    @ro Map<String, Class> classes;
        //    @ro Map<String, MultiMethod> methods;
        //    @ro Map<String, Property> properties;
        //    Boolean implements(Class interface);
        //    Boolean extends(Class class);
        //    Boolean incorporates(Class traitOrMixin);
        //    @ro Boolean isService;
        //    @ro Boolean isConst;
        //    conditional ClassType singleton;
        ensurePropertyTemplate("parent", "x:Class|x:Method|x:Function|x:Property|x:Nullable").makeReadOnly();
        ensurePropertyTemplate("name", "x:String").makeReadOnly();
        ensurePropertyTemplate("PublicType", "x:Type").makeReadOnly();
        ensurePropertyTemplate("ProtectedType", "x:Type").makeReadOnly();
        ensurePropertyTemplate("PrivateType", "x:Type").makeReadOnly();
        ensurePropertyTemplate("children", "x:Map<x:String,x:Class|x:MultiMethod|x:Property|x:MultiFunction>").makeReadOnly();
        ensurePropertyTemplate("classes", "x:Map<x:String,x:Class>").makeReadOnly();
        ensurePropertyTemplate("methods", "x:Map<x:String,x:MultiMethod").makeReadOnly();
        ensurePropertyTemplate("properties", "x:Map<x:String,x:Property").makeReadOnly();
        ensurePropertyTemplate("functions", "x:Map<x:String,x:MultiFunction>").makeReadOnly();
        ensureMethodTemplate("implements", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        ensureMethodTemplate("extends", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        ensureMethodTemplate("incorporates", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        ensurePropertyTemplate("isService", "x:Boolean").makeReadOnly();
        ensurePropertyTemplate("isConst", "x:Boolean").makeReadOnly();
        ensurePropertyTemplate("singleton", "x:ConditionalTuple<ClassType>").makeReadOnly();
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;

            String sTarget = ConstantPoolAdapter.getClassName(constClass);

            TypeCompositionTemplate target = f_types.getTemplate(sTarget);
            if (target.isSingleton())
                {
                return target.createConstHandle(constant);
                }

            TypeComposition clzTarget = target.resolve(constClass);

            return new ClassHandle(f_clazzCanonical, clzTarget);
            }
        return null;
        }

    public static class ClassHandle
            extends ObjectHandle
        {
        protected TypeComposition m_clzTarget;

        protected ClassHandle(TypeComposition clazz, TypeComposition clzTarget)
            {
            super(clazz);

            m_clzTarget = clzTarget;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_clzTarget;
            }
        }
    }
