package com.lonicera.alloc;

import java.util.Objects;

class Arena {

  private static final int MIN_RUN_SIZE = 4096;
  private int chunkSize;
  private int halfChunkSize;
  private int runSize;
  private int subPageSize;
  private int halfSubPageSize;
  private int quantumSize;
  private int halfQuantumSize;
  private int tinySize;

  private int log2TinySize;
  private int log2QuantumSize;
  private int log2SubPageSize;

  private ArenaAllocCache allocCache;

  private ChunkList qInit;
  private ChunkList q0;
  private ChunkList q25;
  private ChunkList q50;
  private ChunkList q75;
  private ChunkList q100;

  private SmallAllocNode[] smallAllocs;

  private int quantumAllocOffset;
  private int subPageAllocOffset;

  public Arena() {
    this(
        2 * 1024 * 1024,
        4096,
        1024,
        16,
        2
    );
  }

  public Arena(int chunkSize, int runSize, int subPageSize, int quantumSize, int tinySize) {
    log2PowerOfTwo(chunkSize, "chunkSize");
    int log2RunSize = log2PowerOfTwo(runSize, "runSize");
    this.log2SubPageSize = log2PowerOfTwo(subPageSize, "minSubPageSize");
    this.log2QuantumSize = log2PowerOfTwo(quantumSize, "quantumSize");
    this.log2TinySize = log2PowerOfTwo(tinySize, "tinySize");
    if (!(chunkSize > runSize && runSize > subPageSize && subPageSize > quantumSize
        && quantumSize > tinySize)) {
      throw new IllegalArgumentException(
          "require : run size > subpage size > quantum size > tiny size");
    }
    this.chunkSize = chunkSize;
    this.halfChunkSize = chunkSize >> 1;
    this.runSize = runSize;
    if (this.runSize < MIN_RUN_SIZE) {
      throw new IllegalArgumentException("run size must > 4k");
    }
    this.subPageSize = subPageSize;
    this.halfSubPageSize = subPageSize >> 1;
    this.quantumSize = quantumSize;
    this.halfQuantumSize = this.quantumSize >> 1;
    this.tinySize = tinySize;

    int tinyCount = log2QuantumSize - log2TinySize - 1;
    quantumAllocOffset = tinyCount;
    int quantumCount = 1 << (log2QuantumSize - log2TinySize - 1);
    subPageAllocOffset = tinyCount + quantumCount;
    int subPageCount = log2SubPageSize - log2QuantumSize - 1;

    this.smallAllocs = new SmallAllocNode[tinyCount + quantumCount + subPageCount];

    this.allocCache = new ArenaAllocCache(
        chunkSize,
        runSize,
        64,
        subPageSize,
        64,
        quantumSize,
        64,
        tinySize,
        64
    );

    qInit = new ChunkList("QINIT", null, null, 0, 24);
    q0 = new ChunkList("Q0", qInit, null, 1, 49);
    q25 = new ChunkList("Q25", q0, null, 25, 74);
    q50 = new ChunkList("Q50", q25, null, 50, 99);
    q75 = new ChunkList("Q75", q50, null, 75, 99);
    q100 = new ChunkList("Q100", q75, null, 100, 100);

    qInit.next(q0);
    q0.next(q25);
    q25.next(q50);
    q50.next(q75);
    q75.next(q100);

  }

  SmallAllocNode smallAllocHead(int alignCapacity) {
    int log2Align = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(alignCapacity);
    int index;
    if (alignCapacity < quantumSize) {
      index = log2Align - log2TinySize;
    } else if (alignCapacity < subPageSize) {
      index = quantumAllocOffset + 1 << (log2Align - log2QuantumSize);
    } else {
      index = subPageAllocOffset + log2Align - log2SubPageSize;
    }

    SmallAllocNode head = smallAllocs[index];
    if (head != null) {
      return head;
    }
    synchronized (smallAllocs) {
      smallAllocs[index] = new SmallAllocNode(
          null,
          null,
          0,
          0,
          0,
          null,
          0,
          alignCapacity
      );
      return smallAllocs[index];
    }
  }

  public ByteBuf alloc(int capacity) {

    if (capacity > halfChunkSize) {
      return allocHuge(capacity);
    }

    int alignCapacity = alignCapacity(capacity);

    synchronized (allocCache) {
      PooledByteBuf cache = allocCache.alloc(alignCapacity);
      if (cache != null) {
        cache.cycle();
        return cache;
      }
    }

    PooledByteBuf byteBuf = new PooledByteBuf();

    if (alignCapacity < runSize) {
      SmallAllocNode head = smallAllocHead(alignCapacity);
      if (head.next() != null) {
        synchronized (head) {
          boolean success = head.next().alloc(byteBuf, alignCapacity);
          if (success) {
            return byteBuf;
          }
        }
      }
    }

    synchronized (this) {
      if (q50.alloc(byteBuf, alignCapacity)) {
        return byteBuf;
      }
      if (q25.alloc(byteBuf, alignCapacity)) {
        return byteBuf;
      }
      if (q0.alloc(byteBuf, alignCapacity)) {
        return byteBuf;
      }
      if (qInit.alloc(byteBuf, alignCapacity)) {
        return byteBuf;
      }
      if (q75.alloc(byteBuf, alignCapacity)) {
        return byteBuf;
      }

      PooledChunk pooledChunk = new PooledChunk(this, chunkSize, runSize, qInit, allocCache);

      boolean success = pooledChunk.alloc(byteBuf, alignCapacity);

      qInit.add(pooledChunk);

      if (success) {
        return byteBuf;
      }
      throw new Error(); // should never happen
    }

  }

  private ByteBuf allocHuge(int capacity) {
    Chunk chunk = new UnpooledChunk(capacity);
    return new UnpooledByteBuf(chunk);
  }

  private int alignCapacity(int capacity) {
    if (capacity > halfQuantumSize && capacity <= halfSubPageSize) {
      int remain = capacity % quantumSize;
      if (remain != 0) {
        return quantumSize * (capacity / quantumSize + 1);
      } else {
        return capacity;
      }
    }

    if (capacity < tinySize) {
      return tinySize;
    }

    int offset = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(capacity);
    int align = 1 << offset;
    if (align >= capacity) {
      return align;
    } else {
      return align << 1;
    }
  }

  private int log2PowerOfTwo(int num, String fieldName) {
    if (num < 2) {
      throw new IllegalArgumentException(fieldName + " must >= 2");
    }
    int log2 = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(num);
    if (1 << log2 != num) {
      throw new IllegalArgumentException(fieldName + " must be power of two");
    }
    return log2;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Arena@" + Objects.hash(this) + "\r\n");
    sb.append(qInit + "\r\n");
    sb.append(q0 + "\r\n");
    sb.append(q25 + "\r\n");
    sb.append(q50 + "\r\n");
    sb.append(q75 + "\r\n");
    sb.append(q100 + "\r\n");
    return sb.toString();
  }
}
