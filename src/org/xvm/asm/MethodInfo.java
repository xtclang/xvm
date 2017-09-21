package org.xvm.asm;


import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Op;


/**
 * TODO
 *
 * @author cp 2017.09.21
 */
public class MethodInfo
    {
    private final MethodStructure f_struct;
    private       boolean         m_fNative;
    private       Op[]            m_aop;
    private       int             m_cVars;
    private int m_cScopes = 1;
    private MethodInfo m_mtFinally;

    public MethodInfo(MethodStructure struct)
        {
        f_struct = struct;
        }

    public MethodStructure getMethodStructure()
        {
        return f_struct;
        }

    public boolean isNative()
        {
        return m_fNative;
        }

    public void setNative(boolean fNative)
        {
        m_fNative = fNative;
        }

    public Op[] getOps()
        {
        return m_aop;
        }

    public void setOps(Op[] aop)
        {
        m_aop = aop;
        }

    public int getMaxVars()
        {
        return m_cVars;
        }

    public void setMaxVars(int cVars)
        {
        m_cVars = cVars;
        }

    public int getMaxScopes()
        {
        return m_cScopes;
        }

    public void setMaxScopes(int cScopes)
        {
        m_cScopes = cScopes;
        }

    public MethodInfo getConstructFinally()
        {
        return m_mtFinally;
        }

    public void setConstructFinally(MethodInfo mtFinally)
        {
        m_mtFinally = mtFinally;
        }
    }
