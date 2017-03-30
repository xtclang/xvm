package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xRange
        extends xObject
    {
    public xRange(TypeSet types)
        {
        super(types, "x:Range<ElementType>", "x:Object", Shape.Mixin);

        addImplement("x:Iterable<ElementType>");
        }

    @Override
    public void initDeclared()
        {
        //    @ro Int size;
        //    Sequence<ElementType> subSequence(Int start, Int end);
        //    Sequence<ElementType> reify();
        //    Iterator<ElementType> iterator()

        ensurePropertyTemplate("size", "x:Int");
        ensureMethodTemplate("subSequence", new String[]{"x:Int", "x:Int"}, THIS);
        ensureMethodTemplate("reify", VOID, THIS);
        ensureMethodTemplate("iterator", VOID, new String[]{"x:Iterator<ElementType>"});
        }
    }
