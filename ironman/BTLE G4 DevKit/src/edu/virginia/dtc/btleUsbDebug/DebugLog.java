package edu.virginia.dtc.btleUsbDebug;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;


import android.content.Context;

public class DebugLog {

  static private PrintStream out = null;
  static private boolean printLog = false;
 
  public static final int DebugLevelNone   = 0;
  public static final int DebugLevelInfo   = 1;
  public static final int DebugLevelDetail = 2;
  
  private static int debugLevel = DebugLevelDetail;

  public static void setDebugLevel(int level) {
    debugLevel = level;
  }

  public DebugLog(Context c, String dir, String filename) {
    try {
        out = new PrintStream(
            new FileOutputStream(
                new File(dir, filename), true), true);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    
    printLog = true;
  }

  static synchronized public void printDate(int level) {
    // skip if not the correct level
    if(level > debugLevel)
      return;
    
    long javatime = System.currentTimeMillis();
    Date date = new Date(javatime);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");

    printf(sdf.format(date));
  }

  static synchronized public void println(int level, String line) {
    // skip if not the correct level
    if(level > debugLevel)
      return;
    
    if(printLog == true)
      out.println(line);
    
    printDate(level);
    System.out.println(line);
  }
  
  static synchronized public void print(String line) {
    if(printLog == true)
      out.print(line);
    
    System.out.print(line);
  }
  
  static synchronized public void printf(String format, Object... args) {
    if(printLog == true)
      out.printf(format, args);
    
    System.out.printf(format, args);
  }
  
}
