package org.xvm.proto.template;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;
import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTest2 extends xObject
    {
    public ConstantPoolAdapter m_adapter;

    public xTest2(TypeSet types)
        {
        super(types, "x:Test2", "x:Test", Shape.Class);
        }

    @Override
    public void initDeclared()
        {
        addPropertyTemplate("prop2", "x:Int").setSetAccess(Access.Protected);

        addFunctionTemplate("construct", new String[]{"x:Test2", "x:String"}, new String[]{"x:Function"});

        addMethodTemplate("method1", VOID, INT);
        }

    @Override
    public void initCode()
        {
        ConstantPoolAdapter adapter = m_adapter;

        System.out.println("### Initialized ###");
        System.out.println(getDescription());

        add_construct(adapter);
        add_method1(adapter);
        }

    private void add_construct(ConstantPoolAdapter adapter)
        {
        FunctionTemplate ct = getFunctionTemplate("construct", new String[]{"x:Test2", "x:String"}, new String[]{"x:Function"});
        // construct xTest2(String s)            // #0 = this:struct, #1 = s
        //      {
        //      this.prop2 = s.length;          // VAR x:Int64 (#2)
        //                                      // GET #1 @"length" #2
        //                                      // SET #0, @"prop2" #2
        //      construct xTest(s);             // CALL_10 -@"x:Test#construct" 1
        //      }                               // RETURN_0
        ct.m_aop = new Op[]
            {
            new X_Print(-adapter.ensureConstantValue("### in constructor: Test2 ###")),
            new Var(adapter.getClassConstId("x:Int64")), // #2
            new Get(1, -adapter.getPropertyConstId("x:String", "length"), 2),
            new Set(0, -adapter.getPropertyConstId("x:Test2", "prop2"), 2),
            new Call_10(-adapter.getMethodConstId("x:Test", "construct"), 1),
            new Return_0(),
            };
        ct.m_cVars = 3;
        }

    private void add_method1(ConstantPoolAdapter adapter)
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
            new X_Print(-adapter.ensureConstantValue("### in method1@Test2 ###")),
            new Var(adapter.getClassConstId("x:Int64")), // #1
            new Call_01(Op.A_SUPER, 1),
            new Neg(1, 1),
            new Return_1(1),
            };
        mt.m_cVars = 2;
        }
    }
