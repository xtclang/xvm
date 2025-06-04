package org.xvm.javajit;


import org.xvm.api.Connector;
import org.xvm.asm.ModuleRepository;


public class JitConnector
        extends Connector {
    public JitConnector(ModuleRepository repo) {
        // TODO
        // TODO move this logic to a JitConnector?
        Xvm        xvm = new Xvm(repo);
        TypeSystem ts  = xvm.createLinker().addModule(module).link();
        // TODO add error reporting
        Container  cnt = xvm.createContainer(ts, xvm.DefaultMainInjector);
        // TODO reflect on cnt to get the main module and find the appropriate run() (or other) method
    }
}
