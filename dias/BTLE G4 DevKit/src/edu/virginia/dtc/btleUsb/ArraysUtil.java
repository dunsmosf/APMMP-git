package edu.virginia.dtc.btleUsb;

public class ArraysUtil {

  public static String toString(byte[] array) {
    if(array == null)
      return "null";
    
    String ret = "{";
    for(int i=0; i < array.length; i++)
      {
        String c = String.format("%02X", array[i]);
        if(i < array.length-1)
          ret += c + ", ";
        else
          ret += c;
      }
    ret += "};";
    
    return ret;
  }
}
