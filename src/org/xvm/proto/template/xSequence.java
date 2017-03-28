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
        super(types, "x:Sequence<ElementType>", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        ensureImplement("x:UniformIndex<x:Int, ElementType>");
        ensureImplement("x:Iterable<ElementType>");

        //    @ro Int size;
        //    Sequence<ElementType> subSequence(Int start, Int end);
        //    Sequence<ElementType> reify();
        //    Iterator<ElementType> iterator()

        addPropertyTemplate("size", "x:Int");
        addMethodTemplate("subSequence", new String[]{"x:Int", "x:Int"}, THIS);
        addMethodTemplate("reify", VOID, THIS);
        addMethodTemplate("iterator", VOID, new String[]{"x:Iterator<ElementType>"});
        }
    }
