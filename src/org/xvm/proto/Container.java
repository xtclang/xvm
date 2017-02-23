package org.xvm.proto;

import java.util.Set;

/**
 * TODO: for now Container == SecureContainer
 *
 * @author gg 2017.02.15
 */
public class Container
    {
    ObjectHeap m_heapGlobal; // only immutable objects
    TypeSet m_typeSet;
    ServiceContext m_service;
    Set<ServiceContext> m_setServices;
    }
