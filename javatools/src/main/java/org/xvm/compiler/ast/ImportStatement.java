package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.NameResolver.Result;

import org.xvm.util.Severity;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 */
public class ImportStatement
        extends Statement
        implements NameResolver.NameResolving {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an import statement.
     *
     * @param cond
     * @param keyword        the "import" keyword
     * @param alias          the simple name that represents the qualified name
     * @param qualifiedName  the qualified name as a list of identifier tokens
     */
    public ImportStatement(Expression cond, Token keyword, Token alias, List<Token> qualifiedName) {
        assert alias != null;

        this.cond          = cond;
        this.keyword       = keyword;
        this.alias         = alias;
        this.qualifiedName = qualifiedName;
    }

    /**
     * Construct an import statement for an "import all" style statement.
     *
     * @param cond
     * @param keyword        the "import" keyword
     * @param qualifiedName  the qualified name as a list of identifier tokens
     * @param star           the "*" token
     */
    public ImportStatement(Expression cond, Token keyword, List<Token> qualifiedName, Token star) {
        assert star != null;

        this.cond          = cond;
        this.keyword       = keyword;
        this.qualifiedName = qualifiedName;
        this.star          = star;
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "cond" - deep copied by AstNode.clone()</li>
     *   <li>Computed state: {@code m_resolver}, {@code m_fImportRegistered} - shallow copied via Object.clone() bitwise copy</li>
     * </ul>
     *
     * @param original  the ImportStatement to copy from
     */
    protected ImportStatement(@NotNull ImportStatement original) {
        super(Objects.requireNonNull(original));

        // Copy non-child structural fields (Tokens are immutable, safe to share)
        this.keyword       = original.keyword;
        this.alias         = original.alias;
        this.qualifiedName = original.qualifiedName;  // List<Token> is immutable
        this.star          = original.star;

        // Deep copy child fields
        this.cond = original.cond == null ? null : original.cond.copy();
        adopt(this.cond);

        // Shallow copy computed state (matching Object.clone() semantics)
        this.m_resolver          = original.m_resolver;
        this.m_fImportRegistered = original.m_fImportRegistered;
    }

    @Override
    public ImportStatement copy() {
        return new ImportStatement(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the import alias
     */
    public String getAliasName() {
        return alias == null ? null : alias.getValueText();
    }

    /**
     * @return true iff the import is of the ".*" form
     */
    public boolean isWildcard() {
        return star != null;
    }

    /**
     * @return the number of simple names in the imported name
     */
    public int getQualifiedNameLength() {
        return qualifiedName.size();
    }

    /**
     * @param i  indicates which simple name of the imported name to obtain
     *
     * @return the i-th simple names in the imported name
     */
    public String getQualifiedNamePart(int i) {
        return (String) qualifiedName.get(i).getValue();
    }

    /**
     * @return the imported name as an array of simple names
     */
    public String[] getQualifiedName() {
        int      cNames = getQualifiedNameLength();
        String[] asName = new String[cNames];
        for (int i = 0; i < cNames; ++i) {
            asName[i] = getQualifiedNamePart(i);
        }
        return asName;
    }

    /**
     * @return the imported name as a dot-delimited name
     */
    public String getQualifiedNameString() {
        return qualifiedName.stream()
                .map(Token::getValueText)
                .collect(Collectors.joining("."));
    }

    @Override
    public long getStartPosition() {
        return keyword.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return (alias == null ? star : alias).getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- NameResolving interface ---------------------------------------------------------------

    @Override
    public NameResolver getNameResolver() {
        NameResolver resolver = m_resolver;
        if (resolver == null || resolver.getNode() != this) {
            Iterator<String> iter = star == null
                    ? qualifiedName.stream()
                                   .map(t -> (String) t.getValue())
                                   .iterator()
                    : qualifiedName.stream()
                                   .map(t -> (String) t.getValue())
                                   .filter(Objects::nonNull)
                                   .iterator();
            m_resolver = resolver = new NameResolver(this, iter);
        }
        return resolver;
    }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs) {
        if (cond != null) {
            log(errs, Severity.WARNING, Compiler.CONDITIONAL_IMPORT);
        }
    }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        setStage(Stage.Resolving);

        if (!m_fImportRegistered) {
            // as global visibility is resolved, each import statement registers itself so that anything
            // following it can see the import, but anything preceding it does not
            getParentBlock().registerImport(this, errs);
            m_fImportRegistered = true;
        }

        NameResolver resolver = getNameResolver();
        switch (resolver.resolve(errs)) {
        case DEFERRED:
            mgr.requestRevisit();
            return;

        case RESOLVED:
            // check that the resolved constant is something that an import is allowed to resolve to
            if (!(resolver.getConstant() instanceof IdentityConstant)) {
                log(errs, Severity.ERROR, Compiler.IMPORT_NOT_IDENTITY, getQualifiedNameString());
            }
            break;
        }
    }


    // ----- compilation (Statement) --------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        // make sure that the name is not taken (or if it is, that it is hideable)
        String sName = getAliasName();
        if (sName != null) {
            if (ctx.getVar(sName) != null && !ctx.isVarHideable(sName)) {
                log(errs, Severity.ERROR, Compiler.IMPORT_NAME_COLLISION, sName);
            } else {
                // resolve what the qualified name is in reference to
                NameResolver resolver = getNameResolver();
                if (resolver.getResult() != Result.RESOLVED &&
                        resolver.resolve(errs) != Result.RESOLVED) {
                    return null;
                }
            }
        }

        return this;
    }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        return fReachable;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        var sb = new StringBuilder();

        if (cond != null) {
            sb.append("if (")
              .append(cond)
              .append(") { ");
        }

        sb.append("import ");

        String last = qualifiedName.isEmpty() ? null :
            String.valueOf(qualifiedName.get(qualifiedName.size() - 1).getValue());
        sb.append(qualifiedName.stream()
            .map(token -> String.valueOf(token.getValue()))
            .collect(Collectors.joining(".")));

        if (alias != null && !last.equals(alias.getValue())) {
            sb.append(" as ")
              .append(alias.getValue());
        } else if (star != null) {
            sb.append(".*");
        }

        sb.append(';');

        if (cond != null) {
            sb.append(" }");
        }

        return sb.toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Conditional expression")
    protected Expression  cond;
    protected Token       keyword;
    protected Token       alias;
    protected List<Token> qualifiedName;
    protected Token       star;

    @ComputedState("Name resolver for import resolution")
    private NameResolver m_resolver;
    @ComputedState("Whether import has been registered")
    private boolean      m_fImportRegistered;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ImportStatement.class, "cond");
}