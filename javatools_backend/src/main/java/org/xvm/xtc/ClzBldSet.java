package org.xvm.xtc;

import org.xvm.util.Ary;
import org.xvm.util.SB;
import org.xvm.util.S;

import java.util.ArrayList;

// Build a set of Java classes all at once, so 'javac' can compile them all at once.
public abstract class ClzBldSet {
  // Mods and classes to have source code generated for.
  public static final Ary<ClassPart> CLZS = new Ary<>(ClassPart.class);
  // All sources to be javac'd at once.  Includes some sources generated
  // outside ClzBuilder (e.g. generified functional interfaces or tuples)
  public static final ArrayList<JavaC.JavaSrc> SRCS = new ArrayList<>();

  // We know we are starting a new gen set here
  public static void do_compile( ClassPart clz ) {
    assert clz._header==null && clz._body==null;
    assert SRCS.isEmpty();
    add(clz);
    // ClzBuilder may add new modules+classes to compile.
    // While we keep finding things to compile...
    for( int i=0; i<CLZS._len; i++ ) {
      // Add to the JavaC source pile.  No source (yet), just the Java class name
      clz = CLZS.at(i);
      XClz xclz = XClz.make(clz);
      JavaC.JavaSrc jsrc = new JavaC.JavaSrc(xclz.qualified_name(),null);
      SRCS.add(jsrc);
      // Compute Module from Class
      ClassPart mod = clz;
      while( !(mod instanceof ModPart) )
        mod = (ClassPart)mod._par;
      // Generate Java source.  This might trigger more things to compile.
      new ClzBuilder((ModPart)mod,clz,new SB(),new SB(),true).jclass();
      assert clz._header!=null;
      //System.out.print(clz._header);
      //System.out.print(clz._body);
      jsrc._src = clz._header.toString() + clz._body.toString();
    }
    // Compile and load the Java classes as a complete set
    JavaC.compile(SRCS);
    CLZS.clear();
    SRCS.clear();
  }

  // Add a module+class to the current compilation step.
  // Filters for dups locally, but not globally.
  static void add( ClassPart clz ) {
    if( clz._tclz != null && find(clz._tclz.qualified_name()) )
      return;
    if( CLZS.find(clz)!= -1 ) return; // Already on the to-do list
    assert clz._header==null && clz._body==null;
    CLZS.push(clz);
  }

  // Add Java file (as a String), to the current compilation step.
  // Filters for dups globally.
  public static void add( String name, String body ) {
    if( find(name) ) return;    // Already done or in-progress
    //System.out.print(body);
    SRCS.add(new JavaC.JavaSrc(name,body));
  }

  // Find a fully qualified class name, in either XFM or the SRCS.  In XFM
  // generally has a ".class" file, and generally this gets loaded pretty
  // quickly.  The SRCS sets awaits a complete closed set of sources before
  // JavaC compiling (which is before installing in the XFM).
  public static boolean find(String name) {
    if( name==null ) return false;
    for( JavaC.JavaSrc js : SRCS )
      if( S.eq(js._name,name) )
        return true;
    return JavaC.XFM.get( name ) != null;
  }
}
