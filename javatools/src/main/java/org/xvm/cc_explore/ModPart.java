package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.xclz.XClz;
import org.xvm.cc_explore.xclz.XClzBuilder;

import java.util.ArrayList;

/**
   Module component
 */
public class ModPart extends ClassPart {
  public final ModuleType _t;   // Type of Module
  public final LitCon _dir;     // Directory?
  public final LitCon _time;    // Creation timestamp?

  public final Version _version; // This version
  public final VerTree _allowedVers; //
  public final ArrayList<Version> _prefers; 

  private XClzBuilder _xbuild; // Cached Java class hierarchy version of this module
  
  ModPart( Part par, int nFlags, ModCon con, CondCon cond, CPool X ) {
    super(par,nFlags,con,cond,X,Part.Format.MODULE);

    _t = ModuleType.valueOf(X.u8());
    VerCon version = null;
    
    if( isFingerprint() ) {
      _allowedVers = new VerTree();
      for( int i=0, len = X.u31(); i < len; i++ )
        _allowedVers.put(((VerCon)X.xget()).ver(), X.u1());

      _prefers = new ArrayList<>();
      for( int i=0, len=X.u31(); i < len; i++ ) {
        Version ver = ((VerCon)X.xget()).ver();
        if( !_prefers.contains(ver) ) // Duplicate filtering
          _prefers.add(ver);
      }
    } else {
      if( X.u1() )
        version = (VerCon)X.xget();
      _allowedVers = null;
      _prefers = null;
    }
    _version = version==null ? null : version.ver();
    _dir  = (LitCon)X.xget();
    _time = (LitCon)X.xget();
  }
  
  // Fingerprints link against a repo to get the actual module
  @Override Part link_as( XEC.ModRepo repo ) {
    return isFingerprint() ? repo.get(_name) : this;
  }
  
  /**
   * Check if this is a fingerprint module, which is a secondary (not main) module in a file
   * structure that represents the set of external dependencies on a particular imported module
   * from the main module and any embedded modules.
   *
   * @return true iff this module represents the "fingerprint" of an external module dependency
   */
  public boolean isFingerprint() {
    return switch( _t ) {
    case Optional, Desired, Required -> true;
    case Primary, Embedded           -> false;
    };
  }

  // Find a method by name, or return null
  public MethodPart method(String s) {
    Part p = child(s);
    if( p instanceof MMethodPart mm && s.equals(mm._name) ) {
      if( mm._name2kid.size()!=1 ) throw XEC.TODO(); // Disambiguate?
      return (MethodPart)mm.child(s);
    }
    return null;
  }

  // Return a Java Class for this XTC Module
  public XClz xclz() { return (_xbuild==null ? (_xbuild=new XClzBuilder(this)) : _xbuild)._xclz; }
  

  // ----- ModuleType enumeration ----------------------------------------------------------------
  /**
   * A module serves one of three primary purposes:
   * <ul>
   * <li>The primary module is the module for which the FileStructure exists;</li>
   * <li>A fingerprint module represents an imported module;</li>
   * <li>An embedded module is an entire module that is embedded within the FileStructure in order
   *     to fully satisfy the dependencies of an import.</li>
   * </ul>
   * <p/>
   * A fingerprint module has three levels that indicate how desired or required it is:
   * <ul>
   * <li>Optional indicates that the dependency is supported, but leaves the decision regarding
   *     whether or not to import the module to the linker;</li>
   * <li>Desired also indicates that the dependency is supported, but even though the dependency
   *     is not required, the linker should make a best effort to obtain and link in the
   *     module;</li>
   * <li>Required indicates that the primary module can not be loaded unless the fingerprint
   *     module is obtained and linked in by the linker.</li>
   * </ul>
   */
  enum ModuleType {
    Primary, Optional, Desired, Required, Embedded;

    /** Look up a Format enum by its ordinal. */
    public static ModuleType valueOf(int i) { return MODULE_TYPES[i]; }
    private static final ModuleType[] MODULE_TYPES = ModuleType.values();
  }

}
