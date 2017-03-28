package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xArray
        extends xObject
    {
    public xArray(TypeSet types)
        {
        super(types, "x:collections.Array<ElementType>", "x:Object", Shape.Class);
        }

    // subclassing
    protected xArray(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        ensureImplement("x:Sequence<ElementType>");

        //    construct Array(Int capacity)
        //    construct Array(Int capacity, function ElementType(Int) supply)
        //
        //    public/private Int capacity = 0;
        //    public/private Int size     = 0;
        //
        //    Ref<ElementType> elementAt(Int index)
        //    @op Array.Type<ElementType> slice(Range<Int> range);
        //    Array.Type<ElementType> reify();
        //    @op Array.Type<ElementType> add(Array.Type<ElementType> that);
        //    @op Array.Type<ElementType> replace(Int index, ElementType value);
        //
        //    static Ordered compare(Array value1, Array value2)
        //
        //    private Element<ElementType>? head;
        //    private class Element<RefType>(ElementType value)

        // TODO construct
        // TODO initialization?

        PropertyTemplate ptCap = addPropertyTemplate("capacity", "x:Int");
        ptCap.setSetAccess(Access.Private);

        PropertyTemplate ptLen = addPropertyTemplate("size", "x:Int");
        ptLen.setSetAccess(Access.Private);

        addMethodTemplate("elementAt", INT, new String[]{"x:Ref<ElementType>"});
        addMethodTemplate("slice", new String[]{"x:Range<x:Int>"}, THIS);
        addMethodTemplate("reify", VOID, THIS);
        addMethodTemplate("add", THIS, THIS);
        addMethodTemplate("replace", new String[]{"x:Int", "ElementType"}, THIS);

        addFunctionTemplate("compare", new String[] {"this.Type", "this.Type"}, new String[] {"x:Ordered"});

        PropertyTemplate ptHead = addPropertyTemplate("head", "x:Ref<ElementType>");
        ptHead.setGetAccess(Access.Private);
        ptHead.setSetAccess(Access.Private);


        // TODO child class
        }
    }
