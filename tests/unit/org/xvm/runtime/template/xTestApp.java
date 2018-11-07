package org.xvm.runtime.template;


import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;

/**
 * A test class.
 */
public class xTestApp extends xModule
    {
    private final Adapter adapter;

    public xTestApp(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        adapter = templates.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        // --- getIntValue - compiled!
        // --- getStringValue - compiled!
        // --- test2()

        MethodStructure ftTest2 = getMethodStructure("test2", VOID);
        ftTest2.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.test2() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestClass"),
                adapter.ensureValueConstantId("t")))  // #0 (t)
            .add(new New_1(adapter.getMethodConstId("TestApp.TestClass", "construct"),
                adapter.ensureValueConstantId("Hello World!"), 0))
            .add(new X_Print(0))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0, 1)) // next register #1
            .add(new X_Print(1))
            .add(new Invoke_01(0, adapter.getMethodConstId("TestApp.TestClass", "method1"),
                2)) // next register #2
            .add(new X_Print(2))

            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                adapter.ensureValueConstantId("e"), +3))
            .add(new Invoke_10(0,
                    adapter.getMethodConstId("TestApp.TestClass", "exceptional"),
                    adapter.ensureValueConstantId("handled")))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #3 (e)
            .add(new X_Print(3))
            .add(new CatchEnd(1))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestClass"),
                adapter.ensureValueConstantId("t2"))) // #3 (t2)
            .add(new New_N(adapter.getMethodConstId("TestApp.TestClass2", "construct"),
                new int[]{
                    adapter.ensureValueConstantId(42),
                    adapter.ensureValueConstantId("Goodbye")
                }, 3))
            .add(new X_Print(3))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 3,
                4)) // next register #4
            .add(new X_Print(4))
            .add(new Invoke_01(3, adapter.getMethodConstId("TestApp.TestClass", "method1"),
                5)) // next register #5
            .add(new X_Print(5))

            .add(new Var_N(adapter.getClassTypeConstId("Function"),
                adapter.ensureValueConstantId("fn"))) // #6 (fn)
            .add(new MBind(3, adapter.getMethodConstId("TestApp.TestClass", "method1"), 6))
            .add(new Call_01(6, 7)) // next register #7
            .add(new X_Print(7))

            .add(new Return_0());

        // --- testService()

        MethodStructure ftLambda$1 = getMethodStructure("lambda_1",
            new String[]{"Int64", "Int64", "Exception"});
        ftLambda$1.createCode()
            // #0 = c; #1 = r, #2 = x
            .add(new X_Print(adapter.ensureValueConstantId(
                "\n# in TestApp.lambda_1 (rfc2.whenComplete) #")))
            .add(new X_Print(0))
            .add(new X_Print(1))
            .add(new X_Print(2))
            .add(new Return_0());

        MethodStructure ftTestService = getMethodStructure("testService", VOID);
        ftTestService.createCode()
            .add(new X_Print(
                adapter.ensureValueConstantId("\n# in TestApp.testService() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestService"),
                adapter.ensureValueConstantId("svc")))     // #0
            .add(new New_1(adapter.getMethodConstId("TestApp.TestService", "construct"),
                adapter.ensureValueConstantId(48), 0))
            .add(new X_Print(0))

            .add(new Invoke_00(0,
                adapter.getMethodConstId("TestApp.TestService", "testConstant")))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("c")))        // #1 (c)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 1))

            .add(new IP_Add(1, adapter.ensureValueConstantId(47)))
            .add(new IP_Div(1, adapter.ensureValueConstantId(2)))
            .add(new IsEq(1, adapter.ensureValueConstantId(48), Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("counter == 48")))

            .add(new P_Set(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0,
                adapter.ensureValueConstantId(17)))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0, 1))

            .add(new IsEq(1, adapter.ensureValueConstantId(17), Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("c == 17")))

            .add(new Var_N(adapter.getClassTypeConstId("Function"),
                adapter.ensureValueConstantId("fnInc")))   // #2 (fnInc)
            .add(new MBind(0, adapter.getMethodConstId("TestApp.TestService", "increment"),
                2))
            .add(new Call_01(2, 1))
            .add(new X_Print(1))

            .add(new Var_DN(adapter.getClassTypeConstId("@annotations.FutureVar Var<Int64>"),
                adapter.ensureValueConstantId("fc"))) // #3 (fc)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 3))
            .add(new Var_N(adapter.getClassTypeConstId("annotations.FutureVar<Int64>"),
                adapter.ensureValueConstantId("rfc"))) // #4 (rfc)
            .add(new MoveRef(3, 4))
            .add(new X_Print(4))
            .add(new X_Print(3))
            .add(new X_Print(4))

            .add(new Var_N(adapter.getClassTypeConstId("annotations.FutureVar<Int64>"),
                adapter.ensureValueConstantId("rfc2"))) // #5 (rfc2)
            .add(new Var_DN(adapter.getClassTypeConstId("@annotations.FutureVar Var<Int64>"),
                adapter.ensureValueConstantId("fc2"))) // #6 (fc2)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 6))
            .add(new MoveRef(6, 5))

            .add(new Var_I(adapter.getClassTypeConstId("Function"),
                adapter.getMethodConstId("TestApp", "lambda_1"))) // #7
            .add(new FBind(7, new int[]{0}, new int[]{1}, 7))
            .add(new Invoke_10(5,
                adapter.getMethodConstId("annotations.FutureVar", "whenComplete"),
                7))

            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                adapter.ensureValueConstantId("e"), +3))
            .add(new Invoke_11(0,
                adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                adapter.ensureValueConstantId(0), 1))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #8 (e)
            .add(new X_Print(8))
            .add(new CatchEnd(1))

            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                adapter.ensureValueConstantId("e"), +3))
            .add(new Invoke_10(4, adapter.getMethodConstId("annotations.FutureVar", "set"),
                adapter.ensureValueConstantId(99)))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #8 (e)
            .add(new X_Print(8))
            .add(new CatchEnd(1))

            .add(new PIP_PreInc(adapter.getPropertyConstId("TestApp.TestService", "counter2"), 0,
                8))  // next register #8
            .add(new IsEq(8, adapter.ensureValueConstantId(6), Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("++counter2 == 6")))

            .add(new PIP_PostInc(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0, 8))
            .add(new IsEq(8, adapter.ensureValueConstantId(20), Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("counter++ == 20")))

            .add(new Invoke_01(0, adapter.getMethodConstId("TestApp.TestService", "increment"), 8))
            .add(new IsEq(8, adapter.ensureValueConstantId(22), Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("svc.increment() == 22")))

            .add(new P_Get(adapter.getPropertyConstId("Ref", "RefType"), 4, 9)) // next register #9
            .add(new X_Print(9))

            .add(new Invoke_00(Op.A_SERVICE, adapter.getMethodConstId("Service", "yield")))

            .add(new P_Get(adapter.getPropertyConstId("Service", "serviceName"),
                0, 10)) // next register #10
            .add(new X_Print(10))

            .add(new Invoke_10(0,
                adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                adapter.ensureValueConstantId(0)))
            .add(new Return_0());

        // --- testService2 ---

        MethodStructure ftTestService2 = getMethodStructure("testService2", VOID);
        ftTestService2.createCode()
            .add(new X_Print(
                    adapter.ensureValueConstantId("\n# in TestApp.testService2() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestService"),
                    adapter.ensureValueConstantId("svc")))     // #0
            .add(new New_1(adapter.getMethodConstId("TestApp.TestService", "construct"),
                    adapter.ensureValueConstantId(48), 0))

            .add(new Var_I(adapter.getClassTypeConstId("Function"),
                     adapter.getMethodConstId("TestApp", "testBlockingReturn"))) // #1
            .add(new FBind(1, new int[] {0}, new int[] {0}, 1))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("c"))) // #2
            .add(new Call_01(1, 2))
            .add(new X_Print(2))

            .add(new Var_DN(adapter.getClassTypeConstId("@annotations.FutureVar Var<Int64>"),
                     adapter.ensureValueConstantId("fc"))) // #3 (fc)
            .add(new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 3))
            .add(new X_Print(3))

            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                adapter.ensureValueConstantId("e"), +3))
            .add(new Call_01(adapter.getMethodConstId("TestApp", "getIntValue"), 3))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #4 (e)
            .add(new X_Print(4))
            .add(new CatchEnd(1))

            .add(new Invoke_10(Op.A_SERVICE,
                adapter.getMethodConstId("Service", "registerTimeout"),
                adapter.ensureValueConstantId(2000)))
            .add(new GuardAll(+4))
            .add(new Invoke_11(0,
                adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                adapter.ensureValueConstantId(1000), 2))
            .add(new X_Print(2))
            .add(new FinallyStart())
            .add(new Invoke_10(Op.A_SERVICE,
                adapter.getMethodConstId("Service", "registerTimeout"),
                adapter.ensureValueConstantId(0)))
            .add(new FinallyEnd())

            .add(new Invoke_10(Op.A_SERVICE,
                adapter.getMethodConstId("Service", "registerTimeout"),
                adapter.ensureValueConstantId(500)))
            .add(new GuardAll(+9))
            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                adapter.ensureValueConstantId("e"), +4))
            .add(new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(200000), 2))
            .add(new Assert(adapter.f_pool.valFalse()))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #4 (e)
            .add(new X_Print(4))
            .add(new CatchEnd(1))
            .add(new FinallyStart())
            .add(new Invoke_10(Op.A_SERVICE,
                adapter.getMethodConstId("Service", "registerTimeout"),
                adapter.ensureValueConstantId(0)))
            .add(new FinallyEnd())
            .add(new Return_0());

        // --- testRef()

        MethodStructure ftTestRef = getMethodStructure("testRef", STRING);
        ftTestRef.createCode()
            // #0 = arg
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testRef() #")))

            .add(new Var_N(adapter.getClassTypeConstId("Var<String>"),
                adapter.ensureValueConstantId("ra"))) // #1 (ra)
            .add(new MoveVar(0, 1))

            .add(new Invoke_01(1, adapter.getMethodConstId("Ref", "get"), 2)) // next register #2
            .add(new X_Print(2))
            .add(new Invoke_10(1, adapter.getMethodConstId("Var", "set"),
                adapter.ensureValueConstantId("bye")))
            .add(new X_Print(0))

            .add(new P_Get(adapter.getPropertyConstId("Referent", "const_"), 1, Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("ra.const_")))

            .add(new Var_N(adapter.getClassTypeConstId("Var<Int64>"),
                adapter.ensureValueConstantId("ri"))) // #3 (ri)
            .add(new Enter())
            .add(new Var_IN(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("i"),
                adapter.ensureValueConstantId(1))) // #4 (i)
            .add(new Var_N(adapter.getClassTypeConstId("Var<Int64>"),
                adapter.ensureValueConstantId("ri2"))) // #5 (ri2)
            .add(new MoveVar(4, 5))
            .add(new MoveVar(4, 3))

            .add(new IsEq(3, 5, Op.A_STACK))
            .add(new AssertM(Op.A_STACK, adapter.ensureValueConstantId("ri != ri2")))

            .add(new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 6)) // next register #6
            .add(new X_Print(6))

            .add(new Invoke_10(3, adapter.getMethodConstId("Var", "set"),
                adapter.ensureValueConstantId(2)))
            .add(new X_Print(4))

            .add(new Move(adapter.ensureValueConstantId(3), 4))
            .add(new X_Print(5))
            .add(new Exit())

            .add(new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 4)) // next register #4
            .add(new X_Print(4))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestClass"),
                adapter.ensureValueConstantId("tc")))  // #5 (tc)
            .add(new New_1(adapter.getMethodConstId("TestApp.TestClass", "construct"),
                adapter.ensureValueConstantId("before"), 5))

            .add(new Var_N(adapter.getClassTypeConstId("Var<String>"),
                adapter.ensureValueConstantId("rp")))  // #6 (rp)
            .add(new P_Var(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 5, 6))
            .add(new X_Print(6))

            .add(new Invoke_10(6, adapter.getMethodConstId("Var", "set"),
                adapter.ensureValueConstantId("after")))
            .add(new X_Print(5))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestService"),
                adapter.ensureValueConstantId("svc")))  // #7 (svc)
            .add(new New_1(adapter.getMethodConstId("TestApp.TestService", "construct"),
                adapter.ensureValueConstantId(48), 7))

            .add(new P_Var(adapter.getPropertyConstId("TestApp.TestService", "counter"), 7, 3))

            .add(new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 8)) // next register #8
            .add(new X_Print(8))

            .add(new Var_N(adapter.getClassTypeConstId("@annotations.AtomicVar Var<Int64>"),
                adapter.ensureValueConstantId("ari")))  // #9 (ari)
            .add(new P_Var(adapter.getPropertyConstId("TestApp.TestService", "counter2"), 7, 9))
            .add(new X_Print(9))

            .add(new Invoke_N0(9, adapter.getMethodConstId("annotations.AtomicVar", "replace"),
                new int[] {adapter.ensureValueConstantId(5), adapter.ensureValueConstantId(6)}))
            .add(new X_Print(9))

            .add(new Return_0());

        // ----- testTuple()

        MethodStructure ftTestTuple = getMethodStructure("testTuple", VOID);
        ftTestTuple.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testTuple() #")))
            .add(new Var_IN(adapter.getClassTypeConstId("collections.Tuple<String,Int64>"),
                adapter.ensureValueConstantId("t"),
                adapter.ensureValueConstantId(
                    new Object[]{"zero", Integer.valueOf(0)}))) // #0 (t)

            .add(new Enter())
            .add(new I_Get(0, adapter.ensureValueConstantId(0), 1)) // next register #1
            .add(new X_Print(1))
            .add(new Exit())

            .add(new Enter())
            .add(new I_Get(0, adapter.ensureValueConstantId(1), 1)) // next register #
            .add(new X_Print(1))
            .add(new Exit())

            .add(new Var_IN(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("i"),
                adapter.ensureValueConstantId(0))) // #1 (i)
            .add(new Var_N(adapter.getClassTypeConstId("collections.Tuple<String,Int64>"),
                adapter.ensureValueConstantId("t2"))) // #2 (t2)
            .add(new Var_T(adapter.getClassTypeConstId("String;Int64"),
                new int[]{adapter.ensureValueConstantId(""), 1})) // #3
            .add(new NewG_1(adapter.getMethodConstId("collections.Tuple", "construct"),
                adapter.getClassTypeConstId("collections.Tuple<String,Int64>"), 3,
                2))
            .add(new X_Print(2))

            .add(new I_Set(2, adapter.ensureValueConstantId(0),
                adapter.ensureValueConstantId("t")))
            .add(new I_Set(2, adapter.ensureValueConstantId(1),
                    adapter.ensureValueConstantId(2)))
            .add(new X_Print(2))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("of"))) // #4 (of)
            .add(new Invoke_T1(adapter.ensureValueConstantId("the test"),
                adapter.getMethodConstId("String", "indexOf"), 2, 4))

            .add(new IsEq(4, adapter.ensureValueConstantId(4), 5)) // next register #5
            .add(new AssertV(5, adapter.ensureValueConstantId("of == 4"), new int[]{4}))

            .add(new Var_N(adapter.getClassTypeConstId("String"),
                adapter.ensureValueConstantId("s"))) // #6
            .add(new Call_1N(adapter.getMethodConstId("TestApp", "testConditional"),
                adapter.ensureValueConstantId(1), new int[]{7, 6})) // next register #7
            .add(new JumpFalse(7, 1))
            .add(new X_Print(6))

            .add(new Call_1T(adapter.getMethodConstId("TestApp", "testConditional"),
                adapter.ensureValueConstantId(-1), 8)) // next register #8
            .add(new X_Print(8))

            .add(new Return_0());

        // ----- testConst()

        ClassTemplate ctPoint = f_templates.getTemplate("TestApp.Point");
        MethodStructure mtConst = ctPoint.getMethodStructure("construct",
            new String[]{"Int64", "Int64"});
        mtConst.createCode()
            // #0 = x; #1 = y
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Point", "x"), 0))
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Point", "y"), 1))
            .add(new Return_0());


        ClassTemplate ctRectangle = f_templates.getTemplate("TestApp.Rectangle");
        MethodStructure mtRectangle = ctRectangle.getMethodStructure("construct",
            new String[]{"TestApp.Point", "TestApp.Point"});
        mtRectangle.createCode()
            // #0 = tl; #1 = br
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Rectangle", "topLeft"), 0))
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Rectangle", "bottomRight"), 1))
            .add(new Return_0());

        ClassTemplate ctFormatter = f_templates.getTemplate("TestApp.Formatter");
        MethodStructure mtFormatter = ctFormatter.getMethodStructure("construct", STRING);
        mtFormatter.createCode()
            // #0 = prefix
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Formatter", "prefix"), 0))
            .add(new Return_0());
        }
    }
