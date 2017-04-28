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

        adapter = types.f_constantPool;
        }

    @Override
    public void initDeclared()
        {
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
            new IVar(adapter.getClassConstId("x:String"),
                     adapter.ensureValueConstantId("Hello world!")), // #0 (s)
            new X_Print(0),

            new Var(adapter.getClassConstId("x:Int64")), // #1 (i)
            new Call_01(-adapter.getMethodConstId("x:TestApp", "getIntValue"), 1),
            new X_Print(1),

            new Var(adapter.getClassConstId("x:Int64")), // #2 (of)
            new IVar(adapter.getClassConstId("x:String"),
                     adapter.ensureValueConstantId("world")), // #3
            new Invoke_11(0, -adapter.getMethodConstId("x:String", "indexOf"), 3, 2),

            new Var(adapter.getClassConstId("x:Int64")), // #4
            new Get(0, adapter.getPropertyConstId("x:String", "length"), 4),
            new Add(4, 2, 4),
            new X_Print(4),

            new Return_0(),
            };
        ftTest1.m_cVars = 5;

        // --- test2()

        FunctionTemplate ftTest2 = ensureFunctionTemplate("test2", VOID, VOID);
        ftTest2.m_aop = new Op[]
            {
            new NVar(adapter.getClassConstId("x:TestClass"), adapter.ensureValueConstantId("t")),  // #0 (t)
            new New_1(adapter.getMethodConstId("x:TestClass", "construct"),
                     -adapter.ensureValueConstantId("Hello World!"), 0),
            new Var(adapter.getClassConstId("x:String")),   // #1
            new Get(0, adapter.getPropertyConstId("x:TestClass", "prop1"), 1),
            new X_Print(1),
            new Var(adapter.getClassConstId("x:Int64")),    // #2
            new Invoke_01(0, -adapter.getMethodConstId("x:TestClass", "method1"), 2),
            new X_Print(2),

            new GuardStart(adapter.getClassConstId("x:Exception"),
                           adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(0, -adapter.getMethodConstId("x:TestClass", "throwing"),
                             -adapter.ensureValueConstantId("handled")),
            new GuardEnd(+4),
            new HandlerStart(), // #3 (e)
            new X_Print(3),
            new HandlerEnd(1),

            new NVar(adapter.getClassConstId("x:TestClass"), adapter.ensureValueConstantId("t2")), // #3 (t2)
            new New_N(adapter.getMethodConstId("x:TestClass2", "construct"),
                     new int[]{-adapter.ensureValueConstantId(42),
                              -adapter.ensureValueConstantId("Goodbye")}, 3),
            new Var(adapter.getClassConstId("x:String")),   // #4
            new Get(3, adapter.getPropertyConstId("x:TestClass", "prop1"), 4),
            new X_Print(4),
            new Var(adapter.getClassConstId("x:Int64")),    // #5
            new Invoke_01(3, -adapter.getMethodConstId("x:TestClass", "method1"), 5),
            new X_Print(5),

            new NVar(adapter.getClassConstId("x:Function"), adapter.ensureValueConstantId("fn")), // #6 (fn)
            new MBind(3, -adapter.getMethodConstId("x:TestClass", "method1"), 6),
            new Var(adapter.getClassConstId("x:Int64")),    // #7
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
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.lambda$1 #")),
            new X_Print(0),
            new X_Print(1),
            new X_Print(2),
            };
        ftLambda$1.m_cVars = 3;

        FunctionTemplate ftTestService = ensureFunctionTemplate("testService", VOID, VOID);
        ftTestService.m_aop = new Op[]
            {
            new Var(adapter.getClassConstId("x:TestService")),     // #0
            new New_1(adapter.getMethodConstId("x:TestService", "construct"),
                     -adapter.ensureValueConstantId(48), 0),
            new X_Print(0),

            new Var(adapter.getClassConstId("x:Int64")),        // #1 (c)
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "increment"), 1),
            new X_Print(1),

            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"),
                      -adapter.ensureValueConstantId(17)),
            new Get(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new X_Print(1),

            new NVar(adapter.getClassConstId("x:Function"),
                     adapter.ensureValueConstantId("fnInc")),   // #2 (fnInc)
            new MBind(0, -adapter.getMethodConstId("x:TestService", "increment"), 2),
            new Call_01(2, 1),
            new X_Print(1),

            new DNVar(adapter.getClassConstId("x:FutureRef<Int>"),
                      adapter.ensureValueConstantId("fc")), // #3 (fc)
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "increment"), 3),
            new NVar(adapter.getClassConstId("x:FutureRef<Int>"),
                     adapter.ensureValueConstantId("rfc")), // #4 (rfc)
            new MoveRef(3, 4),
            new X_Print(4),
            new X_Print(3),
            new X_Print(4),

            new NVar(adapter.getClassConstId("x:FutureRef<Int>"),
                     adapter.ensureValueConstantId("rfc")), // #5 (rfc2)
            new DVar(adapter.getClassConstId("x:FutureRef<Int>")), // #6
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "increment"), 6),
            new MoveRef(6, 5),

            new IVar(adapter.getClassConstId("x:Function"),
                     adapter.getMethodConstId("x:TestApp", "lambda$1")), // #7
            new FBind(7, new int[] {0}, new int[] {1}, 7),
            new Invoke_10(5, -adapter.getMethodConstId("x:FutureRef", "whenComplete"), 7),

            new GuardStart(adapter.getClassConstId("x:Exception"),
                           adapter.ensureValueConstantId("e"), +3),
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "throwing"), 1),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new GuardStart(adapter.getClassConstId("x:Exception"),
                           adapter.ensureValueConstantId("e"), +3),
            new Invoke_10(4, -adapter.getMethodConstId("x:FutureRef", "set"),
                             -adapter.ensureValueConstantId(99)),
            new GuardEnd(+4),
            new HandlerStart(), // #8 (e)
            new X_Print(8),
            new HandlerEnd(1),

            new Invoke_00(0, -adapter.getMethodConstId("x:TestService", "throwing")),
            new Return_0(),
            };
        ftTestService.m_cVars = 9;
        ftTestService.m_cScopes = 2;

        // --- testRef()

        FunctionTemplate ftTestRef = ensureFunctionTemplate("testRef", VOID, VOID);
        ftTestRef.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureValueConstantId("\n# in TestApp.testRef() #")),
            new NVar(adapter.getClassConstId("x:Ref"), adapter.ensureValueConstantId("ri")),     // #0 (ri)
            new Enter(),
            new INVar(adapter.getClassConstId("x:Int64"),
                    adapter.ensureValueConstantId("i"), adapter.ensureValueConstantId(1)),     // #1 (i)
            new NVar(adapter.getClassConstId("x:Ref"), adapter.ensureValueConstantId("ri2")),  // #2 (ri2)
            new MoveRef(1, 2),
            new MoveRef(1, 0),

            new Var(adapter.getClassConstId("x:Int64")), // #3 (temp)
            new Invoke_01(0, -adapter.getMethodConstId("x:Ref", "get"), 3),
            new X_Print(3),

            new Invoke_10(0, -adapter.getMethodConstId("x:Ref", "set"),
                             -adapter.ensureValueConstantId(2)),
            new X_Print(1),

            new Move(-adapter.ensureValueConstantId(3), 1),
            new X_Print(2),
            new Exit(),

            new Var(adapter.getClassConstId("x:Int64")), // #1 (temp)
            new Invoke_01(0, -adapter.getMethodConstId("x:Ref", "get"), 1),
            new X_Print(1),
            new Return_0()
            };
        ftTestRef.m_cVars = 4;
        ftTestRef.m_cScopes = 2;

        // --- run()
        MethodTemplate mtRun = ensureMethodTemplate("run", VOID, VOID);
        mtRun.m_aop = new Op[]
            {
            new Call_00(-adapter.getMethodConstId("x:TestApp", "test1")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "test2")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testService")),
            new Call_00(-adapter.getMethodConstId("x:TestApp", "testRef")),
            new Return_0()
            };
        mtRun.m_cVars = 2;
        }
    }
