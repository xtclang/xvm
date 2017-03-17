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
        add_test3(adapter);
        }

    private void add_getIntValue(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("getIntValue", VOID, INT);
        //  Int getIntValue() // #0 = this:private
        //      {
        //      return 99; // RETURN_1 -@99
        //      }
        mt.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValue(99)),
            };
        mt.m_cVars = 1;
        mt.m_anRetTypeId = new int[]{adapter.getClassConstId("x:Int64")};
        }

    private void add_getStringValue(ConstantPoolAdapter adapter)
        {
        FunctionTemplate mt = addFunctionTemplate("getStringValue", VOID, STRING);
        //  static String getStringValue()
        //      {
        //      return "Hello World!"; // RETURN_1 -@"Hello World"
        //      }
        mt.m_aop = new Op[]
            {
            new Return_1(-adapter.ensureConstantValue("Hello world!")),
            };
        mt.m_cVars = 0;
        mt.m_anRetTypeId = new int[]{adapter.getClassConstId("x:String")};
        }

    private void add_test1(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("test1", VOID, VOID);
        //  Void test1()                    // #0 = this:private
        //      {
        //      String s = "Hello World!";  // IVAR x:String @"Hello World" (#1)
        //      print s;                    // PRINT #1
        //      }                           // RETURN
        mt.m_aop = new Op[]
            {
            new IVar(adapter.getClassConstId("x:String"), adapter.ensureConstantValue("Hello world!")), // #1
            new X_Print(1),
            new Return(),
            };
        mt.m_cVars = 2;
        }

    private void add_test2(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("test2", VOID, VOID);
        //  Void test2()               // #0 = this:private
        //      {
        //      Int i = getIntValue(); // VAR x:Int64 (#1)
        //                             // INVOKE_01 #0 -@"x:Test#getIntValue" #1
        //      print i;               // PRINT #1
        //      }                      // RETURN
        mt.m_aop = new Op[]
            {
            new Var(adapter.getClassConstId("x:Int64")), // #1
            new Invoke_01(0, -adapter.getMethodConstId("x:Test", "getIntValue"), 1),
            new X_Print(1),
            new Return(),
            };
        mt.m_cVars = 2;
        }

    private void add_test3(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("test3", VOID, VOID);
        //  Void test3()                     // #0 = this:private
        //      {
        //      String s = getStringValue(); // VAR x:String (#1)
        //                                   // CALL_01 #0 -@"x:Test#getStringValue" #1
        //      Int of = s.indexOf("World"); // VAR x:Int (#2)
        //                                   // IVAR x:String @"world" (#3)
        //                                   // INVOKE_11 #1 -@"x:String#indexOf" #3 #2
        //      print of;                    // PRINT #2
        //      }                            // RETURN
        mt.m_aop = new Op[]
            {
            new Var(adapter.getClassConstId("x:String")), // #1
            new Call_01(-adapter.getMethodConstId("x:Test", "getStringValue"), 1), // FunctionConstId?
            new Var(adapter.getClassConstId("x:Int64")), // #2
            new IVar(adapter.getClassConstId("x:String"), adapter.ensureConstantValue("world")), // #3
            new Invoke_11(1, -adapter.getMethodConstId("x:String", "indexOf"), 3, 2),
            new X_Print(2),
            new Return(),
            };
        mt.m_cVars = 4;
        }

    private void add_construct(ConstantPoolAdapter adapter)
        {
        FunctionTemplate mt  = addFunctionTemplate("construct", new String[]{"x:Test", "x:String"}, new String[]{"x:Function"});
        FunctionTemplate mtf = addFunctionTemplate("construct:finally", new String[]{"x:Test"}, VOID);
        // construct xTest(String s) // #0 = s
        //      {
        //      this.prop1 = s;      // LSET @"prop1" #0
        //      }                    // RETURN_1 -@"construct:finally"
        // finally
        //      {                    // #0 = this.private
        //      print this;          // PRINT #0
        //      }
        }

    private void add_test4(ConstantPoolAdapter adapter)
        {
        MethodTemplate mt = addMethodTemplate("test4", VOID, VOID);
        //  Void test4()                     // #0 = this:private
        //      {
        //      Test t = new Test("Hello");  // VAR x:Test (#1)
        //                                   // IVAR x:Function @"construct" (#2)
        //                                   // VAR  x:Function (#3)
        //                                   // FBIND #2, 1:(0, -@"hello"), #3
        //                                   // NEW x:Test, #3 (#4)
        //      print t                      // PRINT #3
        //      }                            // RETURN

        }
    @Override
    public void initializeHandle(ObjectHandle handle, ObjectHandle[] ahArg)
        {
        // don't call super;
        return;
        }

    ///////////////////////////////////////

    public void runTests(ServiceContext context)
        {
        ObjectHandle hTest = newInstance(Utils.TYPE_NONE, Utils.OBJECTS_NONE);

        forEachMethod (mt ->
            {
            if (mt.f_sName.startsWith("test"))
                {
                ObjectHandle[] ahVars = new ObjectHandle[1 + mt.m_cVars];
                ObjectHandle[] ahReturn = new ObjectHandle[1]; // for exceptions

                Frame frame = context.createFrame(hTest, null, mt, ahVars, ahReturn);
                frame.execute();
                }
            });
        }
    }
