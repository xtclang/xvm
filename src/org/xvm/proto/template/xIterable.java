package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIterable
        extends TypeCompositionTemplate
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

        ensureMethodTemplate("iterator", VOID, new String[]{"x:Iterator<ElementType>"});
        ensureMethodTemplate("iterator", new String[]{"x:Function"}, new String[]{"x:Iterator<ElementType>"}); // not quite right
        ensureMethodTemplate("forEach", new String[]{"x:Function"}, VOID); // not quite right
        }
    }
