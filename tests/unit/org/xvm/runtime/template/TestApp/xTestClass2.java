package org.xvm.runtime.template.TestApp;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeSet;


/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTestClass2 extends ClassTemplate
    {
    private final Adapter adapter;

    public xTestClass2(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        adapter = types.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        MethodStructure construct = ensureMethodStructure("construct", new String[] {"Int64", "String"});
        MethodStructure ftFinally = ensureMethodStructure("finally", new String[] {"Int64", "String"});

        construct.setOps(new Op[]
            { // #0 = i; #1 = s
            new X_Print(
                    adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
            new LSet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 0),
            new Construct_1(adapter.getMethodConstId("TestApp.TestClass", "construct"),
                    1),
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

        MethodStructure mtMethod1 = ensureMethodStructure("method1", VOID, INT);
        mtMethod1.setOps(new Op[]
            {
            new X_Print(
                    adapter.ensureValueConstantId("\n# in TestClass2.method1() #")),
            new Var(adapter.getClassTypeConstId("Int64")), // #0
            new Call_01(Op.A_SUPER, 0),
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new LGet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 1),
            new Add(0, 1, 0),
            new Return_1(0),
            });

        MethodStructure mtTo = ensureMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
            {
                new Var(adapter.getClassTypeConstId("String")), // #0
                new Call_01(Op.A_SUPER, 0),
                new Add(0, adapter.ensureValueConstantId(", prop2="), 0),
                new Var(adapter.getClassTypeConstId("Int64")), // #1
                new LGet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 1),
                new Var(adapter.getClassTypeConstId("String")), // #2
                new Invoke_01(1, adapter.getMethodConstId("Object", "to"), 2),
                new Add(0, 2, 0),
                new Return_1(0),
            });
        }
    }
