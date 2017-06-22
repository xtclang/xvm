package org.xvm.proto.template;

import org.xvm.asm.Constants;
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
        super(types, "x:TestService", "x:Service", Shape.Service);

        adapter = types.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        m_fAutoRegister = true;

        f_types.ensureTemplate("x:Clock");

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

        FunctionTemplate ftDefault = ensureDefaultConstructTemplate(); // "default"
        ftDefault.m_aop = new Op[]
            {
            new PSet(0, adapter.getPropertyConstId("x:TestService", "counter2"),
                    -adapter.ensureValueConstantId(5)),
            new Return_0(),
            };
        ftDefault.m_cVars = 1;

        PropertyTemplate ptClock = ensurePropertyTemplate("runtimeClock", "x:Clock");
        ptClock.markInjectable();
        ptClock.setGetAccess(Constants.Access.PRIVATE);
        ptClock.setSetAccess(Constants.Access.PRIVATE);

        ConstructTemplate constructor = ensureConstructTemplate( // "construct"
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
            new Var(adapter.getClassTypeConstId("x:Int64")), // #1
            new PreInc(-adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_1(1),
            };
        mtIncrement.m_cVars = 3;

        FunctionTemplate ftLambda$1 = ensureFunctionTemplate("lambda$1", new String[] {"x:Ref<x:Int64>", "x:Int64"}, VOID);
        ftLambda$1.setAccess(Constants.Access.PRIVATE);
        ftLambda$1.m_aop = new Op[]
            { // #0 = &iRet, #1 = cDelay
            new Invoke_10(0, adapter.getMethodConstId("x:Ref", "set"), 1),
            new Return_0()
            };

        MethodTemplate mtExceptional = ensureMethodTemplate("exceptional", INT, INT);
        mtExceptional.m_aop = new Op[]
            { // #1 - cDelay
            new Enter(),
            new Var(adapter.getClassTypeConstId("x:Boolean")), // #2
            new IsZero(1, 2),
            new JumpFalse(2, 4), // -> Exit

            new Var(this.adapter.getClassTypeConstId("x:Exception")), // #3
                    new New_N(adapter.getMethodConstId("x:Exception", "construct"),
                    new int[]{-adapter.ensureValueConstantId("test"),
                            -adapter.getClassTypeConstId("x:Nullable$Null")}, 3),
            new Throw(3),
            new Exit(),     // should be optimized out (after throw)

            new Enter(),
            new DNVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                    adapter.ensureValueConstantId("iRet")), // #2 (iRet)
            new Var(adapter.getClassTypeConstId("x:Clock")), // #3
            new LGet(adapter.getPropertyConstId("x:TestService", "runtimeClock"), 3),
            new IVar(adapter.getClassTypeConstId("x:Function"),
                    -adapter.getMethodConstId("x:TestService", "lambda$1")), // #4
            new Ref(2), // #5
            new FBind(4, new int[] {0, 1}, new int[] {5, 1}, 4),
            new Invoke_N0(3, adapter.getMethodConstId("x:Clock", "scheduleAlarm"),
                    new int[] {4, 1}),
            new Return_1(2),
            new Exit(),     // should be optimized out (after return)
            };
        mtExceptional.m_cVars = 6;
        mtExceptional.m_cScopes = 2;
        }
    }
