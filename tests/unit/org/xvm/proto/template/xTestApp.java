package org.xvm.proto.template;

import org.xvm.asm.Constants;
import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTestApp extends xModule
    {
    private final ConstantPoolAdapter adapter;

    public xTestApp(TypeSet types)
        {
        super(types, "x:TestApp", "x:Object", Shape.Class);

        adapter = types.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        m_fAutoRegister = true;

        f_types.ensureTemplate("x:TestClass");
        f_types.ensureTemplate("x:TestClass2");
        f_types.ensureTemplate("x:TestService");

        // --- getIntValue
        FunctionTemplate ftGetInt = ensureFunctionTemplate("getIntValue", VOID, INT);
        ftGetInt.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureValueConstantId(42)),
            };
        ftGetInt.m_cVars = 1;

        // --- test1()
        FunctionTemplate ftTest1 = ensureFunctionTemplate("test1", VOID, VOID);
        ftTest1.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.test1() #")),
            new INVar(adapter.getClassTypeConstId("x:String"),
                      adapter.ensureValueConstantId("s"),
                      -adapter.ensureValueConstantId("Hello world!")), // #0 (s)
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("x:Int64"),
                     adapter.ensureValueConstantId("i")), // #1 (i)
            new Call_01(-adapter.getMethodConstId("x:TestApp", "getIntValue"), 1),
            new X_Print(1),

            new NVar(adapter.getClassTypeConstId("x:Int64"),
                     adapter.ensureValueConstantId("of")), // #2 (of)
            new IVar(adapter.getClassTypeConstId("x:String"),
                     -adapter.ensureValueConstantId("world")), // #3
            new Invoke_11(0, adapter.getMethodConstId("x:String", "indexOf"), 3, 2),

            new Var(adapter.getClassTypeConstId("x:Int64")), // #4
            new PGet(0, adapter.getPropertyConstId("x:String", "length"), 4),
            new Add(4, 2, 4),
            new X_Print(4),

            new Var(adapter.getClassTypeConstId("x:String")), // #5
            new Invoke_01(2, adapter.getMethodConstId("x:Int64", "x:String to(Void)"), 5),
            new X_Print(5),

            new Return_0(),
            };
        ftTest1.m_cVars = 6;

        // --- test2()

        FunctionTemplate ftTest2 = ensureFunctionTemplate("test2", VOID, VOID);
        ftTest2.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.test2() #")),
            new NVar(adapter.getClassTypeConstId("x:TestClass"), adapter.ensureValueConstantId("t")),  // #0 (t)
            new New_1(adapter.getMethodConstId("x:TestClass", "construct"),
                     -adapter.ensureValueConstantId("Hello World!"), 0),
            new Var(adapter.getClassTypeConstId("x:String")),   // #1
            new PGet(0, adapter.getPropertyConstId("x:TestClass", "prop1"), 1),
            new X_Print(1),
            new Var(adapter.getClassTypeConstId("x:Int64")),    // #2
            new Invoke_01(0, adapter.getMethodConstId("x:TestClass", "method1"), 2),
            new X_Print(2),

            new GuardStart(adapter.getClassTypeConstId("x:Exception"),
                           adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(0, adapter.getMethodConstId("x:TestClass", "exceptional"),
                             -adapter.ensureValueConstantId("handled")),
            new GuardEnd(+4),
            new HandlerStart(), // #3 (e)
            new X_Print(3),
            new HandlerEnd(1),

            new NVar(adapter.getClassTypeConstId("x:TestClass"), adapter.ensureValueConstantId("t2")), // #3 (t2)
            new New_N(adapter.getMethodConstId("x:TestClass2", "construct"),
                     new int[]{-adapter.ensureValueConstantId(42),
                              -adapter.ensureValueConstantId("Goodbye")}, 3),
            new Var(adapter.getClassTypeConstId("x:String")),   // #4
            new PGet(3, adapter.getPropertyConstId("x:TestClass", "prop1"), 4),
            new X_Print(4),
            new Var(adapter.getClassTypeConstId("x:Int64")),    // #5
            new Invoke_01(3, adapter.getMethodConstId("x:TestClass", "method1"), 5),
            new X_Print(5),

            new NVar(adapter.getClassTypeConstId("x:Function"), adapter.ensureValueConstantId("fn")), // #6 (fn)
            new MBind(3, adapter.getMethodConstId("x:TestClass", "method1"), 6),
            new Var(adapter.getClassTypeConstId("x:Int64")),    // #7
            new Call_01(6, 7),
            new X_Print(7),

            new Return_0(),
            };
        ftTest2.m_cVars = 10;
        ftTest2.m_cScopes = 2;

        // --- testService()

        FunctionTemplate ftLambda$1 = ensureFunctionTemplate("lambda$1",
                new String[] {"x:Int64", "x:Int64", "x:Exception"}, VOID);
        ftLambda$1.setAccess(Constants.Access.PRIVATE);
        ftLambda$1.m_aop = new Op[]
            { // #0 = c; #1 = r, #2 = x
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.lambda$1 (rfc2.whenComplete) #")),
            new X_Print(0),
            new X_Print(1),
            new X_Print(2),
            new Return_0(),
            };
        ftLambda$1.m_cVars = 3;

        FunctionTemplate ftTestService = ensureFunctionTemplate("testService", VOID, VOID);
        ftTestService.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testService() #")),
            new NVar(adapter.getClassTypeConstId("x:TestService"),
                    adapter.ensureValueConstantId("svc")),     // #0
            new New_1(adapter.getMethodConstId("x:TestService", "construct"),
                     -adapter.ensureValueConstantId(48), 0),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("x:Int64"),
                    adapter.ensureValueConstantId("c")),        // #1 (c)
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 1),
            new X_Print(1),

            new PSet(0, adapter.getPropertyConstId("x:TestService", "counter"),
                      -adapter.ensureValueConstantId(17)),
            new PGet(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new X_Print(1),

            new NVar(adapter.getClassTypeConstId("x:Function"),
                     adapter.ensureValueConstantId("fnInc")),   // #2 (fnInc)
            new MBind(0, adapter.getMethodConstId("x:TestService", "increment"), 2),
            new Call_01(2, 1),
            new X_Print(1),

            new DNVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                      adapter.ensureValueConstantId("fc")), // #3 (fc)
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 3),
            new NVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                     adapter.ensureValueConstantId("rfc")), // #4 (rfc)
            new MoveRef(3, 4),
            new X_Print(4),
            new X_Print(3),
            new X_Print(4),

            new NVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                     adapter.ensureValueConstantId("rfc")), // #5 (rfc2)
            new DNVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                      adapter.ensureValueConstantId("rfc3")), // #6 (rfc3)
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 6),
            new MoveRef(6, 5),

            new IVar(adapter.getClassTypeConstId("x:Function"),
                     -adapter.getMethodConstId("x:TestApp", "lambda$1")), // #7
            new FBind(7, new int[] {0}, new int[] {1}, 7),
            new Invoke_10(5, adapter.getMethodConstId("x:FutureRef", "whenComplete"), 7),

            new GuardStart(adapter.getClassTypeConstId("x:Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Invoke_11(0, adapter.getMethodConstId("x:TestService", "exceptional"),
                             -adapter.ensureValueConstantId(0), 1),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new GuardStart(adapter.getClassTypeConstId("x:Exception"),
                           adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(4, adapter.getMethodConstId("x:FutureRef", "set"),
                             -adapter.ensureValueConstantId(99)),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new Var(adapter.getClassTypeConstId("x:Int64")), // #8
            new PPreInc(0, adapter.getPropertyConstId("x:TestService", "counter2"), 8),
            new X_Print(8),
            new PPostInc(0, adapter.getPropertyConstId("x:TestService", "counter"), 8),
            new X_Print(8),
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 8),
            new X_Print(8),

            new Invoke_00(Op.A_SERVICE, adapter.getMethodConstId("x:TestService", "yield")),

            new Invoke_10(0, adapter.getMethodConstId("x:TestService", "exceptional"),
                            -adapter.ensureValueConstantId(0)),
            new Return_0(),
            };
        ftTestService.m_cVars = 9;
        ftTestService.m_cScopes = 2;

        // --- testService2 ---

        FunctionTemplate ftTestReturn = ensureFunctionTemplate("testBlockingReturn",
                new String[] {"x:TestService"}, INT);
        ftTestReturn.setAccess(Constants.Access.PRIVATE);
        ftTestReturn.m_aop = new Op[]
            { // #0 = svc
            new Var(adapter.getClassTypeConstId("x:Int64")), // #1
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 1),
            new Return_1(1),
            };
        ftTestReturn.m_cVars = 2;

        FunctionTemplate ftTestService2 = ensureFunctionTemplate("testService2", VOID, VOID);
        ftTestService2.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testService2() #")),
            new NVar(adapter.getClassTypeConstId("x:TestService"),
                    adapter.ensureValueConstantId("svc")),     // #0
            new New_1(adapter.getMethodConstId("x:TestService", "construct"),
                    -adapter.ensureValueConstantId(48), 0),

            new IVar(adapter.getClassTypeConstId("x:Function"),
                     -adapter.getMethodConstId("x:TestApp", "testBlockingReturn")), // #1
            new FBind(1, new int[] {0}, new int[] {0}, 1),

            new NVar(adapter.getClassTypeConstId("x:Int64"),
                     adapter.ensureValueConstantId("c")), // #2
            new Call_01(1, 2),
            new X_Print(2),

            new DNVar(adapter.getClassTypeConstId("x:FutureRef<x:Int64>"),
                      adapter.ensureValueConstantId("fc")), // #3 (fc)
            new Invoke_01(0, adapter.getMethodConstId("x:TestService", "increment"), 3),
            new X_Print(3),

            new GuardStart(adapter.getClassTypeConstId("x:Exception"),
                    adapter.ensureValueConstantId("e"), +3),
            new Call_01(-adapter.getMethodConstId("x:TestApp", "getIntValue"), 3),
            new GuardEnd(+4),
            new HandlerStart(), // #4 (e)
            new X_Print(4),
            new HandlerEnd(1),

            new Invoke_10(Op.A_SERVICE, adapter.getMethodConstId("x:TestService", "registerTimeout"),
                    -adapter.ensureValueConstantId(2000)),
            new GuardAll(+4),
            new Invoke_11(0, adapter.getMethodConstId("x:TestService", "exceptional"),
                    -adapter.ensureValueConstantId(1000), 2),
            new X_Print(2),
            new FinallyStart(),
            new Invoke_10(Op.A_SERVICE, adapter.getMethodConstId("x:TestService", "registerTimeout"),
                    -adapter.ensureValueConstantId(0)),
            new FinallyEnd(),

            new Invoke_10(Op.A_SERVICE, adapter.getMethodConstId("x:TestService", "registerTimeout"),
                    -adapter.ensureValueConstantId(500)),
            new GuardAll(+9),
            new GuardStart(adapter.getClassTypeConstId("x:Exception"),
                    adapter.ensureValueConstantId("e"), +4),
            new Invoke_11(0, adapter.getMethodConstId("x:TestService", "exceptional"),
                    -adapter.ensureValueConstantId(200000), 2),
            new Assert(-adapter.ensureValueConstantId(false)),
            new GuardEnd(+4),
            new HandlerStart(), // #4 (e)
            new X_Print(4),
            new HandlerEnd(1),
            new FinallyStart(),
            new Invoke_10(Op.A_SERVICE, adapter.getMethodConstId("x:TestService", "registerTimeout"),
                    -adapter.ensureValueConstantId(0)),
            new FinallyEnd(),
            new Return_0(),
            };
        ftTestService2.m_cVars = 5;
        ftTestService2.m_cScopes = 3;

        // --- testRef()

        FunctionTemplate ftTestRef = ensureFunctionTemplate("testRef", STRING, VOID);
        ftTestRef.m_aop = new Op[]
            { // #0 = arg
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testRef() #")),

            new NVar(adapter.getClassTypeConstId("x:Ref<x:String>"),
                     adapter.ensureValueConstantId("ra")), // #1 (ra)
            new MoveRef(0, 1),

            new Var(adapter.getClassTypeConstId("x:String")), // #2
            new Invoke_01(1, adapter.getMethodConstId("x:Ref", "get"), 2),
            new X_Print(2),
            new Invoke_10(1, adapter.getMethodConstId("x:Ref", "set"),
                             -adapter.ensureValueConstantId("bye")),
            new X_Print(0),

            new NVar(adapter.getClassTypeConstId("x:Ref"),
                     adapter.ensureValueConstantId("ri")), // #3 (ri)
            new Enter(),
            new INVar(adapter.getClassTypeConstId("x:Int64"),
                      adapter.ensureValueConstantId("i"),
                      -adapter.ensureValueConstantId(1)), // #4 (i)
            new NVar(adapter.getClassTypeConstId("x:Ref"),
                     adapter.ensureValueConstantId("ri2")), // #5 (ri2)
            new MoveRef(4, 5),
            new MoveRef(4, 3),

            new Var(adapter.getClassTypeConstId("x:Int64")), // #6
            new Invoke_01(3, adapter.getMethodConstId("x:Ref", "get"), 6),
            new X_Print(6),

            new Invoke_10(3, adapter.getMethodConstId("x:Ref", "set"),
                             -adapter.ensureValueConstantId(2)),
            new X_Print(4),

            new Move(-adapter.ensureValueConstantId(3), 4),
            new X_Print(5),
            new Exit(),

            new Var(adapter.getClassTypeConstId("x:Int64")), // #4
            new Invoke_01(3, adapter.getMethodConstId("x:Ref", "get"), 4),
                    new X_Print(4),

            new Return_0()
            };
        ftTestRef.m_cVars = 7;
        ftTestRef.m_cScopes = 2;

        // --- testArray()

        FunctionTemplate ftLambda$2 = ensureFunctionTemplate("lambda$2", INT, STRING);
        ftLambda$2.setAccess(Constants.Access.PRIVATE);
        ftLambda$2.m_aop = new Op[]
            { // #0 = i
            new IVar(adapter.getClassTypeConstId("x:String"),
                     -adapter.ensureValueConstantId("value ")), // #1
            new Var(adapter.getClassTypeConstId("x:String")), // #2
            new Invoke_01(0, adapter.getMethodConstId("x:Int64", "x:String to(Void)"), 2),
            new Add(1, 2, 1),
            new Return_1(1)
            };
        ftLambda$2.m_cVars = 3;

        FunctionTemplate ftTestArray = ensureFunctionTemplate("testArray", VOID, VOID);
        ftTestArray.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testArray() #")),
            new NVar(adapter.getClassTypeConstId("x:collections.Array<x:Int64>"),
                     adapter.ensureValueConstantId("ai")),   // #0 (ai)
            new New_1G(adapter.getMethodConstId("x:collections.Array", "construct"),
                       -adapter.getClassTypeConstId("x:collections.Array<x:Int64>"),
                       -adapter.ensureValueConstantId(0), 0),
            new ISet(0, -adapter.ensureValueConstantId(0), -adapter.ensureValueConstantId(1)),
            new ISet(0, -adapter.ensureValueConstantId(1), -adapter.ensureValueConstantId(2)),
            new X_Print(0),

            new Var(adapter.getClassTypeConstId("x:Int64")), // #1
            new IGet(0, -adapter.ensureValueConstantId(0), 1),
            new X_Print(1),

            new IPreInc(0, -adapter.ensureValueConstantId(1), 1),
            new X_Print(1),

            new NVar(adapter.getClassTypeConstId("x:collections.Array<x:String>"),
                     adapter.ensureValueConstantId("as")),   // #2 (as)
            new New_NG(adapter.getMethodConstId("x:collections.Array", "construct"),
                       -adapter.getClassTypeConstId("x:collections.Array<x:String>"),
                       new int[] {-adapter.ensureValueConstantId(5),
                                  -adapter.getMethodConstId("x:TestApp", "lambda$2")}, 2),
            new X_Print(2),

            new Var(adapter.getClassTypeConstId("x:String")), // #3
            new IGet(2, -adapter.ensureValueConstantId(4), 3),
            new X_Print(3),

            new Var(adapter.getClassTypeConstId("x:Ref<x:String>")), // #4
            new IRef(2, -adapter.ensureValueConstantId(0), 4),
            new Invoke_01(4, adapter.getMethodConstId("x:Ref", "get"), 3),
            new X_Print(3),
            new Invoke_10(4, adapter.getMethodConstId("x:Ref", "set"),
                             -adapter.ensureValueConstantId("zero")),
            new IGet(2, -adapter.ensureValueConstantId(0), 3),
            new X_Print(3),

            new Return_0()
            };
        ftTestArray.m_cVars = 5;

        FunctionTemplate ftTestCond = ensureFunctionTemplate("testConditional",
                INT, new String[] {"x:Boolean", "x:String"});
        ftTestCond.m_aop = new Op[]
            { // #0 - i
            new Var(adapter.getClassTypeConstId("x:Boolean")), // #1
            new IsGt(0, -adapter.ensureValueConstantId(0), 1),
            new JumpFalse(1, 2),
            new Return_N(new int[] {
                    -adapter.ensureValueConstantId(true),
                    -adapter.ensureValueConstantId("positive")}),
            new Return_1(-adapter.ensureValueConstantId(false))
            };
        ftTestCond.m_cVars = 2;

        FunctionTemplate ftTestTuple = ensureFunctionTemplate("testTuple", VOID, VOID);
        ftTestTuple.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testTuple() #")),
            new INVar(adapter.getClassTypeConstId("x:Tuple"), adapter.ensureValueConstantId("t"),
                      -adapter.ensureValueConstantId(new Object[] {"zero", Integer.valueOf(0)})), // #0 (t)

            new Enter(),
            new Var(adapter.getClassTypeConstId("x:String")), // #1
            new IGet(0, -adapter.ensureValueConstantId(0), 1),
            new X_Print(1),
            new Exit(),

            new Enter(),
            new Var(adapter.getClassTypeConstId("x:Int64")), // #1
            new IGet(0, -adapter.ensureValueConstantId(1), 1),
            new X_Print(1),
            new Exit(),

            new INVar(adapter.getClassTypeConstId("x:Int64"),
                      adapter.ensureValueConstantId("i"), -adapter.ensureValueConstantId(0)), // #1 (i)
            new NVar(adapter.getClassTypeConstId("x:Tuple<x:String,x:Int64>"),
                     adapter.ensureValueConstantId("t2")), // #2 (t2)
            new TVar(new int[] {adapter.getClassTypeConstId("x:String"), adapter.getClassTypeConstId("x:Int64")},
                    new int[] {-adapter.ensureValueConstantId(""), 1}), // #3
            new New_1(adapter.getMethodConstId("x:Tuple", "construct"), 3, 2),
            new X_Print(2),

            new ISet(2, -adapter.ensureValueConstantId(0), -adapter.ensureValueConstantId("t")),
            new ISet(2, -adapter.ensureValueConstantId(1), -adapter.ensureValueConstantId(2)),
            new X_Print(2),

            new NVar(adapter.getClassTypeConstId("x:Int64"),
                     adapter.ensureValueConstantId("of")), // #4 (of)
            new Invoke_T1(-adapter.ensureValueConstantId("the test"),
                          adapter.getMethodConstId("x:String", "indexOf"), 2, 4),

            new Var(adapter.getClassTypeConstId("x:Boolean")), // #5
            new IsEq(4, -adapter.ensureValueConstantId(4), 5),
            new AssertV(5, adapter.ensureValueConstantId("of == 4"), new int[] {4}),

            new Enter(),
            new Var(adapter.getClassTypeConstId("x:Boolean")), // #6
            new NVar(adapter.getClassTypeConstId("x:String"),
                     adapter.ensureValueConstantId("s")), // #7
            new Call_1N(-adapter.getMethodConstId("x:TestApp", "testConditional"),
                        -adapter.ensureValueConstantId(1), new int[] {6, 7}),
            new JumpFalse(6, 2),
            new X_Print(7),
            new Exit(),

            new Var(adapter.getClassTypeConstId("x:Tuple")), // #6
            new Call_1T(-adapter.getMethodConstId("x:TestApp", "testConditional"),
                        -adapter.ensureValueConstantId(-1), 6),
            new X_Print(6),

            new Return_0()
            };
        ftTestTuple.m_cVars = 8;
        ftTestTuple.m_cScopes = 2;

        // --- run()
        MethodTemplate mtRun = ensureMethodTemplate("run", VOID, VOID);
        mtRun.m_aop = new Op[]
            {
            new Call_00(-adapter.getMethodConstId("x:TestApp", "test1")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "test2")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testService")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testService2")),
            new Call_10(-adapter.getMethodConstId("x:TestApp", "testRef"),
                        -adapter.ensureValueConstantId("hi")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testArray")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testTuple")),
            new Return_0()
            };
        mtRun.m_cVars = 2;
        }
    }
