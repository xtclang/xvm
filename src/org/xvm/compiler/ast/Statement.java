package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.Line_N;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;


/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the label corresponding to the beginning of the Statement
     */
    public Label getBeginLabel()
        {
        Label label = m_labelBegin;
        if (label == null)
            {
            m_labelBegin = label = new Label();
            }
        return label;
        }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel()
        {
        Label label = m_labelEnd;
        if (label == null)
            {
            m_labelEnd = label = new Label();
            }
        return label;
        }

    /**
     * Mark the statement as completing by short-circuiting.
     */
    public void shortCircuit()
        {
        m_fShortCircuited = true;
        }

    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Generate the generic assembly code that wraps the contents of any statement.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean completes(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ctx.updateLineNumber(code, Source.calculateLine(getStartPosition()));

        if (m_labelBegin != null)
            {
            code.add(m_labelBegin);
            }

        boolean fCompletes = fReachable & emit(ctx, fReachable, code, errs);

        if (m_labelEnd != null)
            {
            code.add(m_labelEnd);
            }

        return fCompletes || m_fShortCircuited;
        }

    /**
     * Generate the statement-specific assembly code.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // TODO temporary -- this will be abstract
        throw notImplemented();
        }


    // ----- inner class: compiler Context ---------------------------------------------------------

    /**
     * Compiler context for compiling a method body.
     */
    public static class Context
        {
        public Context(MethodStructure method)
            {
            m_method = method;
            }

        /**
         * @return the MethodStructure that the context represents
         */
        public MethodStructure getMethod()
            {
            return m_method;
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool getPool()
            {
            return m_method.getConstantPool();
            }

        /**
         * Used in the validation phase to track scopes.
         */
        public void enterScope()
            {
            // TODO
            }

        public void registerVar(String sName, Register reg, ErrorListener errs)
            {
            // TODO
            }

        public Argument resolveName(String sName)
            {
            // TODO
            return null;
            }

        /**
         * Used in the validation phase to track scopes.
         */
        public void exitScope()
            {
            // TODO
            }

        public int getLineNumber()
            {
            return m_nLine;
            }

        public void updateLineNumber(Code code, int nLine)
            {
            if (nLine != m_nLine)
                {
                code.add(new Line_N(nLine - m_nLine));
                m_nLine = nLine;
                }
            }

        private MethodStructure m_method;
        private int m_nLine = 0;
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fShortCircuited;
    }
