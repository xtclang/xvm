package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A parameter type and name, with an optional default value.
 */
public class Parameter
        extends AstNode {
    // ----- constructors --------------------------------------------------------------------------

    public Parameter(TypeExpression type) {
        this (type, null);
    }

    public Parameter(TypeExpression type, Token name) {
        this (type, name, null);
    }

    public Parameter(TypeExpression type, Token name, Expression value) {
        this.type  = type;
        this.name  = name;
        this.value = value;
    }

    /**
     * Copy constructor.
     *
     * <p><b>Master clone() semantics:</b>
     * <ul>
     *   <li>Deep copy (from CHILD_FIELDS): type, value</li>
     *   <li>Shallow copy (same reference): name</li>
     * </ul>
     * <p>
     * Order matches master clone(): all non-child fields FIRST, then children.
     *
     * @param original  the Parameter to copy
     */
    protected Parameter(Parameter original) {
        super(original);

        // Step 1: Copy non-child fields FIRST
        this.name = original.name;

        // Step 2: Deep copy children explicitly
        this.type  = original.type == null ? null : original.type.copy();
        this.value = original.value == null ? null : original.value.copy();

        // Step 3: Adopt copied children
        if (this.type != null) {
            this.type.setParent(this);
        }
        if (this.value != null) {
            this.value.setParent(this);
        }
    }

    @Override
    public Parameter copy() {
        return new Parameter(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    public TypeExpression getType() {
        return type;
    }

    public String getName() {
        return name == null ? null : name.getValueText();
    }

    public Token getNameToken() {
        return name;
    }

    public Expression getValue() {
        return value;
    }

    @Override
    public long getStartPosition() {
        return type == null ? name.getStartPosition() :
               name == null ? type.getStartPosition() :
                              Math.min(type.getStartPosition(), name.getStartPosition());
    }

    @Override
    public long getEndPosition() {
        return value == null
                ? name == null
                        ? type.getEndPosition()
                        : Math.max(type.getEndPosition(), name.getEndPosition())
                : value.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);

        if (name != null) {
            sb.append(' ')
              .append(name.getValueText());
        }

        if (value != null) {
            sb.append(" = ")
              .append(value);
        }

        return sb.toString();
    }

    public String toTypeParamString() {
        assert name != null;
        String s = String.valueOf(name.getValue());
        return type == null ? s : (s + " extends " + type);
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Parameter type")
    protected TypeExpression type;
    protected Token          name;
    @ChildNode(index = 1, description = "Default value")
    protected Expression     value;

    private static final Field[] CHILD_FIELDS = fieldsForNames(Parameter.class, "type", "value");
}
