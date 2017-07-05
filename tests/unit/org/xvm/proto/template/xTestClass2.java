package org.xvm.proto.template;

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
        adapter.addMethod(f_struct, "construct", new String[]{"TestApp.TestClass2", "Int64", "String"}, VOID);
        adapter.addMethod(f_struct, "finally", new String[]{"TestApp.TestClass2", "Int64", "String"}, VOID);

        MethodTemplate construct = getMethodTemplate("construct",
                new String[]{"TestApp.TestClass2", "Int64", "String"});
        MethodTemplate ftFinally = getMethodTemplate("finally",
                new String[]{"TestApp.TestClass2", "Int64", "String"});

        construct.m_aop = new Op[]
            { // #0 = this:struct; #1 = i; #2 = s
            new X_Print(-adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
            new PSet(0, adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 1),
            new Construct_1(adapter.getMethodConstId("TestApp.TestClass", "construct"), 2),
            new Return_0(),
            };
        construct.m_cVars = 3;
        construct.m_mtFinally = ftFinally;

        ftFinally.m_aop = new Op[]
            { // #0 = this:private; #1 = i; #2 = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass2 #")),
            new X_Print(1),
            new Return_0(),
            };
        ftFinally.m_cVars = 3;

        MethodTemplate mtMethod1 = getMethodTemplate("method1", VOID);
        mtMethod1.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestClass2.method1() #")),
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Var(adapter.getClassTypeConstId("Int64")), // #2
            new LGet(adapter.getPropertyConstId("TestApp.TestClass2", "prop2"), 2),
            new Add(1, 2, 1),
            new Return_1(1),
            };
        mtMethod1.m_cVars = 3;
        }
    }
