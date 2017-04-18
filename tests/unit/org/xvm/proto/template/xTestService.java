package org.xvm.proto.template;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;

import org.xvm.proto.op.*;

/**
 * A test service.
 *
 * @author gg 2017.03.15
 */
public class xTestService extends xService
    {
    private final ConstantPoolAdapter adapter;

    public xTestService(TypeSet types)
        {
        super(types, "x:TestService", "x:Object", Shape.Service);

        adapter = types.f_constantPool;

        addImplement("x:Service");
        }

    @Override
    public void initDeclared()
        {
        PropertyTemplate ptCounter = ensurePropertyTemplate("counter", "x:Int64");
        MethodTemplate mtCounter = ptCounter.addGet();
        mtCounter.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.counter.get #")),
            new Var(adapter.getClassConstId("x:Int64")), // (#1)
            new Call_01(Op.A_SUPER, 1),
            new Return_1(1),
            };
        mtCounter.m_cVars = 2;

        FunctionTemplate ftConstructor = ensureFunctionTemplate(
                "construct", new String[]{"x:TestService", "x:Int64"}, VOID);
        ftConstructor.m_aop = new Op[]
            {
            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_0(),
            };
        ftConstructor.m_cVars = 2;

        MethodTemplate mtIncrement = ensureMethodTemplate("increment", VOID, INT);
        mtIncrement.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.increment #")),
            new Var(adapter.getClassConstId("x:Int64")), // (#1)
            new Get(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new IVar(adapter.getClassConstId("x:Int64"), adapter.ensureValueConstantId(1)), // (#2)
            new Add(1, 2, 1),
            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_1(1),
            };
        mtIncrement.m_cVars = 3;

        MethodTemplate mtThrowing = ensureMethodTemplate("throwing", VOID, INT);
        mtThrowing.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.throwing #")),
            new Var(this.adapter.getClassConstId("x:Exception")), // #1
            new New_N(this.adapter.getMethodConstId("x:Exception", "construct"),
                    new int[]{-adapter.ensureValueConstantId("test"),
                            -adapter.getClassConstId("x:Nullable$Null")}, 1),
            new Throw(1),
            };
        mtThrowing.m_cVars = 2;
        }
    }
