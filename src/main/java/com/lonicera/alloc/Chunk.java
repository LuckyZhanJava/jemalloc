package com.lonicera.alloc;

import java.nio.ByteBuffer;

interface Chunk {
  ByteBuffer memory();
  int usage();
  void release(long handle);
  void chunkList(ChunkList chunkList);
  boolean alloc(PooledByteBuf byteBuf, int capacity);
}
