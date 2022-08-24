package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.compiler.Token;


/**
 * Represents a statement that corresponds to a Component in an Ecstasy FileStructure.
 */
public abstract class ComponentStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    protected ComponentStatement(long lStartPos, long lEndPos)
        {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isComponentNode()
        {
        return true;
        }

    @Override
    public Component getComponent()
        {
        return component;
        }

    protected void setComponent(Component component)
        {
        this.component = component;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return false;
        }

    @Override
    protected ConstantPool pool()
        {
        return component == null
                ? super.pool()
                : component.getConstantPool();
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }


    // ----- compile phases ------------------------------------------------------------------------


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * A helper method that find a matching annotation expression. This method is used for error
     * reporting only.
     *
     * @param anno      the annotation
     * @param listAnno  the annotation expression list
     *
     * @return an annotation expression matching the annotation
     */
    protected AnnotationExpression findAnnotationExpression(Annotation anno,
                                                            List<AnnotationExpression> listAnno)
        {
        if (listAnno.size() > 1)
            {
            for (AnnotationExpression exprAnno : listAnno)
                {
                if (exprAnno.ensureAnnotation(pool()).equals(anno))
                    {
                    return exprAnno;
                    }
                }
            }
        return listAnno.get(0);
        }


    public static boolean isStatic(List<Token> modifiers)
        {
        if (modifiers != null && !modifiers.isEmpty())
            {
            for (Token modifier : modifiers)
                {
                if (modifier.getId() == Token.Id.STATIC)
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    public static Access getAccess(List<Token> modifiers)
        {
        if (modifiers != null && !modifiers.isEmpty())
            {
            for (Token modifier : modifiers)
                {
                switch (modifier.getId())
                    {
                    case PUBLIC:
                        return Access.PUBLIC;
                    case PROTECTED:
                        return Access.PROTECTED;
                    case PRIVATE:
                        return Access.PRIVATE;
                    }
                }
            }
        return null;
        }


    // ----- fields --------------------------------------------------------------------------------

    private Component  component;
    private final long lStartPos;
    private final long lEndPos;
    }