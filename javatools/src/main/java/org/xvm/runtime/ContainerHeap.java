package org.xvm.runtime;


import org.xvm.asm.Constant;


/**
 * The Constant heap associated with a non-core Container.
 */
public class ContainerHeap
        extends ConstHeap
    {
    /**
     * Instantiate a heap for a dependent container.
     *
     * @param heapParent  the parent container's Constant heap
     */
    public ContainerHeap(ConstHeap heapParent)
        {
        f_heapParent = heapParent;
        }

    @Override
    protected ObjectHandle getConstHandle(Constant constValue)
        {
        ObjectHandle hValue = super.getConstHandle(constValue);
        return hValue == null
                ? f_heapParent.getConstHandle(constValue)
                : hValue;
        }

    @Override
    protected ClassTemplate getTemplate(Constant constValue)
        {
        return f_heapParent.getTemplate(constValue);
        }

    /**
     * The parent heap.
     */
    private final ConstHeap f_heapParent;
    }
