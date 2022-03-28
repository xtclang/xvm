package org.xvm.runtime;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.InjectionKey;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;


/**
 * A non-core container ( > 0).
 */
public class SimpleContainer
        extends Container
    {
    /**
     * Instantiate a simple container.
     *
     * @param context   the parent container's context
     * @param idModule  the module id
     */
    public SimpleContainer(Container containerParent, ServiceContext context, ModuleConstant idModule)
        {
        super(containerParent.f_runtime, containerParent, context.f_templates,
              new ContainerHeap(context.f_container.f_heap), idModule);
        }

    /**
     * @return a set of injections for this container's module
     */
    public Set<InjectionKey> collectInjections()
        {
        ModuleStructure module = (ModuleStructure) getModule().getComponent();

        Set<InjectionKey> setInjections = new HashSet<>();
        module.getFileStructure().visitChildren(
            component -> component.collectInjections(setInjections), false, true);
        return setInjections;
        }
    }