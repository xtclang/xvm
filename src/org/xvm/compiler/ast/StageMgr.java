package org.xvm.compiler.ast;


import java.util.List;
import org.xvm.asm.ErrorListener;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Expression;


/**
 * A Stage Manager is used to shepher the AST nodes through their various stages.
 */
public class StageMgr
    {
    public StageMgr()
        {
        }

    public AstNode process(AstNode node, Stage stageTarget, ErrorListener errs)
        {
        m_errs = errs; 
        Stage stageCur = node.getStage();
        assert stageCur != Stage.Discarded;

        if (stageCur.)
        // TODO
        return null;
        }

    public void requestRevisit()
        {
        // TODO
        }
    public void processChildren()
        {

        }
    public void markComplete()
        {
        // TODO
        // note - automatically assumed to be finished when it returns, if it didn't call revisit(),
        // but a node can signify that it has finished before it returns
        }

    public Expression validateExpression(Expression expr)
        {
        // TODO
        return null;
        }

    // expression validation stuff:
    // TODO pack()
    // TODO unpack()
    // TODO convert()

    private AstNode m_cur;
    private List<AstNode> m_listRevisit;
    private ErrorListener m_errs;
    }
