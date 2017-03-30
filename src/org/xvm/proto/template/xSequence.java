package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xSequence
        extends xObject
    {
    public xSequence(TypeSet types)
        {
        super(types, "x:collections.Sequence<ElementType>", "x:Object", Shape.Interface);

        addImplement("x:UniformIndex<x:Int, ElementType>");
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
