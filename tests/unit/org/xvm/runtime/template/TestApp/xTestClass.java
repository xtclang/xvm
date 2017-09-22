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
public class xTestClass extends ClassTemplate
    {
    private final Adapter adapter;

    public xTestClass(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);


        adapter = types.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        // --- constructor()
        MethodStructure construct = ensureMethodStructure("construct", STRING);
        MethodStructure ftFinally = ensureMethodStructure("finally", STRING);

        construct.setOps(new Op[]
            { // #0 = s
            new X_Print(
                    -adapter.ensureValueConstantId("\n# in constructor: TestClass #")),
            new LSet(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0),
            new Return_0(),
            });
        construct.setMaxVars(1);
        construct.setConstructFinally(ftFinally);

        ftFinally.setOps(new Op[]
            { // #0  = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass #")),
            new X_Print(0),
            new Return_0(),
            });
        ftFinally.setMaxVars(1);

        // --- method1()
        MethodStructure mtMethod1 = ensureMethodStructure("method1", VOID, INT);
        mtMethod1.setOps(new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestClass.method1 #")),
            new NVar(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("s")), // #0 (s)
            new LGet(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0),
            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("of")), // #1 (of)
            new IVar(adapter.getClassTypeConstId("String"),
                    -adapter.ensureValueConstantId("world")), // #2
            new Invoke_11(0, adapter.getMethodConstId("String", "indexOf"), 2, 1),
            new Var(adapter.getClassTypeConstId("Int64")), // #3
            new PGet(0, adapter.getPropertyConstId("String", "size"), 3),
            new Add(3, 1, 3),
            new Return_1(3),
            });
        mtMethod1.setMaxVars(4);

        // ----- exceptional()
        MethodStructure mtExceptional = ensureMethodStructure("exceptional",
                new String[] {"String?"}, INT);
        mtExceptional.setOps(new Op[]
            { // #0 = s
            new Var(adapter.getClassTypeConstId("Exception")), // #1
            new New_N(adapter.getMethodConstId("Exception", "construct"),
                    new int[] {0, -adapter.ensureValueConstantId(null)}, 1),
            new Throw(1),
            });
        mtExceptional.setMaxVars(2);

        // ----- to<String>()
        MethodStructure mtTo = ensureMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
            {
            new Var(adapter.getClassTypeConstId("String")), // #0
            new Call_01(Op.A_SUPER, 0),
            new Add(0, -adapter.ensureValueConstantId(": prop1="), 0),
            new Var(adapter.getClassTypeConstId("String")), // #1
            new LGet(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 1),
            new Var(adapter.getClassTypeConstId("String")), // #2
            new Invoke_01(1, adapter.getMethodConstId("Object", "to"), 2),
            new Add(0, 2, 0),
            new Return_1(0),
            });
        mtTo.setMaxVars(3);
        }
    }
