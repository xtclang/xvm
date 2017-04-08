package org.xvm.proto.template;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;

import org.xvm.proto.op.*;

/**
 * A test service.
 *
 * @author gg 2017.03.15
 */
public class xTestService extends xService
    {
    private final ConstantPoolAdapter adapter;

    public xTestService(TypeSet types)
        {
        super(types, "x:TestService", "x:Object", Shape.Service);

        adapter = types.f_constantPool;

        addImplement("x:Service");
        }

    @Override
    public void initDeclared()
        {
        FunctionTemplate ftConstructor = addFunctionTemplate("construct", new String[]{"x:TestService", "x:Int64"}, VOID);
        ensurePropertyTemplate("counter", "x:Int64");

        // service TestService(Int counter = 0) // #0 = this.struct, #1 = initial value
        //      {
        //      }
        ftConstructor.m_aop = new Op[]
            {
            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_0(),
            };
        ftConstructor.m_cVars = 2;

        MethodTemplate mtIncrement = ensureMethodTemplate("increment", VOID, INT);
        //  Int increment()                     // #0 = this:private
        //      {
        //      return counter++;               // VAR x:Int64 (#1)
        //                                      // GET #0 -@"counter" #1
        //                                      // IVAR x:Int64 -@1 (#2)
        //                                      // ADD #1 #2 #1
        //                                      // SET #0 -@"counter" #1
        //                                      // RETURN_01 #1
        //      }
        mtIncrement.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValueId("# in increment@TestService #")),
            new Var(adapter.getClassConstId("x:Int64")), // (#1)
            new Get(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new IVar(adapter.getClassConstId("x:Int64"), adapter.ensureConstantValueId(1)), // (#2)
            new Add(1, 2, 1),
            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_1(1),
            };
        mtIncrement.m_cVars = 3;

        MethodTemplate mtThrowing = ensureMethodTemplate("throwing", VOID, INT);
        //  Int throwing()  // #0 = this:private
        //      {
        //      throw new Exception("test");    // VAR x:Exception (#1)
        //                                      // NEW_N @"#x:Exception:construct" 2 -@"test" -@"x:Nullable.Null" #1
        //                                      // THROW #1
        //      }
        mtThrowing.m_aop = new Op[]
            {
            new Var(this.adapter.getClassConstId("x:Exception")), // #1
            new New_N(this.adapter.getMethodConstId("x:Exception", "construct"),
                        new int[]{-adapter.ensureConstantValueId("test"),
                                  -adapter.getClassConstId("x:Nullable$Null")}, 1),
            new Throw(1),
            };
        mtThrowing.m_cVars = 2;

        MethodTemplate mtTest = ensureMethodTemplate("test", VOID, VOID);
        // Void test() // #0 = this:private
        //      {
        //      int i = svc.increment();                // VAR x:Int64 (#1)
        //                                              // INVOKE_01 @"x:TestService#increment", 1
        //      print i;                                // PRINT 1
        //      i = svc.increment();                    // INVOKE_01 @"x:TestService#increment", 1
        //      print i;                                // PRINT 1
        //      return;                                 // RETURN_0
        //      }
        mtTest.m_aop = new Op[]
            {
            new X_Print(0),
new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), -adapter.ensureConstantValueId(42)),
            new Var(adapter.getClassConstId("x:Int64")),        // #1
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "increment"), 1),
            new X_Print(1),
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "throwing"), 1),
            new X_Print(1),
            new Return_0(),
            };
        mtTest.m_cVars = 2;
        }
    }
