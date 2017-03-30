package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

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
