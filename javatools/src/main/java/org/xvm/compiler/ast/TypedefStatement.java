package org.xvm.compiler.ast;
 
 
 import java.lang.reflect.Field;
 import java.util.List;
 import java.util.stream.Collectors;
 
 import org.xvm.asm.Component;
 import org.xvm.asm.Constants.Access;
 import org.xvm.asm.ErrorListener;
 import org.xvm.asm.MethodStructure.Code;
 import org.xvm.compiler.ast.Parameter;
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
 
     public TypedefStatement(Expression cond, Token keyword, List<Parameter> templateParams, TypeExpression type, TypeExpression aliasType) {
         super(keyword.getStartPosition(), aliasType.getEndPosition());
 
         this.cond         = cond;
         this.modifier     = keyword.getId() == Id.TYPEDEF ? null : keyword;
         this.templateParams = templateParams;
         this.type         = type;
         this.aliasType    = aliasType;
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
 
     public List<Parameter> getTemplateParams() {
         return templateParams;
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
             String    sName     = aliasType.toString();
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
 
         sb.append("typedef ");
         if (templateParams != null && !templateParams.isEmpty()) {
             sb.append('<')
               .append(templateParams.stream().map(Object::toString).collect(Collectors.joining(", ")))
               .append('>');
         }
         sb.append(' ')
           .append(type)
           .append(" as ")
           .append(aliasType)
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
 
     protected Expression           cond;
     protected Token                modifier;
     protected List<Parameter>      templateParams;
     protected TypeExpression       type;
     protected TypeExpression       aliasType;
 
     private static final Field[] CHILD_FIELDS = fieldsForNames(TypedefStatement.class, "cond", "templateParams", "type", "aliasType");
 }