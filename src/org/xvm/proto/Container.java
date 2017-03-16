package org.xvm.proto;

import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xTest;

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
        m_constantPoolAdapter = new ConstantPoolAdapter();
        m_types = new TypeSet(m_constantPoolAdapter);
        m_heap  = new ObjectHeap(m_constantPoolAdapter, m_types);
        }

    void init()
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
        container.init();

        container.runTest();
        }

    public void runTest()
        {
        xTest clzTest = new xTest(m_types, m_constantPoolAdapter);

        m_types.addTemplate(clzTest);

        clzTest.runTests(new ServiceContext(this));  // todo: xService
        }
    }
