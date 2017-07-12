package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.PropertyStructure;
import org.xvm.proto.Adapter;
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
    private final Adapter adapter;

    public xTestService(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        adapter = types.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        adapter.addMethod(f_struct, "construct", INT, VOID);
        adapter.addMethod(f_struct, "default", VOID, VOID);
        adapter.markInjectable(((PropertyStructure) f_struct.getChild("runtimeClock")));

        MethodTemplate mtGetCounter = ensureGetter("counter");
        mtGetCounter.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.counter.get #")),
            new Var(adapter.getClassTypeConstId("Int64")), // (#0)
            new Call_01(Op.A_SUPER, 0),
            new Return_1(0),
            };
        mtGetCounter.m_cVars = 1;

        MethodTemplate mtSetCounter = ensureSetter("counter");
        mtSetCounter.m_aop = new Op[]
            { // #0 = newValue
            new X_Print(-adapter.ensureValueConstantId("# in TestService.counter.set #")),
            new X_Print(0),
            new Call_10(Op.A_SUPER, 0),
            new Return_0(),
            };
        mtSetCounter.m_cVars = 1;

        MethodTemplate ftDefault = getMethodTemplate("default", VOID, VOID);
        ftDefault.m_aop = new Op[]
            {
            new LSet(adapter.getPropertyConstId("TestApp.TestService", "counter2"),
                    -adapter.ensureValueConstantId(5)),
            new Return_0(),
            };
        ftDefault.m_cVars = 1;

        MethodTemplate constructor = getMethodTemplate("construct", INT);
        constructor.m_aop = new Op[]
            { // #0 - counter
            new LSet(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0),
            new Return_0(),
            };
        constructor.m_cVars = 2;

        MethodTemplate mtIncrement = getMethodTemplate("increment", VOID, INT);
        mtIncrement.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("# in TestService.increment #")),
            new Var(adapter.getClassTypeConstId("Int64")), // #0
            new PreInc(-adapter.getPropertyConstId("TestApp.TestService", "counter"), 0),
            new Return_1(0),
            };
        mtIncrement.m_cVars = 1;

        MethodTemplate ftLambda$1 = getMethodTemplate("lambda_1", new String[]{"Ref<Int64>", "Int64"});
        ftLambda$1.m_aop = new Op[]
            { // #0 = &iRet, #1 = cDelay
            new Invoke_10(0, adapter.getMethodConstId("Ref", "set"), 1),
            new Return_0()
            };
        ftLambda$1.m_cVars = 2;

        MethodTemplate mtExceptional = getMethodTemplate("exceptional", INT, INT);
        mtExceptional.m_aop = new Op[]
            { // #0 - cDelay
            new Var(adapter.getClassTypeConstId("Boolean")), // #1
            new IsZero(0, 1),
            new JumpFalse(1, 6), // -> Enter

            new Enter(),
            new Var(this.adapter.getClassTypeConstId("Exception")), // #2
            new New_N(adapter.getMethodConstId("Exception", "construct"),
                        new int[]{-adapter.ensureValueConstantId("test"),
                                  -adapter.getClassTypeConstId("Nullable.Null")}, 2),
            new Throw(2),
            new Exit(), // optimize out; unreachable

            new Enter(),
            new DNVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                    adapter.ensureValueConstantId("iRet")), // #2 (iRet)
            new Var(adapter.getClassTypeConstId("Clock")), // #3
            new LGet(adapter.getPropertyConstId("TestApp.TestService", "runtimeClock"), 3),
            new IVar(adapter.getClassTypeConstId("Function"),
                    -adapter.getMethodConstId("TestApp.TestService", "lambda_1")), // #4
            new Ref(2), // #5
            new FBind(4, new int[] {0, 1}, new int[] {5, 0}, 4),
            new Invoke_N0(3, adapter.getMethodConstId("Clock", "scheduleAlarm"),
                    new int[] {4, 0}),
            new Return_1(2),
            new Exit(), // optimized out; unreachable
            };
        mtExceptional.m_cVars = 6;
        mtExceptional.m_cScopes = 2;
        }
    }
