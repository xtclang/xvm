import ecstasy.Service.ServiceControl;
import ecstasy.Service.ServiceStatus;

/**
 * The native ServiceControl implementation.
 */
class RTServiceControl
        implements ServiceControl
    {
    // ServiceStats

    @Override @RO ServiceStatus statusIndicator.get() {TODO("native");}
    @Override @RO Duration      upTime         .get() {TODO("native");}
    @Override @RO Duration      cpuTime        .get() {TODO("native");}
    @Override @RO Boolean       contended      .get() {TODO("native");}
    @Override @RO Int           backlogDepth   .get() {TODO("native");}
    @Override @RO Int           bytesReserved  .get() {TODO("native");}
    @Override @RO Int           bytesAllocated .get() {TODO("native");}

    @Override Service.ServiceStats snapshotStats() {TODO("Native");}


    // ServiceControl

    @Override void gc()       {TODO("native");}
    @Override void shutdown() {TODO("native");}
    @Override void kill()     {TODO("native");}
    }