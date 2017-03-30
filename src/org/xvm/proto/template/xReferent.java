package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xReferent
        extends xObject
    {
    public xReferent(TypeSet types)
        {
        super(types, "x:Referent", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    @ro Type ActualType;
        //
        //    <AsType> AsType maskAs(Type AsType);
        //    <AsType> conditional AsType revealAs(Type AsType);
        //    Boolean instanceOf(Type type);
        //    Boolean implements(Class interface);
        //    Boolean extends(Class class);
        //    Boolean incorporates(Class traitOrMixin);
        //    @ro Boolean isService;
        //
        //    @ro Boolean isConst;
        //    @ro Boolean immutable;

        ensurePropertyTemplate("ActualType", "x:Type").makeReadOnly();
        }
    }
