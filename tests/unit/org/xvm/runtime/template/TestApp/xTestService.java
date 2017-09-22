package org.xvm.runtime.template.TestApp;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.Service;


/**
 * A test service.
 *
 * @author gg 2017.03.15
 */
public class xTestService extends Service
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

        markInjectable("runtimeClock");
        markAtomicRef("counter2");

        MethodStructure mtGetCounter = ensureGetter("counter");
        mtGetCounter.setOps(new Op[]
                {
                        new X_Print(
                                -adapter.ensureValueConstantId("# in TestService.counter.get #")),
                        new Var(adapter.getClassTypeConstId("Int64")), // (#0)
                        new Call_01(Op.A_SUPER, 0),
                        new Return_1(0),
                });
        mtGetCounter.setMaxVars(1);

        MethodStructure mtSetCounter = ensureSetter("counter");
        mtSetCounter.setOps(new Op[]
                { // #0 = newValue
                        new X_Print(
                                -adapter.ensureValueConstantId("# in TestService.counter.set #")),
                        new X_Print(0),
                        new Call_10(Op.A_SUPER, 0),
                        new Return_0(),
                });
        mtSetCounter.setMaxVars(1);

        MethodStructure ftDefault = ensureMethodStructure("default", VOID, VOID);
        ftDefault.setOps(new Op[]
                {
                        new LSet(adapter.getPropertyConstId("TestApp.TestService", "counter2"),
                                -adapter.ensureValueConstantId(5)),
                        new Return_0(),
                });
        ftDefault.setMaxVars(1);

        MethodStructure constructor = ensureMethodStructure("construct", INT);
        constructor.setOps(new Op[]
                { // #0 - counter
                        new LSet(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0),
                        new Return_0(),
                });
        constructor.setMaxVars(2);

        MethodStructure mtIncrement = ensureMethodStructure("increment", VOID, INT);
        mtIncrement.setOps(new Op[]
                {
                        new X_Print(-adapter.ensureValueConstantId("# in TestService.increment #")),
                        new Var(adapter.getClassTypeConstId("Int64")), // #0
                        new PreInc(-adapter.getPropertyConstId("TestApp.TestService", "counter"),
                                0),
                        new Return_1(0),
                });
        mtIncrement.setMaxVars(1);

        MethodStructure ftLambda$1 = ensureMethodStructure("lambda_1",
                new String[] {"Ref<Int64>", "Int64"});
        ftLambda$1.setOps(new Op[]
                { // #0 = &iRet, #1 = cDelay
                        new Invoke_10(0, adapter.getMethodConstId("Ref", "set"), 1),
                        new Return_0()
                });
        ftLambda$1.setMaxVars(2);

        MethodStructure mtExceptional = ensureMethodStructure("exceptional", INT, INT);
        mtExceptional.setOps(new Op[]
                { // #0 - cDelay
                        new Var(adapter.getClassTypeConstId("Boolean")), // #1
                        new IsZero(0, 1),
                        new JumpFalse(1, 6), // -> Enter

                        new Enter(),
                        new Var(this.adapter.getClassTypeConstId("Exception")), // #2
                        new New_N(adapter.getMethodConstId("Exception", "construct"),
                                new int[] {
                                        -adapter.ensureValueConstantId("test"),
                                        -adapter.ensureValueConstantId(null)
                                }, 2),
                        new Throw(2),
                        new Exit(), // optimize out; unreachable

                        new Enter(),
                        new DNVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                                adapter.ensureValueConstantId("iRet")), // #2 (iRet)
                        new Var(adapter.getClassTypeConstId("Clock")), // #3
                        new LGet(adapter.getPropertyConstId("TestApp.TestService", "runtimeClock"),
                                3),
                        new IVar(adapter.getClassTypeConstId("Function"),
                                -adapter.getMethodConstId("TestApp.TestService", "lambda_1")), // #4
                        new Ref(2), // #5
                        new FBind(4, new int[] {0, 1}, new int[] {5, 0}, 4),
                        new Invoke_N0(3, adapter.getMethodConstId("Clock", "scheduleAlarm"),
                                new int[] {4, 0}),
                        new Return_1(2),
                        new Exit(), // optimized out; unreachable
                });
        mtExceptional.setMaxVars(6);
        mtExceptional.setMaxScopes(2);

        MethodStructure mtTo = ensureMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
                {
                        new X_Print(-adapter.ensureValueConstantId(
                                "\n# in TestService.to<String>() #")),
                        new Var(adapter.getClassTypeConstId("String")), // #0
                        new Call_01(Op.A_SUPER, 0),
                        new Add(0, -adapter.ensureValueConstantId(": counter2="), 0),
                        new Var(adapter.getClassTypeConstId("Int64")), // #1
                        new LGet(adapter.getPropertyConstId("TestApp.TestService", "counter2"), 1),
                        new Var(adapter.getClassTypeConstId("String")), // #2
                        new Invoke_01(1, adapter.getMethodConstId("Object", "to"), 2),
                        new Add(0, 2, 0),
                        new Return_1(0),
                });
        mtTo.setMaxVars(3);
        }
    }
