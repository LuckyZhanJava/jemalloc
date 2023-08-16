package com.lonicera.alloc;

public interface ByteBuf {

  byte readByte();

  void writeByte(byte b);

  int readBytes(byte[] bytes);

  int writeBytes(byte[] bytes);

  void release();

  void resetReaderIndex();

  void resetWriterIndex();
}
