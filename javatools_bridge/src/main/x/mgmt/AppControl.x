service AppControl
        implements ecstasy.mgmt.Container.ApplicationControl
    {
    construct()
        {
        }

    @Override
    conditional Service mainService() {TODO("Native");}

    @Override
    Tuple invoke(String methodName, Tuple args) {TODO("Native");}
    }