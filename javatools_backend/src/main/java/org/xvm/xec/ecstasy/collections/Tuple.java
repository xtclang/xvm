package org.xvm.xec.ecstasy.collections;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xtc.*;
import org.xvm.xec.ecstasy.Range;
import org.xvm.xec.ecstasy.collections.Array.Mutability;

import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;

public interface Tuple extends Cloneable {
  // Array-like interface
  abstract public Object at(int i); // At an index
  abstract public void set(int i, Object o); // Set an index

  public abstract int size$get();

  // Using slice call instead of array syntax
  default public Tuple slice(Range r) { return at(r); }

  public abstract Tuple at(Range r);
  
  public abstract Mutability mutability$get();

  public abstract Tuple add( Object x );

  public abstract Tuple addAll( Tuple tup );

  public abstract Tuple ensureMutability(Mutability mut0, boolean inPlace);

  // Return a tuple class for this set of types.  The class is cached, and can
  // be used many times.
  public static XClz make_class( XClz xtt ) {
    // Lookup cached version
    int N = xtt.nTypeParms();
    if( N==0 ) return xtt;     // Tuple0 already exists in the base runtime
    
    String tclz = xtt.clz(new SB()).toString();
    String pack = (XEC.XCLZ+"."+xtt._pack).intern();
    String qual = (pack+"."+tclz).intern();
    ClzBuilder.add_import(qual);
    if( ClzBldSet.find(qual) ) return xtt;
    /* Gotta build one.  Looks like:
       class Tuple3$long$String$char extends Tuple3 {
         public long _f0;
         public String _f1;
         public char _f2;
         Tuple(long f0, String f1, char f2) {
           _f0=f0; _f1=f1; _f2=f2;
         }
         public Object f0() { return _f0; }
         public Object f1() { return _f1; }
         public Object f2() { return _f2; }
         public void f0(Object e) { _f0= ((Int64)e)._x; }
         public void f1(Object e) { _f1= ((org.xvm.xec.ecstasy.text.String)e)._x; }
         public void f2(Object e) { _f2= ((Float64)e)._x; }
         public long   at80() { return _f0; }
         public String at81() { return _f1; }
         public char   at82() { return _f2; }
       }
    */
    // Tuple N class
    SB sb = new SB();
    sb.p("// ---------------------------------------------------------------").nl();
    sb.p("// Auto Generated by Tuple from ").p(tclz).nl().nl();
    sb.fmt("package %0;\n\n",pack);
    sb.fmt("import %0.Tuple%1;\n",pack,N);
    HashSet<String> imports = new HashSet<>();
    for( int i=0; i<N; i++ ) {
      XClz box = xtt.typeParm(i).box();
      if( box.needs_import() ) {
        String tqual = box.qualified_name();
        if( !imports.contains(tqual) ) {
          imports.add(tqual);
          sb.fmt("import %0;\n",tqual);
        }
      }
    }
    sb.nl();
    
    sb.fmt("public class %0 extends Tuple%1 {\n",tclz,N).ii();
    // N field declares
    for( int i=0; i<N; i++ )
      sb.ifmt("public %0 _f%1;\n",xtt.typeParm(i).toString(),i);
    // Constructor, taking N arguments
    sb.ifmt("public %0(",tclz);
    for( int i=0; i<N; i++ )
      sb.fmt("%0 f%1, ",xtt.typeParm(i).toString(),i);
    sb.unchar(2).p(") {\n").ii().i();
    // N arg to  field assigns
    for( int i=0; i<N; i++ )
      sb.fmt("_f%0=f%0; ",i);
    sb.nl().di().ip("}\n");
    // Abstract accessors
    for( int i=0; i<N; i++ )
      sb.ifmt("public Object f%0() { return _f%0; }\n",i);
    // Abstract setters
    for( int i=0; i<N; i++ ) {
      XType xt = xtt.typeParm(i);
      XType box = xt.box();
      sb.ifmt("public void f%0(Object e) { _f%0= ((%1)e)%2; }\n",i,box.clz(),xt==box?"":"._i");
    }
    // Class end
    sb.di().ip("}\n");
    sb.p("// ---------------------------------------------------------------").nl();
    ClzBldSet.add(qual,sb.toString());

    return xtt;
  }
}
