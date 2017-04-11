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

        adapter = types.f_constantPool;
        }

    @Override
    public void initDeclared()
        {
        ensurePropertyTemplate("prop2", "x:Int").setSetAccess(Access.Protected);

        FunctionTemplate ctConstruct = ensureFunctionTemplate("construct", new String[]{"x:TestClass2", "x:String"}, VOID);
        ctConstruct.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in constructor: TestClass2 #")),
            new Var(adapter.getClassConstId("x:Int64")), // #2
            new Get(1, adapter.getPropertyConstId("x:String", "length"), 2),
            new Set(0, adapter.getPropertyConstId("x:TestClass2", "prop2"), 2),
            new Call_N0(-adapter.getMethodConstId("x:TestClass", "construct"), new int[] {0, 1}),
            new Return_0(),
            };
        ctConstruct.m_cVars = 3;

        MethodTemplate mtMethod1 = ensureMethodTemplate("method1", VOID, INT);
        mtMethod1.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestClass2.method1() #")),
            new Var(adapter.getClassConstId("x:Int64")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Neg(1, 1),
            new Return_1(1),
            };
        mtMethod1.m_cVars = 2;
        }
    }
