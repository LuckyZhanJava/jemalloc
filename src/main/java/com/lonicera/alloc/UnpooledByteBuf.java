package com.lonicera.alloc;

import java.nio.ByteBuffer;

public class UnpooledByteBuf implements ByteBuf {
  private Chunk chunk;
  private ByteBuffer memory;
  private int offset;
  private int length;
  private int readerIndex;
  private int writerIndex;
  private long runId;
  private boolean released;

  public UnpooledByteBuf(Chunk chunk){
    this.chunk = chunk;
    this.memory = chunk.memory();
    this.offset = 0;
    this.length = memory.capacity();
    this.readerIndex = offset;
    this.writerIndex = offset;
    this.released = false;
  }

  @Override
  public byte readByte(){
    return 0;
  }

  @Override
  public void writeByte(byte b){

  }

  @Override
  public int readBytes(byte[] bytes){
    return 0;
  }

  @Override
  public int writeBytes(byte[] bytes){
    return 0;
  }

  @Override
  public void release(){
    synchronized (this){
      if(!released){
        chunk.release(runId);
        released = true;
      }
    }
  }

  @Override
  public void resetReaderIndex() {
    readerIndex = offset;
  }

  @Override
  public void resetWriterIndex() {
    writerIndex = offset;
  }
}
