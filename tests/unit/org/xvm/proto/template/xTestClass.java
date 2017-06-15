package org.xvm.proto.template;

import org.xvm.proto.*;

import org.xvm.proto.op.*;

/**
 * A test class.
 *
 * @author gg 2017.03.15
 */
public class xTestClass extends TypeCompositionTemplate
    {
    private final ConstantPoolAdapter adapter;

    public xTestClass(TypeSet types)
        {
        super(types, "x:TestClass", "x:Object", Shape.Class);

        adapter = types.f_adapter;
        }

    @Override
    public void initDeclared()
        {
        m_fAutoRegister = true;

        ensurePropertyTemplate("prop1", "x:String");

        // --- constructor()
        ConstructTemplate construct = ensureConstructTemplate(
                new String[]{"x:TestClass", "x:String"});
        FunctionTemplate ftFinally = ensureFunctionTemplate(
                "finally", new String[]{"x:TestClass", "x:String"}, VOID);

        construct.m_aop = new Op[]
            { // #0 = this:struct; #1 = s
            new X_Print(-adapter.ensureValueConstantId("\n# in constructor: TestClass #")),
            new PSet(0, adapter.getPropertyConstId("x:TestClass", "prop1"), 1),
            new Return_0(),
            };
        construct.m_cVars = 2;
        construct.setFinally(ftFinally);

        ftFinally.m_aop = new Op[]
            { // #0 = this:private; #1 = s
            new X_Print(-adapter.ensureValueConstantId("# in finally: TestClass #")),
            new X_Print(1),
            new Return_0(),
            };
        ftFinally.m_cVars = 2;

        // --- method1()
        MethodTemplate mtMethod1 = ensureMethodTemplate("method1", VOID, INT);
        mtMethod1.m_aop = new Op[]
            { // #0 (this)
            new X_Print(-adapter.ensureValueConstantId("\n# in TestClass.method1 #")),
            new NVar(adapter.getClassTypeConstId("x:String"),
                     adapter.ensureValueConstantId("s")), // #1 (s)
            new LGet(adapter.getPropertyConstId("x:TestClass", "prop1"), 1),
            new NVar(adapter.getClassTypeConstId("x:Int64"),
                     adapter.ensureValueConstantId("of")), // #2 (of)
            new IVar(adapter.getClassTypeConstId("x:String"),
                     -adapter.ensureValueConstantId("world")), // #3
            new Invoke_11(1, adapter.getMethodConstId("x:String", "indexOf"), 3, 2),
            new Var(adapter.getClassTypeConstId("x:Int64")), // #4
            new PGet(1, adapter.getPropertyConstId("x:String", "length"), 4),
            new Add(4, 2, 4),
            new Return_1(4),
            };
        mtMethod1.m_cVars = 5;

        MethodTemplate mtExceptional = ensureMethodTemplate("exceptional", STRING, VOID);
        mtExceptional.m_aop = new Op[]
            { // #0 (this), #1 (s)
            new Var(adapter.getClassTypeConstId("x:Exception")), // #2
            new New_N(adapter.getMethodConstId("x:Exception", "construct"),
                        new int[]{1, -adapter.getClassTypeConstId("x:Nullable$Null")}, 2),
            new Throw(2),
            };
        mtExceptional.m_cVars = 3;
        }
    }
