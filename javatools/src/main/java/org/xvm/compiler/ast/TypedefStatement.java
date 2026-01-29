package org.xvm.compiler.ast;


import java.lang.reflect.Field;

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
        this.modifier = keyword.getId() == Id.TYPEDEF ? null : keyword;
        this.type     = type;
        this.alias    = alias;
    }

    /**
     * Copy constructor.
     *
     * <p><b>Master clone() semantics:</b>
     * <ul>
     *   <li>Deep copy (from CHILD_FIELDS): cond, type</li>
     *   <li>Shallow copy (same reference): modifier, alias</li>
     * </ul>
     *
     * @param original  the statement to copy
     */
    protected TypedefStatement(TypedefStatement original) {
        super(original);

        // Step 1: Copy ALL non-child fields FIRST (matches super.clone() behavior)
        this.modifier = original.modifier;
        this.alias    = original.alias;

        // Step 2: Deep copy children explicitly
        this.cond = original.cond == null ? null : original.cond.copy();
        this.type = original.type == null ? null : original.type.copy();

        // Step 3: Adopt copied children
        if (this.cond != null) {
            this.cond.setParent(this);
        }
        if (this.type != null) {
            this.type.setParent(this);
        }
    }

    @Override
    public TypedefStatement copy() {
        return new TypedefStatement(this);
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

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
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
        StringBuilder sb = new StringBuilder();

        if (cond != null) {
            sb.append("if (")
              .append(cond)
              .append(") { ");
        }

        if (modifier != null) {
            sb.append(modifier)
              .append(' ');
        }

        sb.append("typedef ")
          .append(type)
          .append(' ')
          .append(alias.getValue())
          .append(';');

        if (cond != null) {
            sb.append(" }");
        }

        return sb.toString();
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Conditional expression")
    protected Expression     cond;
    protected Token          modifier;
    protected Token          alias;
    @ChildNode(index = 1, description = "Aliased type")
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypedefStatement.class, "cond", "type");
}