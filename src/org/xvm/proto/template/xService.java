package org.xvm.proto.template;

import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xService
        extends xObject
    {
    public xService(TypeSet types)
        {
        super(types, "x:Service", "x:Object", Shape.Interface);
        }

    // subclassing
    protected xService(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    @atomic String serviceName;
        //    enum StatusIndicator {Idle, Busy, ShuttingDown, Terminated};
        //    @ro @atomic StatusIndicator statusIndicator;
        //    @ro @atomic CriticalSection? criticalSection;
        //    enum Reentrancy {Prioritized, Open, Exclusive, Forbidden};
        //    @atomic Reentrancy reentrancy;
        //    @ro @atomic Timeout? incomingTimeout;
        //    @ro @atomic Timeout? timeout;
        //    @ro @atomic Duration upTime;
        //    @ro @atomic Duration cpuTime;
        //    @ro @atomic Boolean contended;
        //    @ro @atomic Int backlogDepth;
        //    Void yield();
        //    Void invokeLater(function Void doLater());
        //    @ro @atomic Int bytesReserved;
        //    @ro @atomic Int bytesAllocated;
        //    Void gc();
        //    Void shutdown();
        //    Void kill();
        //    Void registerTimeout(Timeout? timeout);
        //    Void registerCriticalSection(CriticalSection? criticalSection);
        //    Void registerShuttingDownNotification(function Void notify());
        //    Void registerUnhandledExceptionNotification(function Void notify(Exception));

        PropertyTemplate pt;

        pt = ensurePropertyTemplate("serviceName", "x:String");
        pt.makeReadOnly();
        pt.makeAtomic();
        pt.addGet().markNative();
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ServiceHandle(clazz);
        }

    @Override
    public ObjectHandle createStruct(Frame frame)
        {

        ServiceHandle hService = new ServiceHandle(f_clazzCanonical);
        hService.createFields();
        return hService;
        }

    @Override
    public ObjectHandle invokeNative01(Frame frame, ObjectHandle hTarget, MethodTemplate method, ObjectHandle[] ahReturn)
        {
        ServiceHandle  hThis = (ServiceHandle) hTarget;
        switch (method.f_sName)
            {
            case "serviceName$get":
                ahReturn[0] = xString.makeHandle(hThis.m_sName);
                return null;
            }
        throw new IllegalStateException("Unknown method: " + method);
        }

    public static class ServiceHandle
            extends GenericHandle
        {
        protected String m_sName;
        public ServiceHandle(TypeComposition clazz)
            {
            super(clazz);
            }
        }
    }
