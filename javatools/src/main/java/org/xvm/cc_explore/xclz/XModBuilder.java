package org.xvm.cc_explore.xclz;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.xrun.*;

import java.lang.Character;
import java.util.HashMap;
import javax.lang.model.SourceVersion;
import java.lang.reflect.Constructor;


// XTC Module is roughly a java package, but I don't want to deal with package
// directories So a module becomes "JModXNAME extends XClz" class.  Then a XTC
// class file (can be inside a single-class module file) extends this:
// "JClzTest extends JModTest".

public class XModBuilder {  
  public final ModPart _mod;
  public final SB _sb;
  public XModBuilder( ModPart mod ) {
    _mod = mod;
    _sb = new SB();
  }

  static String java_name( String xname ) {
    return "JMod"+xname;
  }

  private void jmod( ) {
    _sb.p("// Auto Generated by XEC from ").p(_mod._dir._str).p(_mod._path._str).nl().nl();
    _sb.p("package ").p(XEC.XCLZ).p(";").nl().nl();

    String jmod_name = java_name(_mod._name);
    _sb.p("public static class ").p(jmod_name).p(" extends XRunClz {").nl().ii();

    // Required constructor to inject the container
    _sb.ip("public ").p(jmod_name).p("( Container container ) { super(container); }").nl();


    // Look for a module init.  This will become the Java <clinit>
    MMethodPart mm = (MMethodPart)_mod.child("construct");
    MethodPart construct = (MethodPart)mm.child(mm._name);
    if( construct != null ) {
      assert construct._sibling==null;
      // Skip common empty constructor
      if( !construct.is_empty_function() ) {
        _sb.nl();
        _sb.ip("static {").nl();
        new XClzBuilder(_sb).ast(construct).jcode(_sb);
        _sb.ip("}").nl().nl();
      }
    }
    // End module
    _sb.di().p("}").nl();    
  }  
}
