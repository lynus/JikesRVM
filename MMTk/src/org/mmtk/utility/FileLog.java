/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility;

import static org.mmtk.utility.Constants.*;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Error and trace logging.
 */
@Uninterruptible
public class FileLog extends Log{


  private static final int MESSAGE_BUFFER_SIZE = 3000;
  private static final String OVERFLOW_MESSAGE = "... WARNING: Text truncated.\n";
  private static final char OVERFLOW_MESSAGE_FIRST_CHAR = OVERFLOW_MESSAGE.charAt(0);
  private static final int OVERFLOW_SIZE = OVERFLOW_MESSAGE.length();
  private static final int TEMP_BUFFER_SIZE = 20;
  private static final String HEX_PREFIX = "0x";
  private static final int LOG_BITS_IN_HEX_DIGIT = 2;
  private static final int LOG_HEX_DIGITS_IN_BYTE = LOG_BITS_IN_BYTE - LOG_BITS_IN_HEX_DIGIT;
  private static final char [] hexDigitCharacter =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
  private static final char NEW_LINE_CHAR = '\n';

  private final char [] buffer = new char[MESSAGE_BUFFER_SIZE + OVERFLOW_SIZE];


  private int bufferIndex = 0;
  private boolean overflow = false;
  private char overflowLastChar = '\0';
  private boolean threadIdFlag = false;
  private final char[] tempBuffer = new char[TEMP_BUFFER_SIZE];
  private static Log log;
  public FileLog() {
    for (int i = 0; i < OVERFLOW_SIZE; i++) {
      buffer[MESSAGE_BUFFER_SIZE + i] = OVERFLOW_MESSAGE.charAt(i);
    }
    if (VM.strings.openFile()) {
      Log.writeln("FileLog open success.");
      log = this;
    } else {
      Log.writeln("Filelog open failed, fall back to Log");
      log = new Log();
    }
  }

  public static void write(boolean b) {
    write(b ? "true" : "false");
  }
  public static void write(char c) {
    add(c);
  }

  public static void write(long l) {
    boolean negative = l < 0;
    int nextDigit;
    char nextChar;
    int index = TEMP_BUFFER_SIZE - 1;
    char[] intBuffer = getIntBuffer();

    nextDigit = (int) (l % 10);
    nextChar = hexDigitCharacter[negative ? - nextDigit : nextDigit];
    intBuffer[index--] = nextChar;
    l = l / 10;

    while (l != 0) {
      nextDigit = (int) (l % 10);
      nextChar = hexDigitCharacter[negative ? - nextDigit : nextDigit];
      intBuffer[index--] = nextChar;
      l = l / 10;
    }

    if (negative) {
      intBuffer[index--] = '-';
    }

    for (index++; index < TEMP_BUFFER_SIZE; index++) {
      add(intBuffer[index]);
    }
  }

  public static void write(double d) {
    write(d, 2);
  }

  public static void write(double d, int postDecimalDigits) {
    if (d != d) {
      write("NaN");
      return;
    }
    if (d > Integer.MAX_VALUE) {
      write("TooBig");
      return;
    }
    if (d < -Integer.MAX_VALUE) {
      write("TooSmall");
      return;
    }

    boolean negative = (d < 0.0);
    d = negative ? (-d) : d;       // Take absolute value
    int ones = (int) d;
    int multiplier = 1;
    while (postDecimalDigits-- > 0)
      multiplier *= 10;
    int remainder = (int) (multiplier * (d - ones));
    if (remainder < 0) remainder = 0;
    if (negative) write('-');
    write(ones);
    write('.');
    while (multiplier > 1) {
      multiplier /= 10;
      write(remainder / multiplier);
      remainder %= multiplier;
    }
  }

  public static void write(char[] c) {
    write(c, c.length);
  }

