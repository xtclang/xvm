import ecstasy.mgmt.Container;

class ContainerControl
        extends RTServiceControl
        implements Container.Control
    {
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
    }