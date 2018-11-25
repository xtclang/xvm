package org.xvm.runtime.template.TestApp;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

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

        adapter = templates.f_adapter;
        }

    @Override
    public void initDeclared()
        {
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
            new Var_DN(adapter.getClassTypeConstId("@annotations.FutureVar Var<Int64>"),
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
        }
    }
