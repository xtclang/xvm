package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PresentCondition;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.UnresolvedClassConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;


/**
 * Represents a statement that corresponds to a Component in an Ecstasy FileStructure.
 *
 * @author cp 2017.04.12
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
    public ComponentStatement getComponentStatement()
        {
        return this;
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

// TODO - can this be disposed of now?
//    @Override
//    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
//        {
//        boolean   fResolved = true;
//        Component component = getComponent();
//        if (component instanceof CompositeComponent)
//            {
//            for (Component componentEach : ((CompositeComponent) component).components())
//                {
//                fResolved &= resolveConstants(componentEach, errs);
//                }
//            }
//        else
//            {
//            fResolved = resolveConstants(component, errs);
//            }
//
//        if (fResolved)
//            {
//            super.resolveNames(listRevisit, errs);
//            }
//        else
//            {
//            listRevisit.add(this);
//            }
//        }


    // ----- name resolution -----------------------------------------------------------------------

    @Override
    protected IdentityConstant resolveParentBySingleName(String sName)
        {
        IdentityConstant constant = super.resolveParentBySingleName(sName);
        if (constant == null && component != null)
            {
            if (component.getName().equals(sName) || (component instanceof ModuleStructure &&
                    ((ModuleStructure) component).getModuleConstant().getUnqualifiedName().equals(sName)))
                {
                constant = component.getIdentityConstant();
                }
            }
        return constant;
        }

    @Override
    protected IdentityConstant resolveSingleName(String sName)
        {
        // TODO
        return null;
        }

    // TODO - can this be disposed of now?
    private boolean resolveConstants(Component component, ErrorListener errs)
        {
        if (component == null)
            {
            return true;
            }

        boolean fResolved = true;

        // component condition
        ConditionalConstant cond = component.getCondition();
        if (cond != null)
            {
            for (ConditionalConstant condTerm : cond.terminals())
                {
                if (condTerm instanceof PresentCondition)
                    {
                    fResolved &= resolveConstant(((PresentCondition) condTerm).getPresentConstant(), errs);
                    }
                }
            }

        // contributions
        for (Component.Contribution contribution : component.getContributionsAsList())
            {
            fResolved &= resolveConstant(contribution.getRawConstant(), errs);
            fResolved &= resolveConstant(contribution.getDelegatePropertyConstant(), errs);
            }

        return fResolved;
        }

    // TODO - can this be disposed of now?
    private boolean resolveConstant(Constant constantUnknown, ErrorListener errs)
        {
        if (constantUnknown == null)
            {
            return true;
            }

        // make sure sub-constants are resolved
        boolean[] result = new boolean[1];
        result[0] = true;
        constantUnknown.forEachUnderlying(constant -> result[0] &= resolveConstant(constant, errs));
        if (!result[0])
            {
            return false;
            }

        // make sure this constant is resolved
        if (constantUnknown instanceof ResolvableConstant)
            {
            if (((ResolvableConstant) constantUnknown).getResolvedConstant() != null)
                {
                return true;
                }

            if (constantUnknown instanceof UnresolvedClassConstant)
                {
                UnresolvedClassConstant constant = (UnresolvedClassConstant) constantUnknown;
                // TODO
                return false;
                }
            else if (constantUnknown instanceof UnresolvedNameConstant)
                {
                UnresolvedNameConstant constant = (UnresolvedNameConstant) constantUnknown;
// TODO
                IdentityConstant       constId  = null; // TODO (IdentityConstant) resolveFirstName(constant.getName(0));
                for (int i = 1, c = constant.getNameCount(); i < c; ++i)
                    {
                    // TODO resolve constant.getName(i) against constId to obtain the corresponding constId
                    throw new UnsupportedOperationException();
                    }
                constant.resolve(constId);
                return true;
                }
            else if (constantUnknown instanceof UnresolvedTypeConstant)
                {
                UnresolvedTypeConstant constant = (UnresolvedTypeConstant) constantUnknown;
                // TODO
                return false;
                }
            }

        return true;
        }


    // ----- helpers -------------------------------------------------------------------------------

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

    private Component component;
    private long      lStartPos;
    private long      lEndPos;
    }
