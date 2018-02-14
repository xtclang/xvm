package org.xvm.runtime.template.TestApp;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xService;


/**
 * A test service.
 */
public class xTestService extends xService
    {
    private final Adapter adapter;

    public xTestService(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        adapter = templates.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        adapter.addMethod(f_struct, "construct", INT, VOID);

        markInjectable("runtimeClock");
        markAtomic("counter2");

        MethodStructure mtGetCounter = ensureGetter("counter");
        mtGetCounter.setOps(new Op[]
            {
            new X_Print(
                adapter.ensureValueConstantId("# in TestService.counter.get #")),
            new Call_01(Op.A_SUPER, 0), // next register #0
            new Return_1(0),
            });

        MethodStructure mtSetCounter = ensureSetter("counter");
        mtSetCounter.setOps(new Op[]
            { // #0 = newValue
            new X_Print(
                    adapter.ensureValueConstantId("# in TestService.counter.set #")),
            new X_Print(0),
            new Call_10(Op.A_SUPER, 0),
            new Invoke_01(Op.A_THIS, adapter.getMethodConstId("Object", "to"), 1), // next register #1
            new X_Print(1),
            new Return_0(),
            });

        MethodStructure constructor = getMethodStructure("construct", INT);
        constructor.setOps(new Op[]
            { // #0 - counter
            new L_Set(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0),
            new Return_0(),
            });

        MethodStructure mtIncrement = getMethodStructure("increment", VOID, INT);
        mtIncrement.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestService.increment #")),
            new IP_PreInc(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0), // next register #0
            new Return_1(0),
            });

        MethodStructure mtTestConst = getMethodStructure("testConstant", VOID, VOID);
        mtTestConst.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestService.testConstant #")),
            new Var_IN(adapter.getClassType("TestApp.Point", this),
                (StringConstant) adapter.ensureValueConstant("origin"),
                adapter.getSingletonConstant("TestPackage.Origin")),  // #0
            new X_Print(0),
            new Return_0(),
            });

        MethodStructure ftLambda$1 = getMethodStructure("lambda_1",
            new String[]{"Var<Int64>", "Int64"});
        ftLambda$1.setOps(new Op[]
            { // #0 = &iRet, #1 = cDelay
            new Invoke_10(0, adapter.getMethodConstId("Var", "set"), 1),
            new Return_0()
            });

        MethodStructure mtExceptional = getMethodStructure("exceptional", INT, INT);
        mtExceptional.setOps(new Op[]
            { // #0 - cDelay
            new IsZero(0, 1), // next register #1
            new JumpFalse(1, 6), // -> Enter

            new Enter(),
            new New_N(adapter.getMethodConstId("Exception", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId("test"),
                            adapter.ensureValueConstantId(null)
                    }, 2), // next register #2
            new Throw(2),
            new Exit(), // optimize out; unreachable

            new Enter(),
            new Var_DN(adapter.getClassTypeConstId("annotations.FutureVar<Int64>"),
                     adapter.ensureValueConstantId("iRet")), // #2 (iRet)

            new Var_I(adapter.getClassTypeConstId("Function"),
                    adapter.getMethodConstId("TestApp.TestService", "lambda_1")), // #3
            new MoveRef(2, 4), // next register #4 (&iRet)
            new FBind(3, new int[] {0, 1}, new int[] {4, 0}, 3),
            new Invoke_N0(adapter.getPropertyConstId("TestApp.TestService", "runtimeClock"),
                    adapter.getMethodConstId("Clock", "scheduleAlarm"),
                    new int[] {3, 0}),
            new Return_1(2),
            new Exit(), // optimized out; unreachable
            });

        MethodStructure mtTo = getMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId(
                "# in TestService.to<String>() #")),
            new Call_01(Op.A_SUPER, 0), // next register #0
            new GP_Add(0, adapter.ensureValueConstantId(": counter2="), 0),
            new Invoke_01(adapter.getPropertyConstId("TestApp.TestService", "counter2"),
                adapter.getMethodConstId("Object", "to"), 1), // next register #1
            new GP_Add(0, 1, 0),
            new Return_1(0),
            });
        }
    }
