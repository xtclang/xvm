package org.xvm.proto.template;

import org.xvm.proto.*;

/**
 * A test service.
 *
 * @author gg 2017.03.15
 */
public class xTestService extends xObject
    {
    private final ConstantPoolAdapter f_adapter;

    public xTestService(TypeSet types, ConstantPoolAdapter adapter)
        {
        super(types, "x:TestService", "x:Object", Shape.Service);

        f_adapter = adapter;
        }

    @Override
    public void initDeclared()
        {
        ConstantPoolAdapter adapter = f_adapter;

        ensurePropertyTemplate("prop1", "x:String");

        }
    }
