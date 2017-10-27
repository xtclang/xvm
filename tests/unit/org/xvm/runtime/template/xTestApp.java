package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.op.*;

import org.xvm.runtime.Adapter;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeSet;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTestApp extends xModule
    {
    private final Adapter adapter;

    public xTestApp(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        adapter = types.f_container.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        f_types.getTemplate("TestApp.TestClass");
        f_types.getTemplate("TestApp.TestClass2");
        f_types.getTemplate("TestApp.TestService");

        // --- getIntValue
        MethodStructure ftGetInt = ensureMethodStructure("getIntValue", VOID, INT);
        ftGetInt.createCode()
            .add(new Return_1(adapter.ensureValueConstantId(42)));

        // --- test1()
        MethodStructure ftTest1 = ensureMethodStructure("test1", VOID, VOID);
        ftTest1.createCode()
            .add(new Var_DN(
                adapter.getClassTypeConstId("annotations.InjectedRef<io.Console>"),
                adapter.ensureValueConstantId("console"))) // #0 (console)
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.test1() #")))
            .add(new Var_IN(adapter.getClassTypeConstId("String"),
                adapter.ensureValueConstantId("s"),
                adapter.ensureValueConstantId("Hello world!"))) // #1 (s)
            .add(new Invoke_10(0, adapter.getMethodConstId("io.Console", "print"),
                adapter.ensureValueConstantId("\n***** ")))
            .add(new Invoke_10(0, adapter.getMethodConstId("io.Console", "println"), 1))
            .add(new Invoke_10(0, adapter.getMethodConstId("io.Console", "println"),
                adapter.ensureValueConstantId("")))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("i"))) // #2 (i)
            .add(new Call_01(-adapter.getMethodConstId("TestApp", "getIntValue"), 2))
            .add(new X_Print(2))

            .add(new Enter())
            .add(new Var(adapter.getClassTypeConstId("Boolean"))) // #3
            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("of"))) // #4 (of)
            .add(new Invoke_NN(1, adapter.getMethodConstId("String", "indexOf"),
                new int[]{
                    adapter.ensureValueConstantId("world"),
                    adapter.ensureValueConstantId(null)
                },
                new int[]{3, 4}))
            .add(new JumpFalse(3, 10)) // -> Exit

            .add(new P_Get(adapter.getPropertyConstId("String", "size"), 1, 5)) // next register #5
            .add(new GP_Add(5, 4, 5))
            .add(new IsEq(5, adapter.ensureValueConstantId(18), 6)) // next register #6
            .add(new Assert(6))

            .add(new Var(adapter.getClassTypeConstId("String"))) // #7
            .add(new Invoke_01(4, adapter.getMethodConstId("Int64", "to", VOID, STRING), 7))
            .add(new X_Print(7))
            .add(new Exit())

            .add(new Return_0());

        // --- test2()

        MethodStructure ftTest2 = ensureMethodStructure("test2", VOID);
        ftTest2.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.test2() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestClass"),
                adapter.ensureValueConstantId("t")))  // #0 (t)
            .add(new New_1(-adapter.getMethodConstId("TestApp.TestClass", "construct"),
                adapter.ensureValueConstantId("Hello World!"), 0))
            .add(new X_Print(0))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 0, 1)) // next register #1
            .add(new X_Print(1))
            .add(new Var(adapter.getClassTypeConstId("Int64")))    // #2
            .add(new Invoke_01(0, adapter.getMethodConstId("TestApp.TestClass", "method1"),
                2))
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
            .add(new New_N(-adapter.getMethodConstId("TestApp.TestClass2", "construct"),
                new int[]{
                    adapter.ensureValueConstantId(42),
                    adapter.ensureValueConstantId("Goodbye")
                }, 3))
            .add(new X_Print(3))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 3, 4)) // next register #4
            .add(new X_Print(4))
            .add(new Var(adapter.getClassTypeConstId("Int64")))    // #5
            .add(new Invoke_01(3, adapter.getMethodConstId("TestApp.TestClass", "method1"), 5))
            .add(new X_Print(5))

            .add(new Var_N(adapter.getClassTypeConstId("Function"),
                adapter.ensureValueConstantId("fn"))) // #6 (fn)
            .add(new MBind(3, adapter.getMethodConstId("TestApp.TestClass", "method1"), 6))
            .add(new Var(adapter.getClassTypeConstId("Int64")))    // #7
            .add(new Call_01(6, 7))
            .add(new X_Print(7))

            .add(new Return_0());

        // --- testService()

        MethodStructure ftLambda$1 = ensureMethodStructure("lambda_1",
                new String[] {"Int64", "Int64", "Exception"});
        ftLambda$1.createCode()
            // #0 = c; #1 = r, #2 = x
            .add(new X_Print(adapter.ensureValueConstantId(
                "\n# in TestApp.lambda_1 (rfc2.whenComplete) #")))
            .add(new X_Print(0))
            .add(new X_Print(1))
            .add(new X_Print(2))
            .add(new Return_0());

        MethodStructure ftTestService = ensureMethodStructure("testService", VOID);
        ftTestService.createCode()
            .add(new X_Print(
                    adapter.ensureValueConstantId("\n# in TestApp.testService() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestService"),
                adapter.ensureValueConstantId("svc")))     // #0
            .add(new New_1(-adapter.getMethodConstId("TestApp.TestService", "construct"),
                adapter.ensureValueConstantId(48), 0))
            .add(new X_Print(0))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                adapter.ensureValueConstantId("c")))        // #1 (c)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 1))
            .add(new X_Print(1))

            .add(new P_Set(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0,
                adapter.ensureValueConstantId(17)))
            .add(new P_Get(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0,
                1))
            .add(new X_Print(1))

            .add(new Var_N(adapter.getClassTypeConstId("Function"),
                adapter.ensureValueConstantId("fnInc")))   // #2 (fnInc)
            .add(new MBind(0, adapter.getMethodConstId("TestApp.TestService", "increment"),
                2))
            .add(new Call_01(2, 1))
            .add(new X_Print(1))

            .add(new Var_DN(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                adapter.ensureValueConstantId("fc"))) // #3 (fc)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 3))
            .add(new Var_N(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                adapter.ensureValueConstantId("rfc"))) // #4 (rfc)
            .add(new MoveRef(3, 4))
            .add(new X_Print(4))
            .add(new X_Print(3))
            .add(new X_Print(4))

            .add(new Var_N(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                adapter.ensureValueConstantId("rfc"))) // #5 (rfc2)
            .add(new Var_DN(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                adapter.ensureValueConstantId("rfc3"))) // #6 (rfc3)
            .add(new Invoke_01(0,
                adapter.getMethodConstId("TestApp.TestService", "increment"), 6))
            .add(new MoveRef(6, 5))

            .add(new Var_I(adapter.getClassTypeConstId("Function"),
                adapter.getMethodVarId("TestApp", "lambda_1"))) // #7
            .add(new FBind(7, new int[]{0}, new int[]{1}, 7))
            .add(new Invoke_10(5,
                adapter.getMethodConstId("annotations.FutureRef", "whenComplete"),
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
            .add(new Invoke_10(4, adapter.getMethodConstId("annotations.FutureRef", "set"),
                    adapter.ensureValueConstantId(99)))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #8 (e)
            .add(new X_Print(8))
            .add(new CatchEnd(1))

            .add(new PIP_PreInc(adapter.getPropertyConstId("TestApp.TestService", "counter2"), 0,
                8))  // next register #8
            .add(new X_Print(8))

            .add(new PIP_PostInc(adapter.getPropertyConstId("TestApp.TestService", "counter"), 0,
                8))
            .add(new X_Print(8))
            .add(new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 8))
            .add(new X_Print(8))

            .add(new P_Get(adapter.getPropertyConstId("Ref", "RefType"), 4, 9)) // next register #5
            .add(new X_Print(9))

            .add(new Invoke_00(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "yield")))

            .add(new Invoke_10(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(0)))
            .add(new Return_0());

        // --- testService2 ---

        MethodStructure ftTestReturn = ensureMethodStructure("testBlockingReturn",
                new String[] {"Service"}, INT);
        ftTestReturn.createCode()
            // #0 = svc
            .add(new Var(adapter.getClassTypeConstId("Int64"))) // #1
            .add(new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 1))
            .add(new Return_1(1));

        MethodStructure ftTestService2 = ensureMethodStructure("testService2", VOID);
        ftTestService2.createCode()
            .add(new X_Print(
                    adapter.ensureValueConstantId("\n# in TestApp.testService2() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.TestService"),
                    adapter.ensureValueConstantId("svc")))     // #0
            .add(new New_1(-adapter.getMethodConstId("TestApp.TestService", "construct"),
                    adapter.ensureValueConstantId(48), 0))

            .add(new Var_I(adapter.getClassTypeConstId("Function"),
                     adapter.getMethodVarId("TestApp", "testBlockingReturn"))) // #1
            .add(new FBind(1, new int[] {0}, new int[] {0}, 1))

            .add(new Var_N(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("c"))) // #2
            .add(new Call_01(1, 2))
            .add(new X_Print(2))

            .add(new Var_DN(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                     adapter.ensureValueConstantId("fc"))) // #3 (fc)
            .add(new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 3))
            .add(new X_Print(3))

            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +3))
            .add(new Call_01(-adapter.getMethodConstId("TestApp", "getIntValue"), 3))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #4 (e)
            .add(new X_Print(4))
            .add(new CatchEnd(1))

            .add(new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(2000)))
            .add(new GuardAll(+4))
            .add(new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(1000), 2))
            .add(new X_Print(2))
            .add(new FinallyStart())
            .add(new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(0)))
            .add(new FinallyEnd())

            .add(new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(500)))
            .add(new GuardAll(+9))
            .add(new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +4))
            .add(new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(200000), 2))
            .add(new Assert(adapter.ensureValueConstantId(false)))
            .add(new GuardEnd(+4))
            .add(new CatchStart()) // #4 (e)
            .add(new X_Print(4))
            .add(new CatchEnd(1))
            .add(new FinallyStart())
            .add(new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(0)))
            .add(new FinallyEnd())
            .add(new Return_0());

        // --- testRef()

        MethodStructure ftTestRef = ensureMethodStructure("testRef", STRING);
        ftTestRef.createCode()
            // #0 = arg
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testRef() #")))

            .add(new Var_N(adapter.getClassTypeConstId("Ref<String>"),
                    adapter.ensureValueConstantId("ra"))) // #1 (ra)
            .add(new MoveRef(0, 1))

            .add(new Var(adapter.getClassTypeConstId("String"))) // #2
            .add(new Invoke_01(1, adapter.getMethodConstId("Ref", "get"), 2))
            .add(new X_Print(2))
            .add(new Invoke_10(1, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId("bye")))
            .add(new X_Print(0))

            .add(new Var_N(adapter.getClassTypeConstId("Ref<Int64>"),
                    adapter.ensureValueConstantId("ri"))) // #3 (ri)
            .add(new Enter())
            .add(new Var_IN(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("i"),
                    adapter.ensureValueConstantId(1))) // #4 (i)
            .add(new Var_N(adapter.getClassTypeConstId("Ref<Int64>"),
                    adapter.ensureValueConstantId("ri2"))) // #5 (ri2)
            .add(new MoveRef(4, 5))
            .add(new MoveRef(4, 3))

            .add(new Var(adapter.getClassTypeConstId("Int64"))) // #6
            .add(new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 6))
            .add(new X_Print(6))

            .add(new Invoke_10(3, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId(2)))
            .add(new X_Print(4))

            .add(new Move(adapter.ensureValueConstantId(3), 4))
            .add(new X_Print(5))
            .add(new Exit())

            .add(new Var(adapter.getClassTypeConstId("Int64"))) // #4
            .add(new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 4))
            .add(new X_Print(4))

            .add(new Return_0());

        // --- testArray()

        MethodStructure ftLambda$2 = ensureMethodStructure("lambda_2",
                new String[] {"Ref<Int64>"}, STRING);
        ftLambda$2.createCode()
            // #0 = i
            .add(new Var_I(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("value "))) // #1
            .add(new Var(adapter.getClassTypeConstId("String"))) // #2
            .add(new Invoke_01(0, adapter.getMethodConstId("Int64", "to", VOID, STRING), 2))
            .add(new GP_Add(1, 2, 1))
            .add(new Return_1(1));

        MethodStructure ftTestArray = ensureMethodStructure("testArray", VOID);
        ftTestArray.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testArray() #")))
            .add(new Var_N(adapter.getClassTypeConstId("collections.Array<Int64>"),
                adapter.ensureValueConstantId("ai")))   // #0 (ai)
            .add(new NewG_1(-adapter.getMethodConstId("collections.Array", "construct"),
                -adapter.getClassTypeConstId("collections.Array<Int64>"),
                adapter.ensureValueConstantId(0), 0))
            .add(new I_Set(0, adapter.ensureValueConstantId(0),
                    adapter.ensureValueConstantId(1)))
            .add(new I_Set(0, adapter.ensureValueConstantId(1),
                adapter.ensureValueConstantId(2)))
            .add(new X_Print(0))

            .add(new I_Get(0, adapter.ensureValueConstantId(0), 1)) // next register #1
            .add(new X_Print(1))

            .add(new IIP_PreInc(0, adapter.ensureValueConstantId(1), 1))
            .add(new X_Print(1))

            .add(new Var_N(adapter.getClassTypeConstId("collections.Array<String>"),
                adapter.ensureValueConstantId("as1")))   // #2 (as1)
            .add(new NewG_N(-adapter.getMethodConstId("collections.Array", "construct",
                new String[]{"Int64", "Function"}, VOID),
                -adapter.getClassTypeConstId("collections.Array<String>"),
                new int[]{
                    adapter.ensureValueConstantId(5),
                    adapter.getMethodVarId("TestApp", "lambda_2")
                }, 2))
            .add(new X_Print(2))

            .add(new I_Get(2, adapter.ensureValueConstantId(4), 3)) // next register #3
            .add(new X_Print(3))

            .add(new I_Ref(2, adapter.ensureValueConstantId(0), 4)) // next register #4
            .add(new Invoke_01(4, adapter.getMethodConstId("Ref", "get"), 3))
            .add(new X_Print(3))
            .add(new Invoke_10(4, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId("zero")))
            .add(new I_Get(2, adapter.ensureValueConstantId(0), 3))
            .add(new X_Print(3))

            .add(new Return_0());

        // ----- testTuple()

        MethodStructure ftTestCond = ensureMethodStructure("testConditional", INT, null);
            {
            Code code = ftTestCond.createCode();

            // #0 - i
            Var var_i = new Var(adapter.getClassType("Boolean", null));
            code.add(var_i); // #1
            code.add(new IsGt(0, adapter.ensureValueConstantId(0), 1));

            Label labelFalse = new Label();
            code.add(new JumpFalse(var_i.getRegister(), labelFalse));
            code.add(new Return_N(new int[] {
                    adapter.ensureValueConstantId(true),
                    adapter.ensureValueConstantId("positive")}));
            code.add(labelFalse);
            code.add(new Return_1(adapter.ensureValueConstantId(false)));
            }

        MethodStructure ftTestTuple = ensureMethodStructure("testTuple", VOID);
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
            .add(new NewG_1(-adapter.getMethodConstId("collections.Tuple", "construct"),
                -adapter.getClassTypeConstId("collections.Tuple<String,Int64>"), 3,
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

            .add(new Enter())
            .add(new Var(adapter.getClassTypeConstId("Boolean"))) // #6
            .add(new Var_N(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("s"))) // #7
            .add(new Call_1N(-adapter.getMethodConstId("TestApp", "testConditional"),
                    adapter.ensureValueConstantId(1), new int[] {6, 7}))
            .add(new JumpFalse(6, 2))
            .add(new X_Print(7))
            .add(new Exit())

            .add(new Var(adapter.getClassTypeConstId("collections.Tuple<Boolean,String>"))) // #6
            .add(new Call_1T(-adapter.getMethodConstId("TestApp", "testConditional"),
                adapter.ensureValueConstantId(-1), 6))
            .add(new X_Print(6))

            .add(new Return_0());

        // ----- testConst()

        ClassTemplate ctPoint = f_types.getTemplate("TestApp.Point");
        adapter.addMethod(ctPoint.f_struct, "construct", new String[] {"Int64", "Int64"}, VOID);
        MethodStructure mtConst = ctPoint.ensureMethodStructure("construct",
                new String[] {"Int64", "Int64"});
        mtConst.createCode()
            // #0 = x; #1 = y
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Point", "x"), 0))
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Point", "y"), 1))
            .add(new Return_0());

        // Point.to<String>()
        MethodStructure mtTo = ctPoint.ensureMethodStructure("to", VOID, STRING);
        mtTo.createCode()
            .add(new Var_I(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("("))) // #0
            .add(new L_Get(adapter.getPropertyConstId("TestApp.Point", "x"), 1)) // next register #1
            .add(new Var(adapter.getClassTypeConstId("String"))) // #2
            .add(new Invoke_01(1, adapter.getMethodConstId("Object", "to", VOID, STRING), 2))
            .add(new GP_Add(0, 2, 0))
            .add(new GP_Add(0, adapter.ensureValueConstantId(", "), 0))
            .add(new L_Get(adapter.getPropertyConstId("TestApp.Point", "y"), 1))
            .add(new Invoke_01(1, adapter.getMethodConstId("Object", "to", VOID, STRING), 2))
            .add(new GP_Add(0, 2, 0))
            .add(new GP_Add(0, adapter.ensureValueConstantId(")"), 0))
            .add(new Return_1(0));

        // Point.hash.get()
        ctPoint.markCalculated("hash");
        MethodStructure mtGetHash = ctPoint.ensureGetter("hash");
        mtGetHash.createCode()
            .add(new L_Get(adapter.getPropertyConstId("TestApp.Point", "x"), 0)) // next register #0
            .add(new L_Get(adapter.getPropertyConstId("TestApp.Point", "y"), 1)) // next register #1
            .add(new GP_Add(0, 1, 0))
            .add(new Return_1(0));

        ClassTemplate ctRectangle = f_types.getTemplate("TestApp.Rectangle");
        adapter.addMethod(ctRectangle.f_struct, "construct", new String[] {"TestApp.Point", "TestApp.Point"}, VOID);
        MethodStructure mtRectangle = ctRectangle.ensureMethodStructure("construct",
                new String[] {"TestApp.Point", "TestApp.Point"});
        mtRectangle.createCode()
            // #0 = tl; #1 = br
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Rectangle", "tl"), 0))
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Rectangle", "br"), 1))
            .add(new Return_0());

        MethodStructure ftTestConst = ensureMethodStructure("testConst", VOID);
        ftTestConst.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testConst() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.Point"),
                adapter.ensureValueConstantId("p1"))) // #0 (p1)
            .add(new New_N(-adapter.getMethodConstId("TestApp.Point", "construct"),
                new int[]{
                    adapter.ensureValueConstantId(0),
                    adapter.ensureValueConstantId(1)
                },
                0))
            .add(new X_Print(0))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.Point"),
                adapter.ensureValueConstantId("p2"))) // #1 (p2)
            .add(new New_N(-adapter.getMethodConstId("TestApp.Point", "construct"),
                new int[]{
                    adapter.ensureValueConstantId(1),
                    adapter.ensureValueConstantId(0)
                },
                1))
            .add(new X_Print(1))

            .add(new IsEq(0, 1, 2)) // next register #2
            .add(new X_Print(adapter.ensureValueConstantId("p1 == p2")))
            .add(new X_Print(2))

            .add(new IsGt(1, 0, 2))
            .add(new X_Print(adapter.ensureValueConstantId("p2 > p1")))
            .add(new X_Print(2))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.Rectangle"),
                adapter.ensureValueConstantId("r"))) // #3 (r)
            .add(new New_N(-adapter.getMethodConstId("TestApp.Rectangle", "construct"),
                new int[]{1, 0}, 3))
            .add(new X_Print(3))
            .add(new P_Get(adapter.getPropertyConstId("Const", "hash"), 3, 4)) // next register #4
            .add(new X_Print(4))

            .add(new Var_I(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId(42))) // #5
            .add(new P_Get(adapter.getPropertyConstId("Const", "hash"), 5, 5))
            .add(new X_Print(5))

            .add(new Var_IN(adapter.getClassTypeConstId("TestApp.Color"),
                    adapter.ensureValueConstantId("c"),
                    adapter.ensureEnumConstId("TestApp.Color.Blue"))) // #6
            .add(new X_Print(6))

            .add(new P_Get(adapter.getPropertyConstId("Enum", "ordinal"), 6, 4))
            .add(new X_Print(4))

            .add(new Return_0());

        ClassTemplate ctFormatter = f_types.getTemplate("TestApp.Formatter");
        adapter.addMethod(ctFormatter.f_struct, "construct", STRING, VOID);

        MethodStructure mtFormatter = ctFormatter.ensureMethodStructure("construct", STRING);
        mtFormatter.createCode()
            // #0 = prefix
            .add(new L_Set(adapter.getPropertyConstId("TestApp.Formatter", "prefix"), 0))
            .add(new Return_0());

        MethodStructure mtToString = ctFormatter.ensureMethodStructure("to", VOID, STRING);
        mtToString.createCode()
            .add(new L_Get(adapter.getPropertyConstId("TestApp.Formatter", "prefix"), 0))  // next register #0
            .add(new Var(adapter.getClassTypeConstId("String"))) // #1
            .add(new Call_01(Op.A_SUPER, 1))
            .add(new GP_Add(0, 1, 0))
            .add(new Return_1(0));

        ClassTemplate ctPrPoint = f_types.getTemplate("TestApp.PrettyPoint");
        MethodStructure mtPrPConst = ctPrPoint.ensureMethodStructure("construct",
                new String[] {"Int64", "Int64", "String"});
        mtPrPConst.createCode()
            // #0 = x; #1 = y; #2 = prefix
            .add(new Construct_N(-adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {0, 1}))
            .add(new Construct_1(-adapter.getMethodConstId("TestApp.Formatter", "construct"),
                    2))
            .add(new Return_0());

        ClassTemplate ctPrRectangle = f_types.getTemplate("TestApp.PrettyRectangle");
        MethodStructure mtPrRConst = ctPrRectangle.ensureMethodStructure("construct",
                new String[] {"TestApp.Point", "TestApp.Point", "String"});
        mtPrRConst.createCode()
            // #0 = tl; #1 = br; #2 = prefix
            .add(new Construct_N(-adapter.getMethodConstId("TestApp.Rectangle", "construct"),
                    new int[] {0, 1}))
            .add(new Construct_1(-adapter.getMethodConstId("TestApp.Formatter", "construct"),
                    2))
            .add(new Return_0());

        MethodStructure ftTestMixin = ensureMethodStructure("testMixin", VOID);
        ftTestMixin.createCode()
            .add(new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testMixin() #")))
            .add(new Var_N(adapter.getClassTypeConstId("TestApp.PrettyPoint"),
                    adapter.ensureValueConstantId("prp"))) // #0 (prp)
            .add(new New_N(-adapter.getMethodConstId("TestApp.PrettyPoint", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(1),
                            adapter.ensureValueConstantId(2),
                            adapter.ensureValueConstantId("*** ")
                    }, 0))
            .add(new X_Print(0))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.Point"),
                    adapter.ensureValueConstantId("p2"))) // #1 (p2)
            .add(new New_N(-adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(2),
                            adapter.ensureValueConstantId(1)
                    },
                    1))

            .add(new Var_N(adapter.getClassTypeConstId("TestApp.PrettyRectangle"),
                    adapter.ensureValueConstantId("prr"))) // #2 (prr)
            .add(new New_N(-adapter.getMethodConstId("TestApp.PrettyRectangle", "construct"),
                    new int[] {0, 1, adapter.ensureValueConstantId("+++ ")}, 2))
            .add(new X_Print(2))

            .add(new Return_0());

        // --- run()
        MethodStructure mtRun = ensureMethodStructure("run", VOID, VOID);
        mtRun.createCode()
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "test1")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "test2")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testService")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testService2")))
            .add(new Call_10(-adapter.getMethodConstId("TestApp", "testRef"),
                    adapter.ensureValueConstantId("hi")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testArray")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testTuple")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testConst")))
            .add(new Call_00(-adapter.getMethodConstId("TestApp", "testMixin")))
            .add(new Return_0());
        }
    }
