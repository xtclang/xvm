package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Token;


/**
 * A type expression specifies a module name.
 */
public class ModuleTypeExpression
    extends NamedTypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ModuleTypeExpression.
     *
     * @param names  the qualified name of the module
     */
    public ModuleTypeExpression(List<Token> names)
        {
        super(null, names, null, null, null, names.get(names.size()-1).getEndPosition());
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        }
    }