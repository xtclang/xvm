package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIterator
        extends TypeCompositionTemplate
    {
    public xIterator(TypeSet types)
        {
        super(types, "x:Iterator<ElementType>", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    conditional ElementType next();
        //    Void forEach(function Void consume(ElementType))
        //    Boolean forEach(function Boolean consume(ElementType))


        }
    }
