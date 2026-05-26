package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashSet;
import java.util.List;

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

    public TypedefStatement(Expression cond, Token keyword, List<Parameter> typeParams,
                            Token alias, TypeExpression type) {
        super(keyword.getStartPosition(), alias.getEndPosition());

        this.cond       = cond;
        this.modifier   = keyword.getId() == Id.TYPEDEF ? null : keyword;
        this.typeParams = typeParams;
        this.alias      = alias;
        this.type       = type;
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
        // create the structure for this typedef
        if (getComponent() == null) {
            Component container = getParent().getComponent();
            String    sName     = (String) alias.getValue();
            if (container != null && container.isClassContainer()) {
                Access           access    = getDefaultAccess();
                TypeConstant     constType = type.ensureTypeConstant();
                TypedefStructure typedef   = container.createTypedef(access, constType, sName);
                if (typedef != null && typeParams != null && !typeParams.isEmpty()) {
                    HashSet<String> setNames = new HashSet<>();
                    for (Parameter param : typeParams) {
                        String sParam = param.getName();
                        if (setNames.add(sParam)) {
                            TypeExpression exprType  = param.getType();
                            TypeConstant   constParm = exprType == null
                                    ? pool().typeObject()
                                    : exprType.ensureTypeConstant();
                            typedef.addTypeParam(sParam, constParm);
                        } else {
                            log(errs, Severity.ERROR, Compiler.DUPLICATE_TYPE_PARAM, sParam);
                        }
                    }
                }
                setComponent(typedef);
            } else if (!errs.hasSeriousErrors()) {
                log(errs, Severity.ERROR, Compiler.TYPEDEF_UNEXPECTED, sName, container);
            }
        }
    }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        if (!mgr.processChildren()) {
            mgr.requestRevisit();
            return;
        }

        TypedefStructure typedef = (TypedefStructure) getComponent();
        if (typedef == null) {
            return;
        }

        if (typeParams != null && !typeParams.isEmpty()) {
            for (Parameter param : typeParams) {
                TypeExpression exprConstraint = param.getType();
                if (exprConstraint != null) {
                    TypeConstant typeConstraint = exprConstraint.ensureTypeConstant();
                    if (typeConstraint.containsUnresolved()) {
                        mgr.requestRevisit();
                        return;
                    }
                    typedef.updateConstraint(param.getName(), typeConstraint);
                }
            }
        }

        type.resetTypeConstant();
        TypeConstant constType = type.ensureTypeConstant();
        if (constType.containsUnresolved()) {
            mgr.requestRevisit();
            return;
        }
        typedef.setType(constType);
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

        sb.append("typedef ");
        if (typeParams == null || typeParams.isEmpty()) {
            sb.append(type)
              .append(" as ")
              .append(alias.getValue());
        } else {
            sb.append(alias.getValue())
              .append('<');

            boolean first = true;
            for (Parameter param : typeParams) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(param.toTypeParamString());
            }

            sb.append("> as ")
              .append(type);
        }

        sb.append(';');

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

    protected Expression     cond;
    protected Token          modifier;
    protected List<Parameter> typeParams;
    protected Token          alias;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(TypedefStatement.class, "cond", "typeParams", "type");
}
