package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xClass
        extends xObject
    {
    public xClass(TypeSet types)
        {
        super(types, "x:Class<ClassType>", "x:Object", Shape.Interface);
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
        addPropertyTemplate("parent", "x:Class|x:Method|x:Function|x:Property|x:Nullable").makeReadOnly();
        addPropertyTemplate("name", "x:String").makeReadOnly();
        addPropertyTemplate("PublicType", "x:Type").makeReadOnly();
        addPropertyTemplate("ProtectedType", "x:Type").makeReadOnly();
        addPropertyTemplate("PrivateType", "x:Type").makeReadOnly();
        addPropertyTemplate("children", "x:Map<x:String,x:Class|x:MultiMethod|x:Property|x:MultiFunction>").makeReadOnly();
        addPropertyTemplate("classes", "x:Map<x:String,x:Class>").makeReadOnly();
        addPropertyTemplate("methods", "x:Map<x:String,x:MultiMethod").makeReadOnly();
        addPropertyTemplate("properties", "x:Map<x:String,x:Property").makeReadOnly();
        addPropertyTemplate("functions", "x:Map<x:String,x:MultiFunction>").makeReadOnly();
        addMethodTemplate("implements", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        addMethodTemplate("extends", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        addMethodTemplate("incorporates", new String[]{"x:Class"}, BOOLEAN); // non-"virtual"
        addPropertyTemplate("isService", "x:Boolean").makeReadOnly();
        addPropertyTemplate("isConst", "x:Boolean").makeReadOnly();
        addPropertyTemplate("singleton", "x:ConditionalTuple<ClassType>").makeReadOnly();
        }
    }
