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
        super(types, "x:collections.Array<ElementType>", "x.Object", Shape.Class);

        addImplement("x:collections.Sequence<ElementType>");
        }

    // subclassing
    protected xArray(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    construct Array(Int capacity)
        //    construct Array(Int capacity, function ElementType(Int) supply)
        //
        //    public/private Int capacity = 0;
        //    public/private Int size     = 0;
        //
        //    Ref<ElementType> elementAt(Int index)
        //    private Element<ElementType>? head;
        //
        //    class Element<RefType>(ElementType value)

        // TODO construct
        // TODO initialization?

        PropertyTemplate ptCap = addPropertyTemplate("capacity", "x:Int");
        ptCap.setSetAccess(Access.Private);

        PropertyTemplate ptLen = addPropertyTemplate("size", "x:Int");
        ptLen.setSetAccess(Access.Private);

        PropertyTemplate ptHead = addPropertyTemplate("head", "x:Ref<ElementType>");
        ptHead.setGetAccess(Access.Private);
        ptHead.setSetAccess(Access.Private);

        addMethodTemplate("elementAt", INT, new String[] {"x:Ref<ElementType>"});

        // TODO child class
        }
    }
