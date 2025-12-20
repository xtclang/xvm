package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * A typedef statement specifies a type to alias as a simple name.
 */
public class TypedefStatement
        extends ComponentStatement {
    // ----- constructors --------------------------------------------------------------------------

    public TypedefStatement(Expression cond, Token keyword, TypeExpression type, Token alias) {
        super(keyword.getStartPosition(), alias.getEndPosition());

        this.cond     = cond;
        this.modifier = keyword.getId() == Id.TYPEDEF ? Token.NONE : keyword;
        this.type     = type;
        this.alias    = alias;
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Access getDefaultAccess() {
        if (modifier != null) {
            switch (modifier.getId()) {
            case PUBLIC:
                return Access.PUBLIC;
            case PROTECTED:
                return Access.PROTECTED;
            case PRIVATE:
                return Access.PRIVATE;
            }
        }

        return super.getDefaultAccess();
    }



    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs) {
        // create the structure for this method
        if (getComponent() == null) {
            // create a structure for this typedef
            Component container = getParent().getComponent();
            String    sName     = (String) alias.getValue();
            if (container != null && container.isClassContainer()) {
                Access           access    = getDefaultAccess();
                TypeConstant     constType = type.ensureTypeConstant();
                TypedefStructure typedef   = container.createTypedef(access, constType, sName);
                setComponent(typedef);
            } else if (!errs.hasSeriousErrors()) {
                log(errs, Severity.ERROR, Compiler.TYPEDEF_UNEXPECTED, sName, container);
            }
        }
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        T result;
        if (cond != null && (result = visitor.apply(cond)) != null) {
            return result;
        }
        if (type != null && (result = visitor.apply(type)) != null) {
            return result;
        }
        return null;
    }

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        int i = 0;
        Expression newCond = cond == null ? null : (Expression) children.get(i++);
        TypeExpression newType = type == null ? null : (TypeExpression) children.get(i++);
        return new TypedefStatement(newCond, modifier, newType, alias);
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        return this;
    }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        return true;
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        var core = (!hasModifier() ? "typedef " : modifier + " ") + type + ' ' + alias.getValue() + ';';
        return cond == null ? core : "if (" + cond + ") { " + core + " }";
    }

    private boolean hasModifier() {
        return modifier != Token.NONE;
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected final Expression     cond;
    protected final Token          modifier;
    protected final Token          alias;
    protected final TypeExpression type;
}
