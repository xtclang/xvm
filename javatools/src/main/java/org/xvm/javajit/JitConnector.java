package org.xvm.javajit;


import org.xvm.api.Connector;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;


public class JitConnector
        extends Connector {
    public JitConnector(ModuleRepository repo) {
        super(repo);

        xvm = new Xvm(repo);
    }

    @Override
    public void loadModule(String appName) {
        ModuleStructure module = f_repository.loadModule(appName);
        if (module == null) {
            throw new IllegalStateException("Unable to load module \"" + appName + "\"");
        }
        TypeSystem ts = xvm.createLinker().addModule(module).link();
        // TODO add error reporting

        Container container = xvm.createContainer(ts, xvm.DefaultMainInjector);
        // TODO reflect on container to get the main module and find the appropriate run() (or other) method

    }

    /**
     * The XVM within which this TypeSystem exists
     */
    public final Xvm xvm;
}
