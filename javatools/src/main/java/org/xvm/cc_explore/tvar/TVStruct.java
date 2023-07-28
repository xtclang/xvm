package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.XEC;

import java.util.Arrays;
import java.util.HashSet;

/** A type struct.  
 *
 * Has (recursive) fields with labels.  A struct can be open or closed; open
 * structs allow more fields to appear.  Open structs come from FieldNodes
 * which know that a particular field must be present, and also maybe more.
 * Closed structs come from StructNodes which list all the present fields.
 * If field #0 is present, it is named "." and holds the Clazz for this struct.
 * 
 * Fields may be pinned or not.  Pinned fields cannot lift to a superclazz, and
 * come from StructNodes.  Unpinned fields are not necessarily in the correct
 * struct, and may migrate up the superclazz chain.  
 */
public class TVStruct extends TVar {
  // Handy zero-length array variants
  public static final String[] FLDS0 = new String[0];
  public static final TVar  [] TVS0  = new TVar  [0];
  public static final String CLZ = " super"; // Bogus super-class field

  // The many names this thing is called
  public HashSet<String> _names;
  
  // True if more fields can be added.  Generally false for a known Struct, and
  // true for a Field reference to an unknown struct.
  public boolean _open;
  
  // The set of field labels, 1-to-1 with TVar field contents.  Most field
  // operations are UNORDERED, so we generally need to search the fields by
  // string - except for the clazz field "." always in slot 0.
  private String[] _flds;       // Field labels
  
  private int _max;             // Max set of in-use flds/args
  
  // No fields
  public TVStruct(String name, boolean open) { this(name,FLDS0,TVS0,open); }
  private static TVar[] leafs(int len) {
    TVar[] ls = new TVar[len];
    for( int i=0; i<len; i++ ) ls[i] = new TVLeaf();
    return ls;
  }
  // Made from a StructNode; fields are known, so this is pinned closed
  public TVStruct( String name, String[] flds ) { this(name,flds,leafs(flds.length)); }
  // Made from a StructNode; fields are known, so this is pinned closed
  public TVStruct( String name, String[] flds, TVar[] tvs ) { this(name,flds,tvs,false); }
  // Made from a Field or SetField; fields are pinned known but might be open
  public TVStruct( String name, String[] flds, TVar[] tvs, boolean open ) {
    super(tvs);
    _names = new HashSet<String>();
    _names.add(name);
    _flds = flds;
    _open = open;
    _max = flds.length;
    assert tvs.length==_max;
  }

  // Clazz for this struct, or null for ClazzClazz
  public TVStruct clz() {
    if( _max==0 || !_flds[0].equals(CLZ) ) return null;
    return (TVStruct)arg(0);
  }
  
  // Common accessor not called 'find' which already exists
  public int idx( String fld ) {
    for( int i=0; i<_max; i++ ) if( S.eq(_flds[i],fld) ) return i;
    return -1;
  }
  
  public void add_fld(String fld) { add_fld(fld, new TVLeaf()); }
  public void add_fld(String fld, TVar tvf) {
    int idx = idx(fld);
    if( idx!=-1 ) {
      arg(idx).unify(tvf);
      return;
    }
    
    if( _max == _flds.length ) {
      int len=1;
      while( len<=_max ) len<<=1;
      _flds = Arrays.copyOf(_flds,len);
      _args = Arrays.copyOf(_args,len);
    }
    _flds[_max] = fld;
    _args[_max] = tvf;
    _max++;
  }

  // Remove
  void del_fld(int idx) {
    String  fld = _flds[idx];
    TVar    tv3 = _args[idx];
    assert !fld.equals(CLZ); // Never remove clazz
    _args[idx] = _args[_max-1];
    _flds[idx] = _flds[_max-1];
    _max--;
  }
  // Remove field
  public void del_fld(String fld) {
    int idx = idx(fld);
    if( idx != -1 ) del_fld(idx);
  }
  
  @Override public int len() { return _max; }  

  public String fld( int i ) { assert !unified();  return _flds[i]; }
  
  // Return the TVar for field 'fld' or null if missing
  public TVar arg(String fld) {
    assert !unified();
    int i = idx(fld);
    return i>=0 ? arg(i) : null;
  }
  
  // Return the TVar for field 'fld' or null if missing, with OUT rollups
  public TVar debug_arg(String fld) {
    int i = idx(fld);
    return i>=0 ? debug_arg(i) : null;
  }

  public boolean is_open() { return _open; }
  
  // Close if open
  public void close() { _open=false; }

  // -------------------------------------------------------------
  // Union LHS into RHS
  @Override public void _union_impl( TVar tv3 ) {
    TVStruct ts = (TVStruct)tv3; // Invariant when called
    ts._open = ts._open & _open;
    ts._names.addAll(_names);
  }

  // Unify LHS into RHS
  @Override int _unify_impl( TVar tv3 ) {
    TVStruct that = (TVStruct)tv3; // Invariant when called
    assert !this.unified() && !that.unified();
    TVStruct thsi = this;
    
    // Unify LHS fields into RHS
    boolean open = that.is_open();
    for( int i=0; i<thsi._max; i++ ) {
      TVar fthis = thsi.arg(i);       // Field of this      
      String key = thsi._flds[i];
      int ti = that.idx(key);
      if( ti == -1 ) {          // Missing field in that
        if( open ) that.add_fld(key,fthis); // Add to RHS
        else       that.del_fld(key);       // Remove from RHS
      } else {
        TVar fthat = that.arg(ti);  // Field of that
        fthis._unify(fthat);        // Unify both into RHS
        // Progress may require another find()
        thsi = (TVStruct)thsi.find();
        that = (TVStruct)that.find();
      }
    }

    // Fields on the RHS are aligned with the LHS also
    for( int i=0; i<that._max; i++ ) {
      String key = that._flds[i];
      int idx = thsi.idx(key);
      if( idx== -1 &&                     // Missing field in this
          !is_open() ) thsi.del_fld(key); // Drop from RHS
    }

    assert !that.unified(); // Missing a find
    return 0;
  }
  
