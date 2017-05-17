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

        adapter = types.f_adapter;

        addImplement("x:Service");
        }

    @Override
    public void initDeclared()
        {
        m_fAutoRegister = true;

        PropertyTemplate ptCounter = ensurePropertyTemplate("counter", "x:Int64");
        MethodTemplate mtGetCounter = ptCounter.addGet();
        mtGetCounter.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.counter.get #")),
            new Var(adapter.getClassTypeConstId("x:Int64")), // (#1)
            new Call_01(Op.A_SUPER, 1),
            new Return_1(1),
            };
        mtGetCounter.m_cVars = 2;

        MethodTemplate mtSetCounter = ptCounter.addSet();
        mtSetCounter.m_aop = new Op[]
            { // #0 = this; #1 = newValue
            new X_Print(-adapter.ensureValueConstantId("# in TestService.counter.set #")),
            new X_Print(1),
            new Call_10(Op.A_SUPER, 1),
            new Return_0(),
            };
        mtSetCounter.m_cVars = 2;

        PropertyTemplate ptCounter2 = ensurePropertyTemplate("counter2", "x:Int64");
        ptCounter2.makeAtomicRef();

        FunctionTemplate ftDefault = ensureDefaultConstructTemplate();
        ftDefault.m_aop = new Op[]
            {
            new LSet(adapter.getPropertyConstId("x:TestService", "counter2"),
                    -adapter.ensureValueConstantId(5)),
            new Return_0(),
            };
        ftDefault.m_cVars = 1;

        ConstructTemplate constructor = ensureConstructTemplate(
                new String[]{"x:TestService", "x:Int64"});
        constructor.m_aop = new Op[]
            {
            new PSet(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_0(),
            };
        constructor.m_cVars = 2;

        MethodTemplate mtIncrement = ensureMethodTemplate("increment", VOID, INT);
        mtIncrement.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.increment #")),
            new Var(adapter.getClassTypeConstId("x:Int64")), // (#1)
            new PreInc(-adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_1(1),
            };
        mtIncrement.m_cVars = 3;

        MethodTemplate mtThrowing = ensureMethodTemplate("throwing", VOID, INT);
        mtThrowing.m_aop = new Op[]
            {
            new Var(this.adapter.getClassTypeConstId("x:Exception")), // #1
            new New_N(this.adapter.getMethodConstId("x:Exception", "construct"),
                    new int[]{-adapter.ensureValueConstantId("test"),
                            -adapter.getClassTypeConstId("x:Nullable$Null")}, 1),
            new Throw(1),
            };
        mtThrowing.m_cVars = 2;
        }
    }
