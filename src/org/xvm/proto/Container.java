package org.xvm.proto;

import org.xvm.proto.template.*;

import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    TypeSet m_typeSet;
    ServiceContext m_service;
    Set<ServiceContext> m_setServices;

    void initTypes()
        {
        TypeSet types = m_typeSet = new TypeSet();

        // depth first traversal starting with Object

        types.addCompositionTemplate(new xObject(types));     // -> Meta, String, Array, Tuple, Function
        }

    public static void main(String[] asArg)
        {
        Container container = new Container();
        container.initTypes();
        container.m_typeSet.dumpTemplates();
        }
    }