  public static void write(char[] c, int len) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(len <= c.length);
    for (int i = 0; i < len; i++) {
      add(c[i]);
    }
  }

  public static void write(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      add((char)b[i]);
    }
  }

  public static void write(String s) {
    add(s);
  }

  public static void write(Word w) {
    writeHex(w, BYTES_IN_ADDRESS);
  }

  public static void writeDec(Word w) {
    if (BYTES_IN_ADDRESS == 4) {
      write(w.toInt());
    } else {
      write(w.toLong());
    }
  }

  public static void write(Address a) {
    writeHex(a.toWord(), BYTES_IN_ADDRESS);
  }

  public static void write(String s, Address a) {
    write(s);
    write(a);
  }

  public static void write(String s, Extent e) {
    write(s);
    write(e);
  }

  public static void write(String s, ObjectReference objRef) {
    write(s);
    write(objRef);
  }

  public static void write(String s, long l) {
    write(s);
    write(l);
  }

  public static void write(ObjectReference o) {
    writeHex(o.toAddress().toWord(), BYTES_IN_ADDRESS);
  }

  public static void write(Offset o) {
    writeHex(o.toWord(), BYTES_IN_ADDRESS);
  }

  public static void write(Extent e) {
    writeHex(e.toWord(), BYTES_IN_ADDRESS);
  }

  public static void writeln() {
    writelnWithFlush(true);
  }

  public static void writeln(boolean b) {
    writeln(b, true);
  }

  public static void writeln(char c) {
    writeln(c, true);
  }

  public static void writeln(long l) {
    writeln(l, true);
  }

  public static void writeln(double d) {
    writeln(d, true);
  }

  public static void writeln(double d, int postDecimalDigits) {
    writeln(d, postDecimalDigits, true);
  }

  public static void writeln(char [] ca) {
    writeln(ca, true);
  }

  public static void writeln(char [] ca, int len) {
    writeln(ca, len, true);
  }


  public static void writeln(byte [] b) {
    writeln(b, true);
  }

  public static void writeln(String s) {
    writeln(s, true);
  }

  public static void writeln(Word w) {
    writeln(w, true);
  }

  public static void writeln(Address a) {
    writeln(a, true);
  }

  public static void writeln(ObjectReference o) {
    writeln(o, true);
  }

  public static void writeln(Offset o) {
    writeln(o, true);
  }

  public static void writeln(Extent e) {
    writeln(e, true);
  }

  public static void writelnNoFlush() {
    writelnWithFlush(false);
  }

  public static void writeln(boolean b, boolean flush) {
    write(b);
    writelnWithFlush(flush);
  }
  public static void writeln(char c, boolean flush) {
    write(c);
    writelnWithFlush(flush);
  }

  public static void writeln(long l, boolean flush) {
    write(l);
    writelnWithFlush(flush);
  }

  public static void writeln(double d, boolean flush) {
    write(d);
    writelnWithFlush(flush);
  }

  public static void writeln(double d, int postDecimalDigits, boolean flush) {
    write(d, postDecimalDigits);
    writelnWithFlush(flush);
  }


  public static void writeln(char[] ca, boolean flush) {
    write(ca);
    writelnWithFlush(flush);
  }

  public static void writeln(char[] ca, int len, boolean flush) {
    write(ca, len);
    writelnWithFlush(flush);
  }

  public static void writeln(byte[] b, boolean flush) {
    write(b);
    writelnWithFlush(flush);
  }

  public static void writeln(String s, boolean flush) {
    write(s);
    writelnWithFlush(flush);
  }

  public static void writeln(String s, long l) {
    write(s);
    writeln(l);
  }

  public static void writeln(String s, Extent extent) {
    write(s);
    write(extent);
    writeln();
  }

  public static void writeln(String s, ObjectReference objRef) {
    write(s);
    write(objRef);
    writeln();
  }

  public static void writeln(String s, Offset offset) {
    write(s);
    write(offset);
    writeln();
  }

  public static void writeln(String s, Word word) {
    write(s);
    write(word);
    writeln();
  }

  public static void writeln(Word w, boolean flush) {
    write(w);
    writelnWithFlush(flush);
  }

  public static void writeln(Address a, boolean flush) {
    write(a);
    writelnWithFlush(flush);
  }

  public static void writeln(ObjectReference o, boolean flush) {
    write(o);
    writelnWithFlush(flush);
  }

  public static void writeln(Offset o, boolean flush) {
    write(o);
    writelnWithFlush(flush);
  }

  public static void writeln(Extent e, boolean flush) {
    write(e);
    writelnWithFlush(flush);
  }

  public static void writeln(String s, Address a) {
    write(s);
    writeln(a);
  }

  public static void prependThreadId() {
    getLog().setThreadIdFlag();
  }

  public static void flush() {
    getLog().flushBuffer();
  }

  private static void writelnWithFlush(boolean flush) {
    add(NEW_LINE_CHAR);
    if (flush) {
      flush();
    }
  }

  private static void writeHex(Word w, int bytes) {
    int hexDigits = bytes * (1 << LOG_HEX_DIGITS_IN_BYTE);
    int nextDigit;

    write(HEX_PREFIX);

    for (int digitNumber = hexDigits - 1; digitNumber >= 0; digitNumber--) {
      nextDigit = w.rshl(digitNumber << LOG_BITS_IN_HEX_DIGIT).toInt() & 0xf;
      char nextChar = hexDigitCharacter[nextDigit];
      add(nextChar);
    }
  }

  private static void add(char c) {
    getLog().addToBuffer(c);
  }

  private static void add(String s) {
    getLog().addToBuffer(s);
  }

  protected static Log getLog() {
    return log;
  }

  protected void addToBuffer(char c) {
    if (bufferIndex < MESSAGE_BUFFER_SIZE) {
      buffer[bufferIndex++] = c;
    } else {
      overflow = true;
      overflowLastChar = c;
    }
  }

  protected void addToBuffer(String s) {
    if (bufferIndex < MESSAGE_BUFFER_SIZE) {
      bufferIndex += VM.strings.copyStringToChars(s, buffer, bufferIndex, MESSAGE_BUFFER_SIZE + 1);
      if (bufferIndex == MESSAGE_BUFFER_SIZE + 1) {
        overflow = true;
        // We don't bother setting OVERFLOW_LAST_CHAR, since we don't have an
        // MMTk method that lets us peek into a string. Anyway, it's just a
        // convenience to get the newline right.
        buffer[MESSAGE_BUFFER_SIZE] = OVERFLOW_MESSAGE_FIRST_CHAR;
        bufferIndex--;
      }
    } else {
      overflow = true;
    }
  }

  protected void flushBuffer() {
    int newlineAdjust = overflowLastChar == NEW_LINE_CHAR ? 0 : -1;
    int totalMessageSize = overflow ? (MESSAGE_BUFFER_SIZE + OVERFLOW_SIZE + newlineAdjust) : bufferIndex;
    flushIO(threadIdFlag, buffer, totalMessageSize);
    threadIdFlag = false;
    overflow = false;
    overflowLastChar = '\0';
    bufferIndex = 0;
  }

  @Inline
  protected void flushIO(boolean threadIdFlag, char [] buffer, int len) {
    if(threadIdFlag)
      VM.strings.fwriteThreadId(buffer, len);
    else
      VM.strings.fwrite(buffer, len);
  }

  protected void setThreadIdFlag() {
    threadIdFlag = true;
  }

  protected static char[] getIntBuffer() {
    return getLog().getTempBuffer();
  }

  protected char[] getTempBuffer() {
    return tempBuffer;
  }
}
