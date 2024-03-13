package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.util.Ary;
import org.xvm.xec.XTC;

import java.io.*;
import java.lang.ClassNotFoundException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import javax.tools.*;

import static javax.tools.JavaFileObject.Kind;

public abstract class JavaC {
  // Standard Tool JavaC
  static final JavaCompiler COMPILER;
  // File Manager Wrapper to fake byte buffers as class files to JavaC
  static final XFileManager XFILE;
  // XTC loader, using XFILE to find files
  static final XClzLoader LOADER;
  
  // A map from qualified Java class names e.g. "org.xvm.xec.XEC" to a
  // JCodes, which is just a wrapper around the class file contents as bytes.
  public static final XFileSys XFM;

  static {
    COMPILER = ToolProvider.getSystemJavaCompiler();
    XFILE = new XFileManager(COMPILER.getStandardFileManager(null, null, null));
    LOADER = new XClzLoader();
    // Preload all the hand-made Java classes under XTC.XCLZ
    URL xecurl = JavaC.class.getClassLoader().getResource("org/xvm/xec/");
    XFM = new XFileSys("org.xvm.xec",new File(xecurl.getFile()));
  }

  
  // Compile a whole set of classes together
  static void compile( ArrayList<JavaSrc> srcs ) {

    DiagnosticCollector<JavaFileObject> errs = new DiagnosticCollector<>();

    ArrayList<String> options = new ArrayList<>();
    
    JavaCompiler.CompilationTask task = COMPILER.getTask(null, XFILE, errs, options, null, srcs);

    if( !task.call() ) {
      errs.getDiagnostics().forEach( System.err::println );
      throw XEC.TODO();
    }

    try {
      for( JavaSrc src : srcs ) {
        JCodes jc = XFM.get( src._name );
        jc._klass = (Class<XTC>) LOADER.loadClass( src._name );
      }
    } catch( ClassNotFoundException cnfe ) {
      throw new RuntimeException(cnfe);
    }
  }

  // Just wraps the constructor to pass in a URI
  public static class SJFOWrap extends SimpleJavaFileObject {
    public final String _name;
    public SJFOWrap(String name, Kind kind) {
      super(URI.create("string:///" + name.replace('.', '/') + kind.extension),  kind);
      _name = name;
    }
  }
  
  public static class JavaSrc extends SJFOWrap {
    public String _src;
    public JavaSrc(String name, String src) {
      super(name,  Kind.SOURCE);
      _src = src;
    }
    @Override public CharSequence getCharContent(boolean ignore) { return _src;  }
  }

