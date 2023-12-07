package org.xvm.xtc.ast;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.xtc.cons.Const;
import org.xvm.xtc.cons.MethodCon;
import org.xvm.util.SB;

public class CallAST extends AST {
  final XType[] _rets;
  static CallAST make( ClzBuilder X ) {
    // Read optional array of return types (not currently used)
    Const[] retTypes = X.consts();
    // Read the arguments, then the function expression.
    AST[] kids = X.kids_bias(1);
    // Move the function to the 0th kid slot.
    kids[0] = ast_term(X);     // Call expression first
    XType[] rets = XType.xtypes(retTypes);
    return new CallAST(rets,X._meth._name,kids);
  }
  CallAST( XType[] rets, String mname, AST... kids ) {
    super(kids);
    // Check for a call to super: "super.call()" becomes "super.METHOD"
    if( _kids[0] instanceof RegAST reg && reg._reg== -13 )
      _kids[0] = new ConAST(null,null,"super."+mname,reg._type);
    _rets = rets;
    _type = _type();
  }
  
  @Override XType _type() {
    if( _rets==null ) return XType.VOID;
    if( _rets.length == 1 ) return _rets[0];
    throw XEC.TODO();
  }

  @Override AST rewrite( ) {
    // Try to rewrite constant calls to the XTC special equals.
    if( _kids[0] instanceof ConAST con ) {
      if( con._type instanceof XType.Fun fun && fun._args!=null && fun._args[0] instanceof XType.ClzClz clz ) {
        // Hard force Int64/IntNumber "funky dispatch" to Java primitive
        if( clz._clz==XType.JLONG ) {
          if( con._con.equals("Int64.equals") ) return new BinOpAST("==","",XType.BOOL,_kids[1],_kids[2]);
          if( con._con.equals("IntNumber.compare") ) return new BinOpAST("<=>","",XType.BOOL,_kids[1],_kids[2]);
          if( con._con.equals("Int64.hashCode") ) return _kids[2];
          throw XEC.TODO();
        }
        // Hard force  "funky dispatch" to Java primitive
        if( clz._clz==XType.STRING ) {
          if( con._con.equals("String.equals") ) return new BinOpAST("==","",XType.BOOL,_kids[1],_kids[2]);
        }
      }

      // Convert "funky dispatch" calls:
      int lidx = con._con.lastIndexOf('.');
      if( lidx != -1 ) {
        String clz = con._con.substring(0,lidx);
        String base = con._con.substring(lidx+1);
        int nargs = switch( base ) {
          case "hashCode" -> 1;
          case "equals", "compare" -> 2;
          default -> 0;
        };
        if( nargs != 0 ) {
          if( _kids.length==nargs+2 ) { // extra-KID static variant
            // Convert CLZ.equal/cmp(GOLD,arg1,arg2) to their static variant: CLZ.equal/cmp$CLZ(GOLD,arg1,arg2);
            // Convert CLZ.hashCode (GOLD,arg1     ) to their static variant: CLZ.hashCode$CLZ (GOLD,arg1,arg2);
            con._con += "$"+clz;
            return this;
          } else {              // Dynamic variant
            // Convert CLZ.equal/cmp(arg1,arg2) to Java dynamic: GOLD.equal/cmp(arg1,arg2);
            // Convert CLZ.hashCode (arg1     ) to Java dynamic: GOLD.hashCode (arg1     );
            assert _kids.length==nargs+1;
            MethodPart meth = (MethodPart)((MethodCon)con._tcon).part();
            ClassPart clazz = meth.clz();
            ClzBuilder.add_import(clazz);
            return new InvokeAST(base,_rets,new ConAST(clz+".GOLD"),_kids[1],_kids[2]);
          }
        }
      }
    }
    return this;
  }
  
  @Override void jmid ( SB sb, int i ) {
    sb.p( i==0 ? (_kids[0] instanceof RegAST ? ".call(": "(") : ", " );
  }
  @Override void jpost( SB sb ) {
    if( _kids.length > 1 )
      sb.unchar(2);
    sb.p(")");
  }
}
