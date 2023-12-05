package org.xvm.xec.ecstasy.numbers;

import org.xvm.xec.ecstasy.Const;
import org.xvm.xec.ecstasy.Orderable;
import org.xvm.xrun.Never;

import java.math.BigDecimal;
/**
     Support XTC Number
*/
public abstract class Dec64 extends DecimalFPNumber {
  final BigDecimal _bd;
  public Dec64(Never n ) {_bd=null;}
  public Dec64() {_bd=null;}
  public Dec64(String s) { _bd = new BigDecimal(s); }
}