  public static class JCodes extends SJFOWrap {
    // Free the "buf"!
    private static final class BAOS extends ByteArrayOutputStream { byte[] buf() { return buf; } }
    final byte[] _buf;
    final BAOS _bos;
    public Class<XTC> _klass;
    public JCodes(String name, Kind kind) {
      super(name, kind);
      _bos = new BAOS(); // Initial small buffer, will change during compilation
      _buf = null;       // Buf from BAOS, which changes during compilation
    }
    JCodes(String name, File file) {
      super(name,Kind.CLASS);
      _bos = null;
      try {
        _buf = Files.readAllBytes(file.toPath());
      } catch(IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
    @Override public ByteArrayOutputStream openOutputStream() { return _bos; }
    // Input stream from output stream without copying the buf
    @Override public ByteArrayInputStream openInputStream() {
      return _buf==null
        ? new ByteArrayInputStream(_bos.buf(),0,_bos.size())
        : new ByteArrayInputStream(    _buf  ,0,_buf.length);
    }
  }
  
  private static class XClzLoader extends ClassLoader {
    XClzLoader() { super(JavaC.class.getClassLoader()); }
    @Override protected Class<XTC> findClass( String clzname) throws ClassNotFoundException {
      JCodes jc = XFM.get(clzname);
      return (Class<XTC>)(jc._buf==null
                          ? defineClass(clzname, jc._bos.buf(), 0, jc._bos.size())
                          : defineClass(clzname, jc.    _buf  , 0, jc._buf.length));
    }
    @Override public String getName() { return "XEC_ClassLoader"; }
  }

  // A tree-structured set of hashmaps, mirroring the actual org.xvm.xec directory structure
  public static class XFileSys {
    public final HashMap<String,JCodes> _jcodes; // Local class files
    public final HashMap<String,XFileSys> _dirs;      // Sub-directories of the same
    
    // Walk and recursively install all existing hand-made Java classes
    XFileSys( String prefix, File dir ) {
      _jcodes = new HashMap<>();
      _dirs = new HashMap<>();
      if( dir != null )
        for( File file : dir.listFiles() ) {
          String fname = file.getName();
          if( file.isDirectory() ) {
            _dirs.put(fname,new XFileSys(prefix+"."+fname,file));
          } else {
            if( fname.endsWith(".class") ) {
              // e.g. "org.xvm.xec.XRunClz"
              String base = fname.substring(0,fname.length()-6);
              String name = prefix+"."+base;
              _jcodes.put(name,new JCodes(name,file));
            }
          }
      }
    }

    XFileSys pack(String clzname, boolean hasBase ) {
      // org.xvm.xec.PACK0.PACK1.PACKN.BASECLASSNAME
      assert clzname.startsWith(XEC.XCLZ);
      // .PACK0.PACK1.PACKN.BASECLASSNAME
      String packbase = clzname.substring(XEC.XCLZ.length());
      // .PACK0.PACK1.PACKN
      String pack = hasBase ? packbase.substring(0,packbase.lastIndexOf('.')) : packbase;
      return _pack(pack,hasBase);
    }

    // .PACK0.PACK1.PACKN or null if it does not exist
    XFileSys _pack( String pack, boolean create ) {
      if( pack.isEmpty() ) return this;
      int idx = pack.indexOf('.',1);
      String spack = idx == -1 ? pack.substring(1) : pack.substring(1,idx);
      XFileSys dir = _dirs.get(spack);
      if( dir!=null )
        return idx == -1 ? dir : dir._pack(pack.substring(idx),create);
      if( !create )
        return null;
      dir = new XFileSys(null,null);
      _dirs.put(spack,dir);
      return dir;
    }

    
    JCodes get(String clzname) {
      return pack(clzname,true)._jcodes.get(clzname);
    }
    public Class<XTC> klass(String clz) {
      JCodes jc = get(clz);
      return jc == null ? null : jc._klass;
    }
    public Class<XTC> klass(ClassPart clz) {
      return clz._tclz==null ? null : klass(clz._tclz.qualified_name());
    }
    
    JCodes put( String clzname, JCodes jc ) {
      pack(clzname,true)._jcodes.put(clzname,jc);
      return jc;
    }

  }

  
  // Locally compiled java class files "file system".
  private static class XFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    public XFileManager(StandardJavaFileManager man) { super(man); }
    
    @Override public XClzLoader getClassLoader(Location location) { return LOADER; }
    // javac will get this JCodes and fill it; we map it before it gets filled
    @Override public JavaFileObject getJavaFileForOutput(Location ignore, String name, Kind kind, FileObject sibling) {
      JCodes jc = new JCodes(name, kind);
      return XFM.put(name,jc);
    }

    // Intercept my JCodes looking for the fully qualified Java name.
    // Other names belong to e.g. the base system modules.
    @Override public String inferBinaryName(Location location, JavaFileObject file) {
      return file instanceof JCodes jc
        ? jc._name
        : super.inferBinaryName(location,file);
    }

    // Intercept internally compiled class files, and admit they exist.
    @Override public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
      if( location == StandardLocation.CLASS_PATH && packageName.startsWith(XEC.XCLZ) ) {
        // XTC Package name with leading XEC.XCLZ stripped off
        return new PackIter(packageName);
      }
      return super.list(location,packageName,kinds,recurse);
    }
    private class PackIter implements Iterable<JavaFileObject> {
      final String _pack;
      PackIter(String pack) { _pack = pack; }
      // Due to generic weirdness, cannot just return "_map.values().iterator()"
      // but need to make a private class with generic type JavaFileObject and
      // "next()" returns a JCodes which implements JavaFileObject.
      @Override public Iterator<JavaFileObject> iterator() { return new Iter(); }
      private class Iter implements Iterator<JavaFileObject> {
        Iterator<JCodes> _iter;
        Iter() {
          String pack = _pack;
          XFileSys xfm = XFM.pack(_pack,false);
          _iter = xfm==null ? null : xfm._jcodes.values().iterator();
        }
        @Override public boolean hasNext() { return _iter != null && _iter.hasNext(); }
        @Override public JCodes next() { return _iter.next(); }
      }
      
    }

  }
}
