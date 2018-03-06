package org.xvm.asm.constants;


import java.util.List;
import org.xvm.asm.Annotation;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.constants.TypeConstant.Origin;

import org.xvm.util.ListMap;


/**
 * Represents the information about a potential call chain. This is used for classes and properties,
 * while collecting call chain information. The chain progresses through a series of state changes:
 * <ul>
 * <li>Initial - no information;</li>
 * <li>Annotations-Only - for properties, a list of annotations are being collected, but no custom
 *     code has been encountered;</li>
 * <li>Realized - actual chains have been materialized, and the annotation list is not used.</li>
 * </ul>
 */
public class ChainBuilder
    {
    public boolean isEmpty()
        {
        return m_listAnnos == null && m_listmapClassChain == null && m_listmapDefaultChain == null;
        }

    public boolean isAnnosOnly()
        {
        return m_listAnnos != null;
        }

    public boolean isChainsRealized()
        {
        return m_listmapClassChain != null || m_listmapDefaultChain != null;
        }

    /**
     *
     * @param anno  the property annotation to add to the list
     *
     * @return true if the annotation was already present
     */
    public boolean addPropAnno(Annotation anno)
        {
        if (isChainsRealized())
            {
            ListMap<IdentityConstant, Origin> list = m_listAnnos;
            if (list == null)
                {
                m_listAnnos = list = new ListMap<>();
                }


            }
        else
            {
            }
        }

    public boolean addPropCode(Contribution contrib)
    public boolean addPropDefault()

    ListMap<IdentityConstant, Origin> getClassChain()
        {

        }
    ListMap<IdentityConstant, Origin> m_listmapDefaultChain;

    ListMap<IdentityConstant, Origin> m_listAnnos;
    ListMap<IdentityConstant, Origin> m_listmapClassChain;
    ListMap<IdentityConstant, Origin> m_listmapDefaultChain;
    }
