package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIterable
        extends xObject
    {
    public xIterable(TypeSet types)
        {
        // deferring the variable length generics <ElementType...>
        super(types, "x:Iterable<ElementType>", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    Iterator<ElementType> iterator();
        //    Iterator<ElementType> iterator(function Boolean (ElementType) match)
        //    Void forEach(function Void(ElementType) process)

        addMethodTemplate("iterator", VOID, new String[]{"x:Iterator<ElementType>"});
        addMethodTemplate("iterator", new String[]{"x:Function"}, new String[]{"x:Iterator<ElementType>"}); // not quite right
        addMethodTemplate("forEach", new String[]{"x:Function"}, VOID); // not quite right
        }
    }
