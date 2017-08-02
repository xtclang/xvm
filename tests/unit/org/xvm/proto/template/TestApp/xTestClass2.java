package org.xvm.proto.template.TestApp;

import org.xvm.asm.ClassStructure;

import org.xvm.proto.Adapter;
import org.xvm.proto.Op;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.*;

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
        adapter.addMethod(f_struct, "construct", new String[]{"Int64", "String"}, VOID);
        adapter.addMethod(f_struct, "finally", new String[]{"Int64", "String"}, VOID);

        MethodTemplate construct = ensureMethodTemplate("construct",
                new String[]{"Int64", "String"});
        MethodTemplate ftFinally = ensureMethodTemplate("finally",
                new String[]{"Int64", "String"});

        construct.m_aop = new Op[]
            { // #0 = i; #1 = s
            new X_Print(-adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
            new LSet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 0),
            new Construct_1(adapter.getMethodConstId("TestApp.TestClass", "construct"), 1),
            new Return_0(),
            };
        construct.m_cVars = 2;
        construct.m_mtFinally = ftFinally;

        ftFinally.m_aop = new Op[]
            { // #0 = i; #1 = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass2 #")),
            new X_Print(0),
            new X_Print(1),
            new Return_0(),
            };
        ftFinally.m_cVars = 2;

        MethodTemplate mtMethod1 = ensureMethodTemplate("method1", VOID);
        mtMethod1.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestClass2.method1() #")),
            new Var(adapter.getClassTypeConstId("Int64")), // #0
            new Call_01(Op.A_SUPER, 0),
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new LGet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 1),
            new Add(0, 1, 0),
            new Return_1(0),
            };
        mtMethod1.m_cVars = 2;

        MethodTemplate mtTo = ensureMethodTemplate("to", VOID, STRING);
        mtTo.m_aop = new Op[]
            {
            new Var(adapter.getClassTypeConstId("String")), // #0
            new Call_01(Op.A_SUPER, 0),
            new Add(0, -adapter.ensureValueConstantId(", prop2="), 0),
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new LGet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 1),
            new Var(adapter.getClassTypeConstId("String")), // #2
            new Invoke_01(1, adapter.getMethodConstId("Object", "to"), 2),
            new Add(0, 2, 0),
            new Return_1(0),
            };
        mtTo.m_cVars = 3;

        }
    }
