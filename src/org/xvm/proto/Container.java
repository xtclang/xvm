package org.xvm.proto;

import org.xvm.proto.op.*;
import org.xvm.proto.template.xObject;

import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    public TypeSet m_types;
    public ConstantPoolAdapter m_constantPoolAdapter;
    public ObjectHeap m_heap;
    ServiceContext m_service;
    Set<ServiceContext> m_setServices;

    public Container()
        {
        init();
        }

    void init()
        {
        m_constantPoolAdapter = new ConstantPoolAdapter();
        m_types = new TypeSet(m_constantPoolAdapter);
        m_heap  = new ObjectHeap(m_constantPoolAdapter, m_types);

        initTypes();
        }

    void initTypes()
        {
        TypeSet types = m_types;

        // depth first traversal starting with Object

        // typedef Int64   Int;
        // typedef UInt64  UInt;

        types.addAlias("x:Int",  "x:Int64");
        types.addAlias("x:UInt", "x:UInt64");

        types.addTemplate(new xObject(types));

        // container.m_typeSet.dumpTemplates();
        }

    public static void main(String[] asArg)
        {
        Container container = new Container();

        container.runTest();
        }

    public void runTest()
        {
        xTest clzTest = new xTest(m_types);

        m_types.addTemplate(clzTest);

        ObjectHandle hTest = clzTest.newInstance(Utils.TYPE_NONE, Utils.OBJECTS_NONE);

        ServiceContext context = new ServiceContext(this); // todo: xService

        // test.test1();
            {
            ObjectHandle[] ahVars = new ObjectHandle[clzTest.mtTest1.m_cVars];
            ObjectHandle[] ahReturn = new ObjectHandle[1]; // for exceptions

            Frame frame1 = context.createFrame(hTest, null, clzTest.mtTest1, ahVars, ahReturn);
            frame1.execute();
            }

        // test.test3();
            {
            ObjectHandle[] ahVars = new ObjectHandle[clzTest.mtTest3.m_cVars];
            ObjectHandle[] ahReturn = new ObjectHandle[1]; // for exceptions

            Frame frame3 = context.createFrame(hTest, null, clzTest.mtTest3, ahVars, ahReturn);
            frame3.execute();
            }
        }

    //////////////////////////////////////////////////////////////////

    public class xTest extends xObject
        {
        public MethodTemplate mtTest1;
        public MethodTemplate mtTest2;
        public MethodTemplate mtTest3;

        public xTest(TypeSet types)
            {
            super(types, "x:Test", "x:Object", Shape.Class);
            }

        @Override
        public void initDeclared()
            {
            ConstantPoolAdapter adapter = m_constantPoolAdapter;

            mtTest1 = addMethodTemplate("test1", VOID, VOID);
                {
                //  Void test1()
                //      {
                //      return;         // RETURN
                //      }
                mtTest1.m_anRetTypeId = Utils.TYPE_ID_NONE;
                mtTest1.m_aop = new Op[]
                        {
                        new Return(),
                        };
                mtTest1.m_cVars = 1;
                }

            mtTest2 = addMethodTemplate("test2", VOID, INT);
                {
                //  Int test2()
                //      {
                //      return 99;       // RETURN_1 -@99    ; 99 goes into constant pool
                //      }
                int nIntClass = adapter.getClassConstId("x:Int64");
                int n99 = adapter.ensureConstantValue(99);
                mtTest2.m_aop = new Op[]
                        {
                        new Return_1(-n99),
                        };
                mtTest2.m_cVars = 1;
                mtTest2.m_anRetTypeId = new int[] {nIntClass};
                }


            mtTest3 = addMethodTemplate("test3", VOID, VOID);
                {
                //  Void test3()         // reg #0 = this:private
                //      {
                //      Int i = test2(); // VAR x:Int64 (#1)
                //                       // INVOKE_01 -@"x:Method/test2" #0
                //      print i;         // PRINT #1
                //      return;          // RETURN
                //      }
                int nIntClass = adapter.getClassConstId("x:Int64");
                int nTest2Method = adapter.getMethodConstId("x:Test", "test2");
                mtTest3.m_aop = new Op[]
                        {
                        new Var(nIntClass), // #1
                        new Invoke_01(0, -nTest2Method, 1),
                        new X_Print(1),
                        new Return(),
                        };
                mtTest3.m_cVars = 3;
                }
            }

        @Override
        public void initializeHandle(ObjectHandle handle, ObjectHandle[] ahArg)
            {
            return;
            }
        }
    }
