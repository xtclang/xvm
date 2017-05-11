package org.xvm.proto.template;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTestClass2 extends TypeCompositionTemplate
    {
    private final ConstantPoolAdapter adapter;

    public xTestClass2(TypeSet types)
        {
        super(types, "x:TestClass2", "x:TestClass", Shape.Class);

        adapter = types.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        m_fAutoRegister = true;

        ensurePropertyTemplate("prop2", "x:Int");

        ConstructTemplate construct = ensureConstructTemplate(new String[]{"x:TestClass2", "x:Int64", "x:String"});
        FunctionTemplate ftFinally = ensureFunctionTemplate(
                "finally", new String[]{"x:TestClass2", "x:Int64", "x:String"}, VOID);

        construct.m_aop = new Op[]
            { // #0 = this:struct; #1 = i; #2 = s
            new X_Print(-adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
            new PSet(0, adapter.getPropertyConstId("x:TestClass2", "prop2"), 1),
            new Construct_1(adapter.getMethodConstId("x:TestClass", "construct"), 2),
            new Return_0(),
            };
        construct.m_cVars = 3;
        construct.setFinally(ftFinally);

        ftFinally.m_aop = new Op[]
            { // #0 = this:private; #1 = i; #2 = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass2 #")),
            new X_Print(1),
            new Return_0(),
            };
        ftFinally.m_cVars = 3;

        MethodTemplate mtMethod1 = ensureMethodTemplate("method1", VOID, INT);
        mtMethod1.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestClass2.method1() #")),
            new Var(adapter.getClassTypeConstId("x:Int64")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Var(adapter.getClassTypeConstId("x:Int64")), // #2
            new LGet(adapter.getPropertyConstId("x:TestClass2", "prop2"), 2),
            new Add(1, 2, 1),
            new Return_1(1),
            };
        mtMethod1.m_cVars = 3;
        }
    }
