package com.lonicera.alloc;

import java.nio.ByteBuffer;

class PooledByteBuf implements ByteBuf {

  private Chunk chunk;
  private ByteBuffer memory;
  private int offset;
  private int capacity;
  private int readerIndex;
  private int writerIndex;
  private long handle;
  private ByteBuffer bufMemory;
  private ArenaAllocCache arenaCache;
  private boolean released;

  public PooledByteBuf() {

  }

  public void initByteBuf(Chunk chunk, int offset, int capacity, long handle,
      ArenaAllocCache arenaCache) {
    this.chunk = chunk;
    this.memory = chunk.memory();
    this.offset = offset;
    this.capacity = capacity;
    this.readerIndex = offset;
    this.writerIndex = offset;
    this.handle = handle;
    memory.limit(offset + capacity);
    memory.position(offset);
    bufMemory = memory.duplicate();
    this.arenaCache = arenaCache;
    this.released = false;
  }

  @Override
  public byte readByte() {
    return 0;
  }

  @Override
  public void writeByte(byte b) {

  }

  @Override
  public int readBytes(byte[] bytes) {
    return 0;
  }

  @Override
  public int writeBytes(byte[] bytes) {
    return 0;
  }

  @Override
  public void release() {
    synchronized (this) {
      if (!released) {
        if (!arenaCache.cache(this, capacity)) {
          chunk.release(handle);
          released = true;
        }
      }
    }
  }

  public void cycle() {
    readerIndex = 0;
    writerIndex = 0;
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
