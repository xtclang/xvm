package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xRef
        extends xObject
    {
    public xRef(TypeSet types)
        {
        super(types, "x:Ref", "x:Object", Shape.Interface);

        addImplement("x:Referent");
        }

    @Override
    public void initDeclared()
        {
        //    @ro Boolean assigned;
        //    conditional RefType peek()
        //    RefType get();
        //    Void set(RefType value);
        //    @ro Type ActualType;
        //    static Boolean equals(Ref value1, Ref value2)
        //    @ro String? name;
        //    @ro Int byteLength;
        //    @ro Boolean selfContained;

        addPropertyTemplate("assigned", "x:Boolean").makeReadOnly();
        addMethodTemplate("peek", VOID, new String[]{"x:ConditionalTuple<RefType>"});
        addMethodTemplate("get", VOID, new String[]{"RefType"});
        addMethodTemplate("set", new String[]{"RefType"}, VOID);
        addPropertyTemplate("ActualType", "x:Type").makeReadOnly();
        addPropertyTemplate("name", "x:String|x:Nullable").makeReadOnly();
        addPropertyTemplate("byteLength", "x:Int").makeReadOnly();
        addPropertyTemplate("selfContained", "x:Boolean").makeReadOnly();

        addFunctionTemplate("equals", new String[] {"x:Ref", "x:Ref"}, BOOLEAN);
        }
    }
