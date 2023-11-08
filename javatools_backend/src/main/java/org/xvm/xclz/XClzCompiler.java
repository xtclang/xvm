package org.xvm.xclz;

import org.xvm.XEC;

import java.io.ByteArrayOutputStream;
import java.lang.ClassNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import javax.tools.*;

import static javax.tools.JavaFileObject.Kind;

public abstract class XClzCompiler {

  // Compile a whole class
  static Class<XRunClz> compile( String clzname, String source ) {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> errs = new DiagnosticCollector<>();
    XFileManager xfile = new XFileManager(compiler.getStandardFileManager(null, null, null));
    ArrayList<JavaSrc> srcs = new ArrayList<>();
    srcs.add(new JavaSrc(clzname, source));

    ArrayList<String> options = new ArrayList<String>(){{
        add("-source");
        add("17");
        add("--enable-preview");
      }};
    
    JavaCompiler.CompilationTask task = compiler.getTask(null, xfile, errs, options, null, srcs);

    boolean result = task.call();

    if( !result ) {
      errs.getDiagnostics().forEach( System.err::println );
      throw XEC.TODO();
    }

    try {
      return (Class<XRunClz>)xfile._loader.loadClass(clzname);
    } catch( ClassNotFoundException cnfe ) {
      throw new RuntimeException(cnfe);
    }
  }

  private static class JavaSrc extends SimpleJavaFileObject {
    public final String _src;
    public JavaSrc(String name, String src) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),  Kind.SOURCE);
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
    @Override protected Class<XRunClz> findClass(String clzname) throws ClassNotFoundException {
      JCodes codes = _map.get(clzname);
      if( codes==null )  throw new ClassNotFoundException();
      byte[] bytes = codes._bos.toByteArray();
      return (Class<XRunClz>)defineClass(clzname, bytes, 0, bytes.length);
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
