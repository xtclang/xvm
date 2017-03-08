package org.xvm.proto;

import org.xvm.proto.op.Return;
import org.xvm.proto.op.Return1;
import org.xvm.proto.template.xObject;

import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    TypeSet m_types;
    ConstantPoolAdapter m_constantPoolAdapter;
    ObjectHeap m_heap;
    ServiceContext m_service;
    Set<ServiceContext> m_setServices;

    void init()
        {
        m_constantPoolAdapter = new ConstantPoolAdapter();
        m_types = new TypeSet(m_constantPoolAdapter);
        m_heap  = new ObjectHeap(m_constantPoolAdapter, m_types);

        initTypes();
        initConstants();
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

    void initConstants()
        {
        m_constantPoolAdapter.registerClasses(m_types);
        }

    public static void main(String[] asArg)
        {
        Container container = new Container();
        container.init();

        /*
        ServiceContext context = container.createServiceContext();

        Frame frame = context.createFrame("onStarted");

        Frame(ServiceContext context, TypeCompositionTemplate.InvocationTemplate function, ObjectHandle[] ahArgs, int cVars, int cReturns)

        */
        }

    Op[] test1(ConstantPoolAdapter adapter)
        {
        //        Int foo()
        //            {
        //            return 99;              // RETURN_1 99         ; 99 goes into constant pool
        //            }

        return new Op[]
            {
            new Return1(-adapter.ensureConstantValue(99)),
            };
        }
    }
