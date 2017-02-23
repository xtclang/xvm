package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.02.17
 */
public class Function
    {
    int m_cArgs; // number of arguments
    int m_cReturns; // number of return values
    int m_cVars; // number of local vars
    byte[] m_abOps; // op-codes


    Frame createFrame(ServiceContext context, ObjectHandle[] ahArgs)
        {
        assert ahArgs.length == m_cArgs;

        return new Frame(context, this, ahArgs, m_cVars, m_cReturns);
        }
    }
