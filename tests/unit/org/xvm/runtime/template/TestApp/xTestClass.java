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
public class xTestClass extends xObject
    {
    private final Adapter adapter;

    public xTestClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        adapter = templates.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        // --- constructor()
        MethodStructure construct = getMethodStructure("construct", STRING);
        MethodStructure ftFinally = getMethodStructure("finally", STRING);

        construct.setOps(new Op[]
            { // #0 = s
            new X_Print(
                    adapter.ensureValueConstantId("\n# in constructor: TestClass #")),
            new L_Set(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0),
            new Return_0(),
            });
        construct.setConstructFinally(ftFinally);

        ftFinally.setOps(new Op[]
            { // #0  = s
            new X_Print(adapter.ensureValueConstantId("# in finally: TestClass #")),
            new X_Print(0),
            new Return_0(),
            });

        // --- method1()
        MethodStructure mtMethod1 = getMethodStructure("method1", VOID, INT);
        mtMethod1.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestClass.method1 #")),
            new Var_N(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("s")), // #0 (s)
            new L_Get(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0),
            new Var_N(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("of")), // #1 (of)
            new Invoke_11(0, adapter.getMethodConstId("String", "indexOf"),
                adapter.ensureValueConstantId("world"), 1),
            new P_Get(adapter.getPropertyConstId("String", "size"), 0, 2), // next register #3
            new GP_Add(2, 1, 2),
            new Return_1(2),
            });

        // ----- exceptional()
        MethodStructure mtExceptional = getMethodStructure("exceptional",
            new String[]{"String?"}, INT);
        mtExceptional.setOps(new Op[]
            { // #0 = s
            new New_N(adapter.getMethodConstId("Exception", "construct"),
                    new int[] {0, adapter.ensureValueConstantId(null)}, 1), // next register #1
            new Throw(1),
            });

        // ----- to<String>()
        MethodStructure mtTo = getMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
            {
            new Call_01(Op.A_SUPER, 0), // next register #0
            new GP_Add(0, adapter.ensureValueConstantId(": prop1="), 0),
            new Invoke_01(adapter.getPropertyConstId("TestApp.TestClass", "prop1"),
                adapter.getMethodConstId("Object", "to"), 1), // next register #1
            new GP_Add(0, 1, 0),
            new Return_1(0),
            });
        }
    }
