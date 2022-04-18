package org.xvm.runtime;


import org.xvm.asm.Constant;


/**
 * The Constant heap for the core Ecstasy container.
 */
public class CoreConstHeap
        extends ConstHeap
    {
    /**
     * Construct the core Constant heap for shared Ecstasy module.
     *
     * @param templates  the template registry
     */
    public CoreConstHeap(TemplateRegistry templates)
        {
        f_templates = templates;
        }

    @Override
    protected ClassTemplate getTemplate(Constant constValue)
        {
        return f_templates.getTemplate(constValue);
        }

    /**
     * The registry.
     */
    private final TemplateRegistry f_templates;
    }