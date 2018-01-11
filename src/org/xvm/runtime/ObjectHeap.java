package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Heap and constants.
 */
public class ObjectHeap
    {
    public final TemplateRegistry f_templates;
    public final ConstantPool f_pool;

    Map<Integer, ObjectHandle> m_mapConstants = new HashMap<>();

    public ObjectHeap(ConstantPool pool, TemplateRegistry templates)
        {
        f_templates = templates;
        f_pool = pool;
        }

    // nValueConstId -- "literal" (Int/String/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(Frame frame, int nValueConstId)
        {
        return m_mapConstants.computeIfAbsent(nValueConstId, nConstId ->
            {
            Constant constValue = f_pool.getConstant(nConstId);
            ClassTemplate template = f_templates.getConstTemplate(constValue); // must exist

            return template.createConstHandle(frame, constValue);
            });

        }
    }
