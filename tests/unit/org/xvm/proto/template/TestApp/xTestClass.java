package org.xvm.proto.template.TestApp;

import org.xvm.asm.ClassStructure;

import org.xvm.proto.*;

import org.xvm.proto.op.*;

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
        MethodInfo construct = ensureMethodInfo("construct", STRING);
        MethodInfo ftFinally = ensureMethodInfo("finally", STRING);

        construct.m_aop = new Op[]
            { // #0 = s
            new X_Print(-adapter.ensureValueConstantId("\n# in constructor: TestClass #")),
            new LSet(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0),
            new Return_0(),
            };
        construct.m_cVars = 1;
        construct.m_mtFinally = ftFinally;

        ftFinally.m_aop = new Op[]
            { // #0  = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass #")),
            new X_Print(0),
            new Return_0(),
            };
        ftFinally.m_cVars = 1;

        // --- method1()
        MethodInfo mtMethod1 = ensureMethodInfo("method1", VOID, INT);
        mtMethod1.m_aop = new Op[]
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
            };
        mtMethod1.m_cVars = 4;

        // ----- exceptional()
        MethodInfo mtExceptional = ensureMethodInfo("exceptional",
                new String[]{"String?"}, INT);
        mtExceptional.m_aop = new Op[]
            { // #0 = s
            new Var(adapter.getClassTypeConstId("Exception")), // #1
            new New_N(adapter.getMethodConstId("Exception", "construct"),
                        new int[]{0, -adapter.ensureValueConstantId(null)}, 1),
            new Throw(1),
            };
        mtExceptional.m_cVars = 2;

        // ----- to<String>()
        MethodInfo mtTo = ensureMethodInfo("to", VOID, STRING);
        mtTo.m_aop = new Op[]
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
            };
        mtTo.m_cVars = 3;
        }
    }
