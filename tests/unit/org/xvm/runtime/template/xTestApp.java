package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
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
        ftGetInt.setOps(new Op[]
            {
            new Return_1(adapter.ensureValueConstantId(42)),
            });

        // --- test1()
        MethodStructure ftTest1 = ensureMethodStructure("test1", VOID, VOID);
        ftTest1.setOps(new Op[]
            {
            new DNVar(
                    adapter.getClassTypeConstId("annotations.InjectedRef<io.Console>"),
                    adapter.ensureValueConstantId("console")), // #0 (console)
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.test1() #")),
            new INVar(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("s"),
                    adapter.ensureValueConstantId("Hello world!")), // #1 (s)
            new Invoke_10(0, adapter.getMethodConstId("io.Console", "print"),
                    adapter.ensureValueConstantId("\n***** ")),
            new Invoke_10(0, adapter.getMethodConstId("io.Console", "println"), 1),
            new Invoke_10(0, adapter.getMethodConstId("io.Console", "println"),
                    adapter.ensureValueConstantId("")),

            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("i")), // #2 (i)
            new Call_01(-adapter.getMethodConstId("TestApp", "getIntValue"), 2),
            new X_Print(2),

            new Enter(),
            new Var(adapter.getClassTypeConstId("Boolean")), // #3
            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("of")), // #4 (of)
            new Invoke_NN(1, adapter.getMethodConstId("String", "indexOf"),
                    new int[] {
                            adapter.ensureValueConstantId("world"),
                            adapter.ensureValueConstantId(null)
                    },
                    new int[] {3, 4}),
            new JumpFalse(3, 10), // -> Exit

            new Var(adapter.getClassTypeConstId("Int64")), // #5
            new PGet(1, adapter.getPropertyConstId("String", "size"), 5),
            new Add(5, 4, 5),
            new Var(adapter.getClassTypeConstId("Boolean")), // #6
            new IsEq(5, adapter.ensureValueConstantId(18), 6),
            new Assert(6),

            new Var(adapter.getClassTypeConstId("String")), // #7
            new Invoke_01(4, adapter.getMethodConstId("Int64", "to", VOID, STRING), 7),
            new X_Print(7),
            new Exit(),

            new Return_0(),
            });

        // --- test2()

        MethodStructure ftTest2 = ensureMethodStructure("test2", VOID);
        ftTest2.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.test2() #")),
            new NVar(adapter.getClassTypeConstId("TestApp.TestClass"),
                    adapter.ensureValueConstantId("t")),  // #0 (t)
            new New_1(adapter.getMethodConstId("TestApp.TestClass", "construct"),
                    adapter.ensureValueConstantId("Hello World!"), 0),
            new X_Print(0),
            new Var(adapter.getClassTypeConstId("String")),   // #1
            new PGet(0, adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 1),
            new X_Print(1),
            new Var(adapter.getClassTypeConstId("Int64")),    // #2
            new Invoke_01(0, adapter.getMethodConstId("TestApp.TestClass", "method1"),
                    2),
            new X_Print(2),

            new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(0,
                    adapter.getMethodConstId("TestApp.TestClass", "exceptional"),
                    adapter.ensureValueConstantId("handled")),
            new GuardEnd(+4),
            new HandlerStart(), // #3 (e)
            new X_Print(3),
            new HandlerEnd(1),

            new NVar(adapter.getClassTypeConstId("TestApp.TestClass"),
                    adapter.ensureValueConstantId("t2")), // #3 (t2)
            new New_N(adapter.getMethodConstId("TestApp.TestClass2", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(42),
                            adapter.ensureValueConstantId("Goodbye")
                    }, 3),
            new X_Print(3),
            new Var(adapter.getClassTypeConstId("String")),   // #4
            new PGet(3, adapter.getPropertyConstId("TestApp.TestClass", "prop1"), 4),
            new X_Print(4),
            new Var(adapter.getClassTypeConstId("Int64")),    // #5
            new Invoke_01(3, adapter.getMethodConstId("TestApp.TestClass", "method1"),
                    5),
            new X_Print(5),

            new NVar(adapter.getClassTypeConstId("Function"),
                    adapter.ensureValueConstantId("fn")), // #6 (fn)
            new MBind(3, adapter.getMethodConstId("TestApp.TestClass", "method1"), 6),
            new Var(adapter.getClassTypeConstId("Int64")),    // #7
            new Call_01(6, 7),
            new X_Print(7),

            new Return_0(),
            });

        // --- testService()

        MethodStructure ftLambda$1 = ensureMethodStructure("lambda_1",
                new String[] {"Int64", "Int64", "Exception"});
        ftLambda$1.setOps(new Op[]
            { // #0 = c; #1 = r, #2 = x
            new X_Print(adapter.ensureValueConstantId(
                "\n# in TestApp.lambda_1 (rfc2.whenComplete) #")),
            new X_Print(0),
            new X_Print(1),
            new X_Print(2),
            new Return_0(),
            });

        MethodStructure ftTestService = ensureMethodStructure("testService", VOID);
        ftTestService.setOps(new Op[]
            {
            new X_Print(
                    adapter.ensureValueConstantId("\n# in TestApp.testService() #")),
            new NVar(adapter.getClassTypeConstId("TestApp.TestService"),
                    adapter.ensureValueConstantId("svc")),     // #0
            new New_1(adapter.getMethodConstId("TestApp.TestService", "construct"),
                    adapter.ensureValueConstantId(48), 0),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("c")),        // #1 (c)
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 1),
            new X_Print(1),

            new PSet(0, adapter.getPropertyConstId("TestApp.TestService", "counter"),
                    adapter.ensureValueConstantId(17)),
            new PGet(0, adapter.getPropertyConstId("TestApp.TestService", "counter"),
                    1),
            new X_Print(1),

            new NVar(adapter.getClassTypeConstId("Function"),
                    adapter.ensureValueConstantId("fnInc")),   // #2 (fnInc)
            new MBind(0, adapter.getMethodConstId("TestApp.TestService", "increment"),
                    2),
            new Call_01(2, 1),
            new X_Print(1),

            new DNVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                     adapter.ensureValueConstantId("fc")), // #3 (fc)
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 3),
            new NVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                    adapter.ensureValueConstantId("rfc")), // #4 (rfc)
            new MoveRef(3, 4),
            new X_Print(4),
            new X_Print(3),
            new X_Print(4),

            new NVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                    adapter.ensureValueConstantId("rfc")), // #5 (rfc2)
            new DNVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                     adapter.ensureValueConstantId("rfc3")), // #6 (rfc3)
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 6),
            new MoveRef(6, 5),

            new IVar(adapter.getClassTypeConstId("Function"),
                     adapter.getMethodVarId("TestApp", "lambda_1")), // #7
            new FBind(7, new int[] {0}, new int[] {1}, 7),
            new Invoke_10(5,
                    adapter.getMethodConstId("annotations.FutureRef", "whenComplete"),
                    7),

            new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(0), 1),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(4, adapter.getMethodConstId("annotations.FutureRef", "set"),
                    adapter.ensureValueConstantId(99)),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new Var(adapter.getClassTypeConstId("Int64")), // #8
            new PPreInc(0,
                    adapter.getPropertyConstId("TestApp.TestService", "counter2"), 8),
            new X_Print(8),

            new PPostInc(0,
                    adapter.getPropertyConstId("TestApp.TestService", "counter"), 8),
            new X_Print(8),
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 8),
            new X_Print(8),

            new Var(adapter.getClassTypeConstId("Type")), // #9
            new PGet(4, adapter.getPropertyConstId("Ref", "RefType"), 9),
            new X_Print(9),

            new Invoke_00(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "yield")),

            new Invoke_10(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(0)),
            new Return_0(),
            });

        // --- testService2 ---

        MethodStructure ftTestReturn = ensureMethodStructure("testBlockingReturn",
                new String[] {"Service"}, INT);
        ftTestReturn.setOps(new Op[]
            { // #0 = svc
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 1),
            new Return_1(1),
            });

        MethodStructure ftTestService2 = ensureMethodStructure("testService2", VOID);
        ftTestService2.setOps(new Op[]
            {
            new X_Print(
                    adapter.ensureValueConstantId("\n# in TestApp.testService2() #")),
            new NVar(adapter.getClassTypeConstId("TestApp.TestService"),
                    adapter.ensureValueConstantId("svc")),     // #0
            new New_1(adapter.getMethodConstId("TestApp.TestService", "construct"),
                    adapter.ensureValueConstantId(48), 0),

            new IVar(adapter.getClassTypeConstId("Function"),
                     adapter.getMethodVarId("TestApp", "testBlockingReturn")), // #1
            new FBind(1, new int[] {0}, new int[] {0}, 1),

            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("c")), // #2
            new Call_01(1, 2),
            new X_Print(2),

            new DNVar(adapter.getClassTypeConstId("annotations.FutureRef<Int64>"),
                     adapter.ensureValueConstantId("fc")), // #3 (fc)
            new Invoke_01(0,
                    adapter.getMethodConstId("TestApp.TestService", "increment"), 3),
            new X_Print(3),

            new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Call_01(-adapter.getMethodConstId("TestApp", "getIntValue"), 3),
            new GuardEnd(+4),
            new HandlerStart(), // #4 (e)
            new X_Print(4),
            new HandlerEnd(1),

            new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(2000)),
            new GuardAll(+4),
            new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(1000), 2),
            new X_Print(2),
            new FinallyStart(),
            new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(0)),
            new FinallyEnd(),

            new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(500)),
            new GuardAll(+9),
            new GuardStart(adapter.getClassTypeConstId("Exception"),
                    adapter.ensureValueConstantId("e"), +4),
            new Invoke_11(0,
                    adapter.getMethodConstId("TestApp.TestService", "exceptional"),
                    adapter.ensureValueConstantId(200000), 2),
            new Assert(adapter.ensureValueConstantId(false)),
            new GuardEnd(+4),
            new HandlerStart(), // #4 (e)
            new X_Print(4),
            new HandlerEnd(1),
            new FinallyStart(),
            new Invoke_10(Op.A_SERVICE,
                    adapter.getMethodConstId("TestApp.TestService", "registerTimeout"),
                    adapter.ensureValueConstantId(0)),
            new FinallyEnd(),
            new Return_0(),
            });

        // --- testRef()

        MethodStructure ftTestRef = ensureMethodStructure("testRef", STRING);
        ftTestRef.setOps(new Op[]
            { // #0 = arg
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testRef() #")),

            new NVar(adapter.getClassTypeConstId("Ref<String>"),
                    adapter.ensureValueConstantId("ra")), // #1 (ra)
            new MoveRef(0, 1),

            new Var(adapter.getClassTypeConstId("String")), // #2
            new Invoke_01(1, adapter.getMethodConstId("Ref", "get"), 2),
            new X_Print(2),
            new Invoke_10(1, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId("bye")),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("Ref<Int64>"),
                    adapter.ensureValueConstantId("ri")), // #3 (ri)
            new Enter(),
            new INVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("i"),
                    adapter.ensureValueConstantId(1)), // #4 (i)
            new NVar(adapter.getClassTypeConstId("Ref<Int64>"),
                    adapter.ensureValueConstantId("ri2")), // #5 (ri2)
            new MoveRef(4, 5),
            new MoveRef(4, 3),

            new Var(adapter.getClassTypeConstId("Int64")), // #6
            new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 6),
            new X_Print(6),

            new Invoke_10(3, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId(2)),
            new X_Print(4),

            new Move(adapter.ensureValueConstantId(3), 4),
            new X_Print(5),
            new Exit(),

            new Var(adapter.getClassTypeConstId("Int64")), // #4
            new Invoke_01(3, adapter.getMethodConstId("Ref", "get"), 4),
            new X_Print(4),

            new Return_0()
            });

        // --- testArray()

        MethodStructure ftLambda$2 = ensureMethodStructure("lambda_2",
                new String[] {"Ref<Int64>"}, STRING);
        ftLambda$2.setOps(new Op[]
            { // #0 = i
            new IVar(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("value ")), // #1
            new Var(adapter.getClassTypeConstId("String")), // #2
            new Invoke_01(0, adapter.getMethodConstId("Int64", "to", VOID, STRING), 2),
            new Add(1, 2, 1),
            new Return_1(1)
            });

        MethodStructure ftTestArray = ensureMethodStructure("testArray", VOID);
        ftTestArray.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testArray() #")),
            new NVar(adapter.getClassTypeConstId("collections.Array<Int64>"),
                    adapter.ensureValueConstantId("ai")),   // #0 (ai)
            new New_1G(adapter.getMethodConstId("collections.Array", "construct"),
                    -adapter.getClassTypeConstId("collections.Array<Int64>"),
                    adapter.ensureValueConstantId(0), 0),
            new ISet(0, adapter.ensureValueConstantId(0),
                    adapter.ensureValueConstantId(1)),
            new ISet(0, adapter.ensureValueConstantId(1),
                    adapter.ensureValueConstantId(2)),
            new X_Print(0),

            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new IGet(0, adapter.ensureValueConstantId(0), 1),
            new X_Print(1),

            new IPreInc(0, adapter.ensureValueConstantId(1), 1),
            new X_Print(1),

            new NVar(adapter.getClassTypeConstId("collections.Array<String>"),
                    adapter.ensureValueConstantId("as1")),   // #2 (as1)
            new New_NG(adapter.getMethodConstId("collections.Array", "construct",
                    new String[] {"Int64", "Function"}, VOID),
                    -adapter.getClassTypeConstId("collections.Array<String>"),
                    new int[] {
                            adapter.ensureValueConstantId(5),
                            adapter.getMethodVarId("TestApp", "lambda_2")
                    }, 2),
            new X_Print(2),

            new Var(adapter.getClassTypeConstId("String")), // #3
            new IGet(2, adapter.ensureValueConstantId(4), 3),
            new X_Print(3),

            new Var(adapter.getClassTypeConstId("Ref<String>")), // #4
            new IRef(2, adapter.ensureValueConstantId(0), 4),
            new Invoke_01(4, adapter.getMethodConstId("Ref", "get"), 3),
            new X_Print(3),
            new Invoke_10(4, adapter.getMethodConstId("Ref", "set"),
                    adapter.ensureValueConstantId("zero")),
            new IGet(2, adapter.ensureValueConstantId(0), 3),
            new X_Print(3),

            new Return_0()
            });

        // ----- testTuple()

        MethodStructure ftTestCond = ensureMethodStructure("testConditional", INT, null);
        ftTestCond.setOps(new Op[]
            { // #0 - i
            new Var(adapter.getClassTypeConstId("Boolean")), // #1
            new IsGt(0, adapter.ensureValueConstantId(0), 1),
            new JumpFalse(1, 2),
            new Return_N(new int[] {
                    adapter.ensureValueConstantId(true),
                    adapter.ensureValueConstantId("positive")
            }),
            new Return_1(adapter.ensureValueConstantId(false))
            });

        MethodStructure ftTestTuple = ensureMethodStructure("testTuple", VOID);
        ftTestTuple.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testTuple() #")),
            new INVar(adapter.getClassTypeConstId("collections.Tuple<String,Int64>"),
                    adapter.ensureValueConstantId("t"),
                    adapter.ensureValueConstantId(
                        new Object[]{"zero", Integer.valueOf(0)})), // #0 (t)

            new Enter(),
            new Var(adapter.getClassTypeConstId("String")), // #1
            new IGet(0, adapter.ensureValueConstantId(0), 1),
            new X_Print(1),
            new Exit(),

            new Enter(),
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new IGet(0, adapter.ensureValueConstantId(1), 1),
            new X_Print(1),
            new Exit(),

            new INVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("i"),
                    adapter.ensureValueConstantId(0)), // #1 (i)
            new NVar(adapter.getClassTypeConstId("collections.Tuple<String,Int64>"),
                    adapter.ensureValueConstantId("t2")), // #2 (t2)
            new TVar(new int[] {
                    adapter.getClassTypeConstId("String"),
                    adapter.getClassTypeConstId("Int64")},
                    new int[] {adapter.ensureValueConstantId(""), 1}), // #3
            new New_1G(adapter.getMethodConstId("collections.Tuple", "construct"),
                    -adapter.getClassTypeConstId("collections.Tuple<String,Int64>"), 3,
                    2),
            new X_Print(2),

            new ISet(2, adapter.ensureValueConstantId(0),
                    adapter.ensureValueConstantId("t")),
            new ISet(2, adapter.ensureValueConstantId(1),
                    adapter.ensureValueConstantId(2)),
            new X_Print(2),

            new NVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId("of")), // #4 (of)
            new Invoke_T1(adapter.ensureValueConstantId("the test"),
                    adapter.getMethodConstId("String", "indexOf"), 2, 4),

            new Var(adapter.getClassTypeConstId("Boolean")), // #5
            new IsEq(4, adapter.ensureValueConstantId(4), 5),
            new AssertV(5, adapter.ensureValueConstantId("of == 4"), new int[] {4}),

            new Enter(),
            new Var(adapter.getClassTypeConstId("Boolean")), // #6
            new NVar(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("s")), // #7
            new Call_1N(-adapter.getMethodConstId("TestApp", "testConditional"),
                    adapter.ensureValueConstantId(1), new int[] {6, 7}),
            new JumpFalse(6, 2),
            new X_Print(7),
            new Exit(),

            new Var(adapter.getClassTypeConstId("collections.Tuple<Boolean,String>")),
            // #6
            new Call_1T(-adapter.getMethodConstId("TestApp", "testConditional"),
                    adapter.ensureValueConstantId(-1), 6),
            new X_Print(6),

            new Return_0()
            });

        // ----- testConst()

        ClassTemplate ctPoint = f_types.getTemplate("TestApp.Point");
        adapter.addMethod(ctPoint.f_struct, "construct", new String[] {"Int64", "Int64"}, VOID);
        MethodStructure mtConst = ctPoint.ensureMethodStructure("construct",
                new String[] {"Int64", "Int64"});
        mtConst.setOps(new Op[]
            { // #0 = x; #1 = y
            new LSet(adapter.getPropertyConstId("TestApp.Point", "x"), 0),
            new LSet(adapter.getPropertyConstId("TestApp.Point", "y"), 1),
            new Return_0()
            });

        // Point.to<String>()
        MethodStructure mtTo = ctPoint.ensureMethodStructure("to", VOID, STRING);
        mtTo.setOps(new Op[]
            {
            new IVar(adapter.getClassTypeConstId("String"),
                    adapter.ensureValueConstantId("(")), // #0
            new Var(adapter.getClassTypeConstId("Int64")), // #1
            new Var(adapter.getClassTypeConstId("String")), // #2
            new LGet(adapter.getPropertyConstId("TestApp.Point", "x"), 1),
            new Invoke_01(1, adapter.getMethodConstId("Object", "to", VOID, STRING), 2),
            new Add(0, 2, 0),
            new Add(0, adapter.ensureValueConstantId(", "), 0),
            new LGet(adapter.getPropertyConstId("TestApp.Point", "y"), 1),
            new Invoke_01(1, adapter.getMethodConstId("Object", "to", VOID, STRING), 2),
            new Add(0, 2, 0),
            new Add(0, adapter.ensureValueConstantId(")"), 0),
            new Return_1(0),
            });

        // Point.hash.get()
        ctPoint.markCalculated("hash");
        MethodStructure mtGetHash = ctPoint.ensureGetter("hash");
        mtGetHash.setOps(new Op[]
            {
            new Var(adapter.getClassTypeConstId("Int64")), // (#0)
            new LGet(adapter.getPropertyConstId("TestApp.Point", "x"), 0),
            new Var(adapter.getClassTypeConstId("Int64")), // (#1)
            new LGet(adapter.getPropertyConstId("TestApp.Point", "y"), 1),
            new Add(0, 1, 0),
            new Return_1(0),
            });

        ClassTemplate ctRectangle = f_types.getTemplate("TestApp.Rectangle");
        adapter.addMethod(ctRectangle.f_struct, "construct", new String[] {"TestApp.Point", "TestApp.Point"}, VOID);
        MethodStructure mtRectangle = ctRectangle.ensureMethodStructure("construct",
                new String[] {"TestApp.Point", "TestApp.Point"});
        mtRectangle.setOps(new Op[]
            { // #0 = tl; #1 = br
            new LSet(adapter.getPropertyConstId("TestApp.Rectangle", "tl"), 0),
            new LSet(adapter.getPropertyConstId("TestApp.Rectangle", "br"), 1),
            new Return_0()
            });

        MethodStructure ftTestConst = ensureMethodStructure("testConst", VOID);
        ftTestConst.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testConst() #")),
            new NVar(adapter.getClassTypeConstId("TestApp.Point"),
                    adapter.ensureValueConstantId("p1")), // #0 (p1)
            new New_N(adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(0),
                            adapter.ensureValueConstantId(1)
                    },
                    0),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("TestApp.Point"),
                    adapter.ensureValueConstantId("p2")), // #1 (p2)
            new New_N(adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(1),
                            adapter.ensureValueConstantId(0)
                    },
                    1),
            new X_Print(1),

            new Var(adapter.getClassTypeConstId("Boolean")), // #2
            new IsEq(0, 1, 2),
            new X_Print(adapter.ensureValueConstantId("p1 == p2")),
            new X_Print(2),

            new IsGt(1, 0, 2),
            new X_Print(adapter.ensureValueConstantId("p2 > p1")),
            new X_Print(2),

            new NVar(adapter.getClassTypeConstId("TestApp.Rectangle"),
                    adapter.ensureValueConstantId("r")), // #3 (r)
            new New_N(adapter.getMethodConstId("TestApp.Rectangle", "construct"),
                    new int[] {1, 0}, 3),
            new X_Print(3),
