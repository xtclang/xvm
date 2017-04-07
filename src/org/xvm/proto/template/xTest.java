package org.xvm.proto.template;

import org.xvm.proto.*;

import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTest extends xService
    {
    private final ConstantPoolAdapter adapter;

    public xTest(TypeSet types)
        {
        super(types, "x:Test", "x:Object", Shape.Class);

        adapter = types.f_constantPool;
        }

    @Override
    public void initDeclared()
        {
        ensurePropertyTemplate("prop1", "x:String");

        addFunctionTemplate("getIntValue", VOID, INT);
        addFunctionTemplate("getStringValue", VOID, STRING);
        addFunctionTemplate("test1", VOID, VOID);
        addFunctionTemplate("test2", VOID, VOID);
        addFunctionTemplate("construct", new String[]{"x:Test", "x:String"}, new String[]{"x:Function"});
        addFunctionTemplate("construct:finally", new String[]{"x:Test", "x:String"}, VOID);
        addFunctionTemplate("test3", VOID, VOID);
        addFunctionTemplate("throwing", STRING, VOID);
        addFunctionTemplate("test4", VOID, VOID);

        ensureMethodTemplate("method1", VOID, INT);

        add_getIntValue();
        add_getStringValue();

        add_test1();
        add_test2();

        add_construct();
        add_method1();

        add_test3();

        add_throwing();
        add_test4();
        }

    private void add_getIntValue()
        {
        FunctionTemplate ft = getFunctionTemplate("getIntValue", VOID, INT);
        //  static Int getIntValue()
        //      {
        //      return 99;                      // RETURN_1 -@99
        //      }
        ft.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValueId(99)),
            };
        ft.m_cVars = 1;
        }

    private void add_getStringValue()
        {
        FunctionTemplate ft = getFunctionTemplate("getStringValue", VOID, STRING);
        //  static String getStringValue()
        //      {
        //      return "Hello World!";          // RETURN_1 -@"Hello World"
        //      }
        ft.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValueId("Hello world!")),
            };
        ft.m_cVars = 0;
        }

    private void add_test1()
        {
        FunctionTemplate ft = getFunctionTemplate("test1", VOID, VOID);
        //  static Void test1()
        //      {
        //      String s = "Hello World!";      // IVAR x:String @"Hello World" (#0)
        //      print s;                        // PRINT #0
        //      }                               // RETURN
        ft.m_aop = new Op[]
            {
            new IVar(this.adapter.getClassConstId("x:String"), adapter.ensureConstantValueId("Hello world!")), // #0
            new X_Print(0),
            new Return_0(),
            };
        ft.m_cVars = 1;
        }

    private void add_test2()
        {
        FunctionTemplate ft = getFunctionTemplate("test2", VOID, VOID);
        //  static Void test2()
        //      {
        //      Int i = getIntValue();          // VAR x:Int64 (#0)
        //                                      // CALL_01 -@"x:Test#getIntValue" #0
        //      print i;                        // PRINT #0
        //      }                               // RETURN
        ft.m_aop = new Op[]
            {
            new Var(this.adapter.getClassConstId("x:Int64")), // #0
            new Call_01(-adapter.getMethodConstId("x:Test", "getIntValue"), 0),
            new X_Print(0),
            new Return_0(),
            };
        ft.m_cVars = 1;
        }

    private void add_construct()
        {
        FunctionTemplate ct = getFunctionTemplate("construct", new String[]{"x:Test", "x:String"}, new String[]{"x:Function"});
        FunctionTemplate ft = getFunctionTemplate("construct:finally", new String[]{"x:Test", "x:String"}, VOID);
        // construct xTest(String s)            // #0 = this:struct, #1 = s
        //      {
        //      this.prop1 = s;                 // SET #0, @"prop1" #1
        //      }                               // RETURN_1 -@"xTest#construct:finally"
        // finally
        //      {                               // #0 = this:private, #1 = s
        //      print this;                     // PRINT #0
        //      }                               // RETURN
        ct.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# in constructor: Test #")),
            new Set(0, adapter.getPropertyConstId("x:Test", "prop1"), 1),
            new Return_1(-adapter.getMethodConstId("x:Test", "construct:finally")),
            };
        ct.m_cVars = 2;

        ft.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# finally #")),
            new X_Print(0),
            new Return_0(),
            };
        ft.m_cVars = 2;
        }

    private void add_method1()
        {
        MethodTemplate mt = getMethodTemplate("method1", VOID, INT);
        //  Int method1()                       // #0 = this:private
        //      {
        //      String s = getStringValue();    // VAR x:String (#1)
        //                                      // CALL_01 -@"x:Test#getStringValue" #1
        //      Int of = s.indexOf("World");    // VAR x:Int (#2)
        //                                      // IVAR x:String @"world" (#3)
        //                                      // INVOKE_11 #1 -@"x:String#indexOf" #3 #2
        //      return of + s.length;           // VAR x:Int (#4)
        //                                      // GET #1 -@"x:String#length" #4
        //                                      // ADD #4 #2 #4
        //                                      // RETURN_01 #4
        //      }
        mt.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# in method1@Test #")),
            new Var(this.adapter.getClassConstId("x:String")), // #1
            new Call_01(-adapter.getMethodConstId("x:Test", "getStringValue"), 1), // should be FunctionConstId
            new Var(this.adapter.getClassConstId("x:Int64")), // #2
            new IVar(this.adapter.getClassConstId("x:String"), adapter.ensureConstantValueId("world")), // #3
            new Invoke_11(1, -adapter.getMethodConstId("x:String", "indexOf"), 3, 2),
            new Var(this.adapter.getClassConstId("x:Int64")), // #4
            new Get(1, adapter.getPropertyConstId("x:String", "length"), 4),
            new Add(4, 2, 4),
            new Return_1(4),
            };
        mt.m_cVars = 5;
        }

    private void add_test3()
        {
        FunctionTemplate ft = getFunctionTemplate("test3", VOID, VOID);
        //  static Void test3()
        //      {
        //      Test t = new Test("Hello");     // VAR x:Test (#0)
        //                                      // NEW_1 @"x:Test#construct" -@"Hello" #0
        //      print t.prop1                   // VAR x:String (#1)
        //                                      // GET #1  @"prop1" #1
        //                                      // PRINT #1
        //      print t.method1();              // VAR x:Int #2
        //                                      // INVOKE_01 #0 -@"x:Test#method1" #2
        //                                      // PRINT #2
        //      }                               // RETURN
        ft.m_aop = new Op[]
            {
            new Var(this.adapter.getClassConstId("x:Test")),     // #0
            new New_1(this.adapter.getMethodConstId("x:Test", "construct"), -adapter.ensureConstantValueId("Hello"), 0),
            new Var(this.adapter.getClassConstId("x:String")),   // #1
            new Get(0, adapter.getPropertyConstId("x:Test", "prop1"), 1),
            new X_Print(1),
            new Var(this.adapter.getClassConstId("x:Int64")),    // #2
            new Invoke_01(0, -adapter.getMethodConstId("x:Test", "method1"), 2),
            new X_Print(2),
            new X_Print(-adapter.ensureConstantValueId("# finished test3 #")),
            new Return_0(),
            };
        ft.m_cVars = 3;
        }

    private void add_throwing()
        {
        FunctionTemplate ft = getFunctionTemplate("throwing", STRING, VOID);
        //  static Void throwing(String s)  // #0 = s
        //      {
        //      throw new Exception(s);     // VAR x:Exception (#1)
        //                                  // NEW_N @"#x:Exception:construct" 2 0 -@"x:Nullable.Null" #1
        //                                  // THROW #1
        //      }
        ft.m_aop = new Op[]
            {
            new Var(this.adapter.getClassConstId("x:Exception")), // #1
            new New_N(this.adapter.getMethodConstId("x:Exception", "construct"),
                        new int[]{0, -adapter.getClassConstId("x:Nullable$Null")}, 1),
            new Throw(1),
            };
        ft.m_cVars = 2;
        }

    private void add_test4()
        {
        FunctionTemplate ft = getFunctionTemplate("test4", VOID, VOID);
        // static void test4()
        //      {
        //      try                             // 0) GUARD 1 x:Exception 5 (+5)
        //          {                           //
        //          Boolean f = true;           // 1) IVAR x:Boolean -@"Boolean$True"
        //          print f;                    // 2) print 0
        //          throwing("handled");        // 3) CALL_10 -@"x:Test#throwing" -@"handled"
        //          }                           // 4) ENDGUARD 8 (+4)
        //      catch (Exception e)             // 5) ENTER ; #0 = e
        //          {                           //
        //          print e;                    // 6) print 0
        //          }                           // 7) EXIT
        //                                      //
        //      throwing("unhandled");          // 8) CALL_00 -@"x:Test#throwing"
        //      return;                         // 9) RETURN
        //      }
        ft.m_aop = new Op[]
            {
            new GuardStart(new int[]{adapter.getClassConstId("x:Exception")}, new int[] {+5}),
            new IVar(this.adapter.getClassConstId("x:Boolean"), adapter.getClassConstId("x:Boolean$True")),
            new X_Print(0),
            new Call_10(-adapter.getMethodConstId("x:Test", "throwing"), -adapter.ensureConstantValueId("handled")),
            new GuardEnd(+4),
            new Enter(),
            new X_Print(0),
            new Exit(),
            new Call_10(-adapter.getMethodConstId("x:Test", "throwing"), -adapter.ensureConstantValueId("unhandled")),
            new Return_0(),
            };
        ft.m_cVars = 1;
        ft.m_cScopes = 2;
        }
    }
