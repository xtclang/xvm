package org.xvm.api;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeInfo;


/**
 * The API between Java host environment and an XVM runtime.
 * <p>
 * For a given Connector there is one and only one Runtime and one and only one top level
 * Container. All underlying Containers will use the same Runtime.
 * <p>
 * Normally, the usage of the Connector follows these steps:
 * <ul>
 *   <li> instantiate a Connector
 *   <li> add necessary module repositories
 *   <li> load a [main] module getting the Runtime back
 *   <li> configure the Runtime and the Container
 *   <li> start the Runtime and the Container for the main module
 *   <li> wait for the natural termination or explicitly shutdown the container at some point
 * </ul>
 */
public abstract class Connector {
    /**
     * Construct the Connector based on the specified ModuleRepository.
     */
    public Connector(ModuleRepository repository) {
        f_repository = repository;
    }

    /**
     * Create the main container for the specified module.
     */
    public abstract void loadModule(String sAppName);

    /**
     * Obtain the ConstantPool for the container associated with this Connector.
     */
    public abstract ConstantPool getConstantPool();

    /**
     * Start the Runtime and the main Container.
     *
     * @param mapInjections a map of custom injections where each key maps to a list of values;
     *                      may be null or empty if there are no custom injections
     */
    public abstract void start(Map<String, List<String>> mapInjections);

    /**
     * Find any possible entry points for a given name in the main module.
     */
    public abstract Set<MethodStructure> findMethods(String sMethodName);

    /**
     * Find an entry points for a given name in the specified module.
     */
    protected Set<MethodStructure> findMethods(ModuleConstant idModule, String sMethodName) {
        TypeInfo             typeInfo    = idModule.getType().ensureTypeInfo();
        Set<MethodConstant>  setMethodId = typeInfo.findMethods(sMethodName, -1, TypeInfo.MethodKind.Any);
        Set<MethodStructure> setMethods  = new HashSet<>();
        for (MethodConstant idMethod : setMethodId) {
            MethodInfo infoMethod = typeInfo.getMethodById(idMethod);
            setMethods.add(infoMethod.getHead().getMethodStructure());
        }
        return setMethods;
    }

    /**
     * Invoke an XTC method with a void return and specified arguments.
     *
     * @param method  the method structure
     * @param args    arguments as a list
     */
    public void invoke0(MethodStructure method, List<String> args) {
        invoke0(method, args.toArray(new String[0]));
    }

    /**
     * Invoke an XTC method with a void return and specified arguments.
     *
     * @param method  the method structure
     * @param asArg   arguments (must not be null)
     */
    public abstract void invoke0(MethodStructure method, String... asArg);

    /**
     * Wait for the container termination.
     *
     * @return zero if the main method was void or the return type not an int-convertible; otherwise
     *              the return value
     */
    public abstract int join() throws InterruptedException;

    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The module repository.
     */
    protected final ModuleRepository f_repository;
}
