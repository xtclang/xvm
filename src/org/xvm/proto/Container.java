package org.xvm.proto;

import org.xvm.proto.template.xException;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xService;

import java.util.HashSet;
import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    final public TypeSet f_types;
    final public ConstantPoolAdapter f_constantPoolAdapter;
    final public ObjectHeap f_heap;
    ServiceContext m_service;
    Set<ServiceContext> m_setServices = new HashSet<>();

    public Container()
        {
        f_constantPoolAdapter = new ConstantPoolAdapter();
        f_types = new TypeSet(f_constantPoolAdapter);
        f_heap = new ObjectHeap(f_constantPoolAdapter, f_types);

        init();
        }

    protected void init()
        {
        TypeSet types = f_types;

        // depth first traversal starting with Object

        // typedef Int64   Int;
        // typedef UInt64  UInt;

        types.addAlias("x:Int", "x:Int64");
        types.addAlias("x:UInt", "x:UInt64");

        types.addTemplate(new xObject(types));
        types.addTemplate(new xException(types));
        types.addTemplate(new xService(types));

        // container.m_typeSet.dumpTemplates();
        }

    // TODO: for xService only
    public ServiceContext createContext(xObject xo)
        {
        ServiceContext ctx = new ServiceContext(this);
        m_setServices.add(ctx);
        return ctx;
        }
    }
