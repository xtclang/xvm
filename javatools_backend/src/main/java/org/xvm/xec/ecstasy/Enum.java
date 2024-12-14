package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.util.SB;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;
import org.xvm.xtc.ClassPart;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.Part;
import org.xvm.xtc.XCons;

/** The Java Enum class, implementing an XTC hidden internal class.
    The XTC Enum is an *XTC interface*, not an XTC class, but it has many class-like properties.

    The XTC Enum interface is thus treated like this Java Class.
 */
public class Enum extends Const {
  public static final Enum GOLD = new Enum();
  public Enum() {}              // Explicit no-arg-no-work constructor
  public Enum(Never n) {}       // Forced   no-arg-no-work constructor

  // --- Comparable
  public boolean equals( XTC x0, XTC x1 ) { throw org.xvm.XEC.TODO(); }

  public static <E extends java.lang.Enum> boolean equals$Enum( XTC gold, E       ord0, E       ord1 ) { return ord0==ord1; }
  public static <E extends java.lang.Enum> boolean equals$Enum( XTC gold, boolean ord0, boolean ord1 ) { return ord0==ord1; }

  // Build the VALUES array for ENUMS
  // private static final ENUM[] _VALUES = new ENUM[]{....};
  // public static final AryXTC<ENUM> VALUES = new AryXTC<ENUM>(ENUM.GOLD,_VALUES);
  // public final AryXTC<ENUM> value$get() { return VALUES; }
  public static ClassPart[] makeValues(ClassPart clz, SB sb) {
    assert clz._f == Part.Format.ENUM;
    String name = clz.xclz()._name;

    // The enum values are intermixed with implemented methods
    Ary<ClassPart> cs = new Ary<>(ClassPart.class);
    for( Part p : clz._name2kid.values() )
      if( p instanceof ClassPart e && e._f == Part.Format.ENUMVALUE )
        cs.setX(e._ord,e);
    ClassPart[] es = cs.asAry();

    // private static final ENUM[] _VALUES = new ENUM[]{....};
    sb.ifmt("private static final %0[] _VALUES = new %0[]{ ",name);
    for( ClassPart e : es )
      sb.p(e._name).p(".GOLD, ");
    sb.unchar(2).p("};").nl();

    // public static final AryXTC<ENUM> VALUES = new AryXTC<ENUM>(ENUM.GOLD,_VALUES);
    sb.ifmt("public static final AryXTC<%0> VALUES = new AryXTC<%0>(%0.GOLD,_VALUES);\n",name);

    // public final AryXTC<ENUM> value$get() { return VALUES; }
    sb.ifmt("public final AryXTC<%0> values$get() { return VALUES; }\n",name);

    // Now the enum instances
    //public static class ENUMVALUE extends ENUM {
    //  public static final ENUMVALUE GOLD = new ENUMVALUE();
    //}
    sb.nl();
    for( ClassPart e : es )
      sb.ifmt("public static class %1 extends %0 { public static final %1 GOLD = new %1(); }\n",name,e._name);
    sb.nl();

    return es;
  }
}
