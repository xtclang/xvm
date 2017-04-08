package org.xvm.proto.template;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTest2 extends xService
    {
    private final ConstantPoolAdapter adapter;

    public xTest2(TypeSet types)
        {
        super(types, "x:Test2", "x:Test", Shape.Class);

        adapter = types.f_constantPool;
        }

    @Override
    public void initDeclared()
        {
        ensurePropertyTemplate("prop2", "x:Int").setSetAccess(Access.Protected);

        addFunctionTemplate("construct", new String[]{"x:Test2", "x:String"}, VOID);

        ensureMethodTemplate("method1", VOID, INT);
        addFunctionTemplate("test21", VOID, VOID);
        addFunctionTemplate("test22", VOID, VOID);

        add_construct();
        add_method1();
        add_test21();
        add_test22();
        }

    private void add_construct()
        {
        FunctionTemplate ct = getFunctionTemplate("construct", new String[]{"x:Test2", "x:String"}, VOID);
        // construct xTest2(String s)            // #0 = this:struct, #1 = s
        //      {
        //      this.prop2 = s.length;          // VAR x:Int64 (#2)
        //                                      // GET #1 @"length" #2
        //                                      // SET #0, @"prop2" #2
        //      construct xTest(s);             // CALL_N0 -@"x:Test#construct" 2 #0 #1
        //      }                               // RETURN_0
        ct.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# in constructor: Test2 #")),
            new Var(adapter.getClassConstId("x:Int64")), // #2
            new Get(1, adapter.getPropertyConstId("x:String", "length"), 2),
            new Set(0, adapter.getPropertyConstId("x:Test2", "prop2"), 2),
            new Call_N0(-adapter.getMethodConstId("x:Test", "construct"), new int[] {0, 1}),
            new Return_0(),
            };
        ct.m_cVars = 3;
        }

    private void add_method1()
        {
        MethodTemplate mt = getMethodTemplate("method1", VOID, INT);
        //  Int method1()                       // #0 = this:private
        //      {
        //      int i = super();                // VAR x:Int (#1)
        //                                      // CALL_11 this:super #0 #1
        //      return -i;                      // NEG #1 #1
        //                                      // RETURN_01 #1
        //      }
        mt.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# in method1@Test2 #")),
            new Var(adapter.getClassConstId("x:Int64")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Neg(1, 1),
            new Return_1(1),
            };
        mt.m_cVars = 2;
        }

    private void add_test21()
        {
        FunctionTemplate ft = getFunctionTemplate("test21", VOID, VOID);
        //  static Void test3()
        //      {
        //      Test t = new Test2("Goodbye");  // VAR x:Test (#0)
        //                                      // NEW_1 @"x:Test2#construct" -@"Goodbye" #0
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
            new New_1(this.adapter.getMethodConstId("x:Test2", "construct"), -adapter.ensureConstantValueId("Goodbye"), 0),
            new Var(this.adapter.getClassConstId("x:String")),   // #1
            new Get(0, adapter.getPropertyConstId("x:Test", "prop1"), 1),
            new X_Print(1),
            new Var(this.adapter.getClassConstId("x:Int64")),    // #2
            new Invoke_01(0, -adapter.getMethodConstId("x:Test", "method1"), 2),
            new X_Print(2),
            new Return_0(),
            };
        ft.m_cVars = 3;
        }

    private void add_test22()
        {
        FunctionTemplate ft = getFunctionTemplate("test22", VOID, VOID);
        //  static Void test3()
        //      {
        //      Test t = new Test2("ABC");      // VAR x:Test (#0)
        //                                      // NEW_1 @"x:Test2#construct" -@"Goodbye" #0
        //      Function f = t.method1;         // VAR x:Function  (#1)
        //                                      // MBIND #0 -@"x:Test#method1" #1
        //      print f();                      // VAR x:Int #2
        //                                      // CALL_01 #1 #2
        //                                      // PRINT #2
        //      }                               // RETURN
        ft.m_aop = new Op[]
            {
            new Var(this.adapter.getClassConstId("x:Test")),     // #0
            new New_1(this.adapter.getMethodConstId("x:Test2", "construct"), -adapter.ensureConstantValueId("ABC"), 0),
            new Var(this.adapter.getClassConstId("x:Function")),   // #1
            new MBind(0, -adapter.getMethodConstId("x:Test", "method1"), 1),
            new Var(this.adapter.getClassConstId("x:Int64")),    // #2
            new Call_01(1, 2),
            new X_Print(2),
            new Return_0(),
            };
        ft.m_cVars = 3;
        }

    }