new Nop(),
            new Var(adapter.getClassTypeConstId("Int64")), // #4
            new PGet(3, adapter.getPropertyConstId("Const", "hash"), 4),
            new X_Print(4),

            new IVar(adapter.getClassTypeConstId("Int64"),
                    adapter.ensureValueConstantId(42)), // #5
            new PGet(5, adapter.getPropertyConstId("Const", "hash"), 5),
            new X_Print(5),

            new INVar(adapter.getClassTypeConstId("TestApp.Color"),
                    adapter.ensureValueConstantId("c"),
                    adapter.ensureEnumConstId("TestApp.Color.Blue")), // #6
            new X_Print(6),

            new PGet(6, adapter.getPropertyConstId("Enum", "ordinal"), 4),
            new X_Print(4),

            new Return_0()
            });

        ClassTemplate ctFormatter = f_types.getTemplate("TestApp.Formatter");
        adapter.addMethod(ctFormatter.f_struct, "construct", STRING, VOID);

        MethodStructure mtFormatter = ctFormatter.ensureMethodStructure("construct", STRING);
        mtFormatter.setOps(new Op[]
            { // #0 = prefix
            new LSet(adapter.getPropertyConstId("TestApp.Formatter", "prefix"), 0),
            new Return_0()
            });

        MethodStructure mtToString = ctFormatter.ensureMethodStructure("to", VOID, STRING);
        mtToString.setOps(new Op[]
            {
            new Var(adapter.getClassTypeConstId("String")), // #0
            new LGet(adapter.getPropertyConstId("TestApp.Formatter", "prefix"), 0),
            new Var(adapter.getClassTypeConstId("String")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Add(0, 1, 0),
            new Return_1(0)
            });

        ClassTemplate ctPrPoint = f_types.getTemplate("TestApp.PrettyPoint");
        MethodStructure mtPrPConst = ctPrPoint.ensureMethodStructure("construct",
                new String[] {"Int64", "Int64", "String"});
        mtPrPConst.setOps(new Op[]
            { // #0 = x; #1 = y; #2 = prefix
            new Construct_N(adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {0, 1}),
            new Construct_1(adapter.getMethodConstId("TestApp.Formatter", "construct"),
                    2),
            new Return_0()
            });

        ClassTemplate ctPrRectangle = f_types.getTemplate("TestApp.PrettyRectangle");
        MethodStructure mtPrRConst = ctPrRectangle.ensureMethodStructure("construct",
                new String[] {"TestApp.Point", "TestApp.Point", "String"});
        mtPrRConst.setOps(new Op[]
            { // #0 = tl; #1 = br; #2 = prefix
            new Construct_N(adapter.getMethodConstId("TestApp.Rectangle", "construct"),
                    new int[] {0, 1}),
            new Construct_1(adapter.getMethodConstId("TestApp.Formatter", "construct"),
                    2),
            new Return_0()
            });

        MethodStructure ftTestMixin = ensureMethodStructure("testMixin", VOID);
        ftTestMixin.setOps(new Op[]
            {
            new X_Print(adapter.ensureValueConstantId("\n# in TestApp.testMixin() #")),
            new NVar(adapter.getClassTypeConstId("TestApp.PrettyPoint"),
                    adapter.ensureValueConstantId("prp")), // #0 (prp)
            new New_N(adapter.getMethodConstId("TestApp.PrettyPoint", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(1),
                            adapter.ensureValueConstantId(2),
                            adapter.ensureValueConstantId("*** ")
                    }, 0),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("TestApp.Point"),
                    adapter.ensureValueConstantId("p2")), // #1 (p2)
            new New_N(adapter.getMethodConstId("TestApp.Point", "construct"),
                    new int[] {
                            adapter.ensureValueConstantId(2),
                            adapter.ensureValueConstantId(1)
                    },
                    1),

            new NVar(adapter.getClassTypeConstId("TestApp.PrettyRectangle"),
                    adapter.ensureValueConstantId("prr")), // #2 (prr)
            new New_N(adapter.getMethodConstId("TestApp.PrettyRectangle", "construct"),
                    new int[] {0, 1, adapter.ensureValueConstantId("+++ ")}, 2),
            new X_Print(2),

            new Return_0(),
            });

        // --- run()
        MethodStructure mtRun = ensureMethodStructure("run", VOID, VOID);
        mtRun.setOps(new Op[]
            {
            new Call_00(-adapter.getMethodConstId("TestApp", "test1")),
            new Call_00(-adapter.getMethodConstId("TestApp", "test2")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testService")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testService2")),
            new Call_10(-adapter.getMethodConstId("TestApp", "testRef"),
                    adapter.ensureValueConstantId("hi")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testArray")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testTuple")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testConst")),
            new Call_00(-adapter.getMethodConstId("TestApp", "testMixin")),
            new Return_0()
            });
        }
    }