  // -------------------------------------------------------------
  @Override int _fresh_unify_impl( TVar tv3 ) {
    boolean missing = false;
    assert !unified() && !tv3.unified();

    TVStruct that = (TVStruct)tv3.find();
    that._names.addAll(_names);

    for( int i=0; i<_max; i++ ) {
      TVar lhs = arg(i);
      int ti = that.idx(_flds[i]);
      if( ti == -1 ) {          // Missing in RHS              
        if( is_open() || that.is_open() ) {
          TVar nrhs = lhs._fresh();
          if( that.is_open() ) {
            that.add_fld(_flds[i],nrhs);
          } else { // RHS not open, put copy of LHS into RHS with miss_fld error
            that.add_fld(_flds[i],new TVMiss());
          }
        } else missing = true; // Else neither side is open, field is not needed in RHS
        
      } else {
        TVar rhs = that.arg(ti); // Lookup via field name
        lhs._fresh_unify(rhs);
      }
      assert !unified();      // If LHS unifies, VARS is missing the unified key
      that = (TVStruct)that.find(); // Might have to update
    }

    // Fields in RHS and not the LHS are also merged; if the LHS is open we'd
    // just copy the missing fields into it, then unify the structs (shortcut:
    // just skip the copy).  If the LHS is closed, then the extra RHS fields
    // are removed.
    if( !is_open() && (_max != that._max || missing) )
      for( int i=0; i<that._max; i++ ) {
        TVar lhs = arg(that._flds[i]); // Lookup vis field name
        if( lhs==null )
          that.del_fld(i--);
      }

    // If LHS is closed, force RHS closed
    that._open &= _open;

    return 0;
  }
  
  
  // -------------------------------------------------------------
  @Override boolean _trial_unify_ok_impl( TVar tv3 ) {
    TVStruct that = (TVStruct)tv3; // Invariant when called
    for( int i=0; i<_max; i++ ) {
      TVar lhs = arg(i);
      TVar rhs = that.arg(_flds[i]); // RHS lookup by field name
      if( lhs!=rhs && rhs!=null && !lhs._trial_unify_ok(rhs) )
        return false;           // Child fails to unify
    }
    // Allow unification with extra fields.  The normal unification path
    // will not declare an error, it will just remove the extra fields.
    return this.mismatched_child(that) && that.mismatched_child(this);
  }

  private boolean mismatched_child(TVStruct that ) {
    if( that.is_open() ) return true; // Missing fields may add later
    for( int i=0; i<_max; i++ )
      if( that.arg(_flds[i])==null ) // And missing key in RHS
        return false;          // Trial unification failed
    for( String name : _names )
      if( !that._names.contains(name) )
        return false;
    return true;
  }

  // -------------------------------------------------------------
  @Override public TVStruct copy() {
    TVStruct st = (TVStruct)super.copy();
    st._flds = _flds.clone();
    st._names = (HashSet<String>)_names.clone();
    return st;
  }

  @Override public VBitSet _get_dups_impl(VBitSet visit, VBitSet dups, boolean debug) {
    for( int i=0; i<_max; i++ )
      _args[i]._get_dups(visit,dups,debug);
    return dups;
  }

  
  @Override SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    sb.p(_names.toString());
    if( _args==null  ) return sb.p(_open ? "(...)" : "()");

    // Print clazz field up front.
    TVar clz = debug_arg(CLZ);
    if( clz!=null ) clz._str(sb,visit,dups,debug).p(":");    
    boolean is_tup = is_tup(debug), once=_open;
    sb.p(is_tup ? "(" : "@{");
    for( int idx : sorted_flds() ) {
      if( CLZ.equals(_flds[idx]) ) continue; // CLZ already printed up front
      if( !is_tup ) {                         // Skip tuple field names
        sb.p(_flds[idx]);
        sb.p("= ");
      }
      if( _args[idx] == null ) sb.p("_");
      else _args[idx]._str(sb,visit,dups,debug);
      sb.p(is_tup ? ", " : "; ");
      once=true;
    }
    if( _open ) sb.p(" ..., ");
    if( once ) sb.unchar(2);
    sb.p(!is_tup ? "}" : ")");
    return sb;
  }

  boolean is_tup(boolean debug) {
    if( _max==0 ) return true;
    boolean label=true;
    for( int i=0; i<len(); i++ ) {
      char c = _flds[i].charAt(0);
      if( debug && c=='&' ) return false;
      else if( Character.isDigit(c) ) label=false;
    }
    return !label;
  }

  // Stoopid hand-rolled bubble sort
  private int[] sorted_flds() {
    int[] is = new int[_max];
    for( int i=0; i<_max; i++ ) is[i] = i;
    for( int i=0; i<_max; i++ )
      for( int j=i+1; j<_max; j++ ) {
        String fi = _flds[is[i]];
        String fj = _flds[is[j]];
        if( fi!=null && (fj==null || fj.compareTo(fi) < 0) )
          { int tmp = is[i]; is[i] = is[j]; is[j] = tmp; }
      }
    return is;
  }
  
  public static void reset_to_init0() {
  }
}
