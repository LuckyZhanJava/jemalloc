package com.lonicera.alloc;

import java.nio.ByteBuffer;
import java.util.Objects;

class UnpooledChunk implements Chunk {
  private ByteBuffer memory;
  public UnpooledChunk(int capacity){
    this.memory = ByteBuffer.allocateDirect(capacity);
  }

  public boolean alloc(ByteBuf byteBuf, int capacity){
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteBuffer memory(){
    return memory;
  }

  @Override
  public int usage() {
    return 100;
  }

  @Override
  public void release(long handle){
    memory = null;
  }

  @Override
  public void chunkList(ChunkList chunkList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean alloc(PooledByteBuf byteBuf, int capacity) {
    throw new UnsupportedOperationException();
  }

  public String toString(){
    return "Chunk@" + Objects.hashCode(this) + "[" + usage() + "]";
  }
}
