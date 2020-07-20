import ecstasy.mgmt.Container;

service ContainerControl
        implements Container.Control
    {
    construct()
        {
        }

    // Container.Control
    @Override @RO Container.Status status                                   .get() {TODO("Native");}
    @Override Container.Control.Goal targetOptimization                     .get() {TODO("Native");}
    @Override Dec schedulingPriority                                        .get() {TODO("Native");}
    @Override void limitThreads(Int max)                                           {TODO("Native");}
    @Override void limitCompute(Duration max, function void() maxCpuExceeded)      {TODO("Native");}
    @Override void limitMemory(Int max, function void() maxRamExceeded)            {TODO("Native");}
    @Override Tuple invoke(String methodName, Tuple args, Service? runWithin)      {TODO("Native");}
    @Override @RO Service? mainService                                      .get() {TODO("Native");}
    @Override @RO Container[] nestedContainers                              .get() {TODO("Native");}
    @Override @RO Service[] nestedServices                                  .get() {TODO("Native");}
    @Override void pause()                                                         {TODO("Native");}
    @Override void resume()                                                        {TODO("Native");}
    @Override void store(FileStore filestore)                                      {TODO("Native");}
    @Override void load(FileStore filestore)                                       {TODO("Native");}

    // ServiceControl
    @Override void gc()                                                            {TODO("Native");}
    @Override void shutdown()                                                      {TODO("Native");}
    @Override void kill()                                                          {TODO("Native");}

    // ServiceStats
    @Override @RO Service.ServiceStatus statusIndicator                     .get() {TODO("Native");}
    @Override @RO Duration upTime                                           .get() {TODO("Native");}
    @Override @RO Duration cpuTime                                          .get() {TODO("Native");}
    @Override @RO Boolean contended                                         .get() {TODO("Native");}
    @Override @RO Int backlogDepth                                          .get() {TODO("Native");}
    @Override @RO Int bytesReserved                                         .get() {TODO("Native");}
    @Override @RO Int bytesAllocated                                        .get() {TODO("Native");}
    @Override Service.ServiceStats snapshotStats()                                 {TODO("Native");}
    }