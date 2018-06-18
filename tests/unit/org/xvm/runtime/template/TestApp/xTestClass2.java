package org.xvm.runtime.template.TestApp;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template.xObject;


/**
 * A test class.
 */
public class xTestClass2 extends xObject
    {
    private final Adapter adapter;

    public xTestClass2(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        adapter = templates.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        MethodStructure construct = getMethodStructure("construct", new String[]{"Int64", "String"});
        MethodStructure ftFinally = getMethodStructure("finally", new String[]{"Int64", "String"});

        construct.setOps(new Op[]
            { // #0 = i; #1 = s
                new X_Print(
                    adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
                new L_Set(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 0),
                new Construct_1(adapter.getMethodConstId("TestApp.TestClass", "construct"), 1),
                new Return_0(),
            });
        construct.setConstructFinally(ftFinally);

        ftFinally.setOps(new Op[]
            { // #0 = i; #1 = s
            new X_Print(adapter.ensureValueConstantId("# in finally: TestClass2 #")),
            new X_Print(0),
            new X_Print(1),
            new Return_0(),
            });

        }
    }
