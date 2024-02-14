package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.SB;

import java.util.ArrayList;
import java.util.HashMap;

// Build a set of Java classes all at once, so 'javac' can compile them all at once.
public abstract class ClzBldSet {
  // Mods and classes to have source code generated for.
  public static final Ary<  ModPart> MODS = new Ary<>(  ModPart.class);
  public static final Ary<ClassPart> CLZS = new Ary<>(ClassPart.class);
  // Mods and classes *with source* to be javac'd all at a go
  public static final Ary<ClassPart> JAVAC = new Ary<>(ClassPart.class);
  // All sources to be javac'd at once.  Includes some sources generated
  // outside ClzBuilder (e.g. generified functional interfaces or tuples)
  public static final ArrayList<JavaC.JavaSrc> SRCS = new ArrayList<>();

  // Other generated sources, that eventually get javac'd.
  public static HashMap<String,String> XCLASSES = new HashMap<>();

  
  // We know we are starting a new gen set here
  public static void do_compile( ModPart mod, ClassPart clz ) {
    assert clz._jclz==null && clz._header==null && clz._body==null;
    assert SRCS.isEmpty();
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
    for( ClassPart c : JAVAC )
      SRCS.add(new JavaC.JavaSrc(c._tclz.qualified_name(),
                                 c._header.toString() + c._body.toString()));
    JavaC.compile(SRCS,JAVAC);
    JAVAC.clear();
    SRCS.clear();
  }

  // Add a module+class to the current compilation step.
  // Filters for dups locally, but not globally.
  static void add( ModPart mod, ClassPart clz ) {
    // No need to generate, already done
    if( clz._jclz!=null ) return;
    if( CLZS .find(clz)!= -1 ) return; // Already on the to-do list
    if( JAVAC.find(clz)!= -1 ) return; // Already on the to-do list
    assert clz._header==null && clz._body==null;
    MODS.push(mod);
    CLZS.push(clz);    
  }

  // Add Java file (as a String), to the current compilation step.
  // Filters for dups globally.
  public static void do_compile( String name, String body ) {
    if( XCLASSES.containsKey(name) ) return;
    XCLASSES.put(name,body);
    System.out.print(body);
    JavaC.compile(new ArrayList<>(){{add(new JavaC.JavaSrc(name,body));}},null);
  }
}
