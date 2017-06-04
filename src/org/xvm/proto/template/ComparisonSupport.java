package org.xvm.proto.template;

import org.xvm.proto.ObjectHandle;

/**
 * Support for comparison op-codes.
 *
 * @author gg 2017.06.03
 */
public interface ComparisonSupport
    {
    int compare(ObjectHandle hValue1, ObjectHandle hValue2);
    }
