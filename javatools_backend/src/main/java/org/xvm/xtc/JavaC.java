package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.xec.XClz;

import java.io.ByteArrayOutputStream;
import java.lang.ClassNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import javax.tools.*;

import static javax.tools.JavaFileObject.Kind;

public abstract class JavaC {

  // Compile a whole set of classes together
  static void compile( ArrayList<JavaSrc> srcs, Ary<ClassPart> clzs ) {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> errs = new DiagnosticCollector<>();
    XFileManager xfile = new XFileManager(compiler.getStandardFileManager(null, null, null));

    ArrayList<String> options = new ArrayList<>(){};
    
    JavaCompiler.CompilationTask task = compiler.getTask(null, xfile, errs, options, null, srcs);

    if( !task.call() ) {
      errs.getDiagnostics().forEach( System.err::println );
      throw XEC.TODO();
    }

    try {
      for( int i=0; i<clzs._len; i++ )
        clzs.at(i)._jclz = (Class<XClz>)xfile._loader.loadClass(srcs.get(i)._name);
    } catch( ClassNotFoundException cnfe ) {
      throw new RuntimeException(cnfe);
    }
  }

  static class JavaSrc extends SimpleJavaFileObject {
    public final String _name, _src;
    public JavaSrc(String name, String src) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),  Kind.SOURCE);
      _name = name;
      _src = src;
    }
    @Override public CharSequence getCharContent(boolean ignore) { return _src;  }
  }

  private static class JCodes extends SimpleJavaFileObject {
    public final ByteArrayOutputStream _bos = new ByteArrayOutputStream();
    public JCodes(String name, Kind kind) {
      super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
    }
    @Override public ByteArrayOutputStream openOutputStream() { return _bos; }
  }
  
  private static class XClzLoader extends ClassLoader {
    public final HashMap<String,JCodes> _map;
    XClzLoader( ClassLoader par, XFileManager xfm ) { super(par); _map = xfm._map; }
    @Override protected Class<XClz> findClass(String clzname) throws ClassNotFoundException {
      JCodes codes = _map.get(clzname);
      if( codes==null )  throw new ClassNotFoundException();
      byte[] bytes = codes._bos.toByteArray();
      return (Class<XClz>)defineClass(clzname, bytes, 0, bytes.length);
    }
  }
  
  private static class XFileManager extends ForwardingJavaFileManager<JavaFileManager> {    
    public final HashMap<String,JCodes> _map = new HashMap<>();
    public final XClzLoader _loader;
    public XFileManager(StandardJavaFileManager man) {
      super(man);
      _loader = new XClzLoader(getClass().getClassLoader(),this);
    }
    @Override public XClzLoader getClassLoader(Location location) { return _loader; }
    @Override  public JavaFileObject getJavaFileForOutput(Location ignore, String name, Kind kind, FileObject sibling) {
      JCodes jb = new JCodes(name, kind);
      _map.put(name,jb);
      return jb;
    }
  }
  
}
