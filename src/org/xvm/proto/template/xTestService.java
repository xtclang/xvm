package org.xvm.proto.template;

import org.xvm.proto.*;
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
            new Var(adapter.getClassConstId("x:Int64")), // (#1)
            new Get(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new IVar(adapter.getClassConstId("x:Int64"), adapter.ensureConstantValueId(1)), // (#2)
            new Add(1, 2, 1),
            new Set(0, adapter.getPropertyConstId("x:TestService", "counter"), 1),
            new Return_1(1),
            };
        mtIncrement.m_cVars = 3;

        FunctionTemplate ftTest = addFunctionTemplate("test", VOID, VOID);
        // static Void test()
        //      {
        //      TestService svc = new TestService(48);  // VAR x:TestService (#0)
        //                                              // NEW_1 @"x:TestService#construct", -@48, 0
        //      int i = svc.increment();                // VAR x:Int64 (#1)
        //                                              // INVOKE_01 @"x:TestService#increment", 1
        //      print i;                                // PRINT 1
        //      return;                                 // RETURN_0
        //      }
        ftTest.m_aop = new Op[]
            {
            new Var(adapter.getClassConstId("x:TestService")),  // #0
            new New_1(adapter.getMethodConstId("x:TestService", "construct"), -adapter.ensureConstantValueId(48), 0),
            new X_Print(0),
            new Var(adapter.getClassConstId("x:Int64")),        // #1
            new Invoke_01(0, -adapter.getMethodConstId("x:TestService", "increment"), 1),
            new X_Print(1),
            new Return_0(),
            };
        ftTest.m_cVars = 2;
        }
    }
