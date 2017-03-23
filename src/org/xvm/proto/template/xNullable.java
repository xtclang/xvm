package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNullable
        extends xObject
    {
    public xNullable(TypeSet types)
        {
        super(types, "x:Nullable", "x:Object", Shape.Enum);
        }

    // subclassing
    protected xNullable(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        if (!f_sName.equals("x:Nullable")) return; // avoid recursion

        // in-place declaration for True and False
        // in-place generation of Hashable
        f_types.addTemplate(new xNullable(f_types, "x:Nullable.Null", "x:Nullable", Shape.Enum));
        }
    }
