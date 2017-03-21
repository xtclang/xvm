package org.xvm.proto.template;

import org.xvm.proto.*;

import org.xvm.proto.op.*;

/**
 * TODO:
 *
 * @author gg 2017.03.15
 */
public class xTest extends xObject
    {
    private final ConstantPoolAdapter f_adapter;

    public xTest(TypeSet types, ConstantPoolAdapter adapter)
        {
        super(types, "x:Test", "x:Object", Shape.Class);

        f_adapter = adapter;
        }

    @Override
    public void initDeclared()
        {
        ConstantPoolAdapter adapter = f_adapter;

        // TypeCompositionTemplate tStruct = f_types.addTemplate();
        addPropertyTemplate("prop1", "x:String");

        add_getIntValue(adapter);
        add_getStringValue(adapter);

        add_test1(adapter);
        add_test2(adapter);

        add_construct(adapter);
        add_method1(adapter);

        add_test3(adapter);
        }

    private void add_getIntValue(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ft = addFunctionTemplate("getIntValue", VOID, INT);
        //  static Int getIntValue()
        //      {
        //      return 99; // RETURN_1 -@99
        //      }
        ft.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValue(99)),
            };
        ft.m_cVars = 1;
        }

    private void add_getStringValue(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ft = addFunctionTemplate("getStringValue", VOID, STRING);
        //  static String getStringValue()
        //      {
        //      return "Hello World!"; // RETURN_1 -@"Hello World"
        //      }
        ft.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValue("Hello world!")),
            };
        ft.m_cVars = 0;
        }

    private void add_test1(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ft = addFunctionTemplate("test1", VOID, VOID);
        //  static Void test1()
        //      {
        //      String s = "Hello World!";  // IVAR x:String @"Hello World" (#0)
        //      print s;                    // PRINT #0
        //      }                           // RETURN
        ft.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### test1 ###")),
            new IVar(adapter.getClassConstId("x:String"), adapter.ensureConstantValue("Hello world!")), // #0
            new X_Print(0),
            new Return(),
            };
        ft.m_cVars = 1;
        }

    private void add_test2(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ft = addFunctionTemplate("test2", VOID, VOID);
        //  static Void test2()
        //      {
        //      Int i = getIntValue(); // VAR x:Int64 (#0)
        //                             // CALL_01 -@"x:Test#getIntValue" #0
        //      print i;               // PRINT #0
        //      }                      // RETURN
        ft.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### test2 ###")),
            new Var(adapter.getClassConstId("x:Int64")), // #0
            new Call_01(-adapter.getMethodConstId("x:Test", "getIntValue"), 0),
            new X_Print(0),
            new Return(),
            };
        ft.m_cVars = 1;
        }

    private void add_construct(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ct = addFunctionTemplate("construct", new String[]{"x:Test", "x:String"}, new String[]{"x:Function"});
        FunctionTemplate ft = addFunctionTemplate("construct:finally", new String[]{"x:Test", "x:String"}, VOID);
        // construct xTest(String s) // #0 = this:struct, #1 = s
        //      {
        //      this.prop1 = s;      // SET #0, @"prop1" #1
        //      }                    // RETURN_1 -@"xTest#construct:finally"
        // finally
        //      {                    // #0 = this:private, #1 = s
        //      print this;          // PRINT #0
        //      }                    // RETURN
        ct.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### construct ###")),
            new Set(0, adapter.ensureConstantValue("prop1"), 1),
            new Return_1(-adapter.getMethodConstId("x:Test", "construct:finally")),
            };
        ct.m_cVars = 2;

        ft.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### finally ###")),
            new X_Print(0),
            new Return(),
            };
        ft.m_cVars = 2;
        }

    private void add_method1(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("method1", VOID, INT);
        //  Int method1()                    // #0 = this:private
        //      {
        //      String s = getStringValue(); // VAR x:String (#1)
        //                                   // CALL_01 -@"x:Test#getStringValue" #1
        //      Int of = s.indexOf("World"); // VAR x:Int (#2)
        //                                   // IVAR x:String @"world" (#3)
        //                                   // INVOKE_11 #1 -@"x:String#indexOf" #3 #2
        //      return of;                   // RETURN_01 #2
        //      }
        mt.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### method1 ###")),
            new Var(adapter.getClassConstId("x:String")), // #1
            new Call_01(-adapter.getMethodConstId("x:Test", "getStringValue"), 1), // should be FunctionConstId
            new Var(adapter.getClassConstId("x:Int64")), // #2
            new IVar(adapter.getClassConstId("x:String"), adapter.ensureConstantValue("world")), // #3
            new Invoke_11(1, -adapter.getMethodConstId("x:String", "indexOf"), 3, 2),
            new Return_1(2),
            };
        mt.m_cVars = 4;
        }

    private void add_test3(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ft = addFunctionTemplate("test4", VOID, VOID);
        //  static Void test3()
        //      {
        //      Test t = new Test("Hello");  // VAR x:Test (#0)
        //                                   // NEW_1 @"xTest#construct" -@"Hello" #0
        //      print t.prop1                // VAR x:String (#1)
        //                                   // GET #1  @"prop1" #1
        //                                   // PRINT #1
        //      print t.method1();           // VAR x:Int #2
        //                                   // INVOKE_01 #0 -@"x:Test#method1" #2
        //                                   // PRINT #2
        //      }                            // RETURN
        ft.m_aop = new Op[]
            {
            new Var(adapter.getClassConstId("x:Test")),     // #0
            new New_1(adapter.getMethodConstId("x:Test", "construct"), -adapter.ensureConstantValue("Hello"), 0),
            new Var(adapter.getClassConstId("x:String")),   // #1
            new Get(0, adapter.ensureConstantValue("prop1"), 1),
            new X_Print(1),
            new Var(adapter.getClassConstId("x:Int64")),    // #2
            new Invoke_01(0, -adapter.getMethodConstId("x:Test", "method1"), 2),
            new X_Print(-adapter.ensureConstantValue("### test3 ###")),
            new X_Print(2),
            new Return(),
            };
        ft.m_cVars = 3;
        }


    ///////////////////////////////////////

    public void runTests(ServiceContext context)
        {
        forEachFunction (ft ->
            {
            if (ft.f_sName.startsWith("test"))
                {
                ObjectHandle[] ahVars = new ObjectHandle[ft.m_cVars];
                ObjectHandle[] ahReturn = new ObjectHandle[ft.m_cReturns];

                Frame frame = context.createFrame(null, null, ft, ahVars, ahReturn);
                frame.execute();
                }
            });
        }
    }
