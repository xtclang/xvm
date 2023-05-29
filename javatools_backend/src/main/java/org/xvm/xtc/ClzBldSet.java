package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.SB;

import java.util.ArrayList;

// Build a set of Java classes all at once, so 'javac' can compile them all at once.
public abstract class ClzBldSet {
  // Mods and classes to have source code generated for
  public static final Ary<  ModPart> MODS = new Ary<>(  ModPart.class);
  public static final Ary<ClassPart> CLZS = new Ary<>(ClassPart.class);
  // Mods and classes with source to be javac'd all at a go
  public static final Ary<ClassPart> JAVAC = new Ary<>(ClassPart.class);

  // We know we are starting a new gen set here
  public static void do_compile( ModPart mod, ClassPart clz ) {
    assert clz._jclz==null && clz._header==null && clz._body==null;
    add(mod,clz);
    // While we keep finding things to compile...
    while( !MODS.isEmpty() ) {
      mod = MODS.pop();
      clz = CLZS.pop();
      JAVAC.push(clz);
      // ClzBuilder may add new modules+classes to compile
      new ClzBuilder(mod,clz,new SB(),new SB(),true).jclass();
      System.out.print(clz._header);
      System.out.print(clz._body);
      assert CLZS.find(clz)==-1;
      assert clz._header!=null;
    }
    // Compile and load the Java classes as a complete set
    ArrayList<JavaC.JavaSrc> srcs = new ArrayList<>();
    for( ClassPart c : JAVAC )
      srcs.add(new JavaC.JavaSrc(c._tclz.qualified_name(),
                                 c._header.toString() + c._body.toString()));
    JavaC.compile(srcs,JAVAC);
    JAVAC.clear();
  }

  static void add( ModPart mod, ClassPart clz ) {
    // No need to generate, already done
    if( clz._jclz!=null ) return;
    if( CLZS .find(clz)!= -1 ) return; // Already on the to-do list
    if( JAVAC.find(clz)!= -1 ) return; // Already on the to-do list
    assert clz._header==null && clz._body==null;
    MODS.push(mod);
    CLZS.push(clz);    
  }
  
}
