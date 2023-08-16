package com.lonicera.alloc;

public class ArenaAllocCache {

  private class ByteBufQueue {

    private PooledByteBuf[] bufs;
    private int size;
    private int takeIndex = 0;
    private int putIndex = 0;

    public ByteBufQueue(int size) {
      if (size <= 0) {
        throw new IllegalArgumentException("size require > 0");
      }
      this.size = size;
      bufs = new PooledByteBuf[size];
    }

    public PooledByteBuf poll() {
      if (takeIndex >= size) {
        takeIndex = takeIndex % size;
      }
      if (takeIndex < putIndex) {
        PooledByteBuf byteBuf = bufs[takeIndex];
        takeIndex++;
        return byteBuf;
      } else {
        return null;
      }
    }

    public boolean offer(PooledByteBuf byteBuf) {
      if (putIndex >= size) {
        putIndex = putIndex % size;
        if (putIndex > takeIndex) {
          return false;
        }
      }
      bufs[putIndex] = byteBuf;
      putIndex++;
      return true;
    }

  }

  private ByteBufQueue[] largeCaches;
  private ByteBufQueue[] subPageCaches;
  private ByteBufQueue[] quantumCaches;
  private ByteBufQueue[] tinyCaches;

  private int runSize;
  private int log2RunSize;
  private int subPageSize;
  private int log2SubPageSize;
  private int quantumSize;
  private int tinySize;
  private int log2TinySize;

  public ArenaAllocCache(
      int chunkSize,
      int runSize,
      int largeCacheCount,
      int subPageSize,
      int subPageCacheCount,
      int quantumSize,
      int quantumCacheCount,
      int tinySize,
      int tinyCacheCount
  ) {
    int largeLevelCount = diffLog2(runSize, chunkSize);
    largeCaches = initByteBufCache(largeLevelCount, largeCacheCount);
    int subPageLevelCount = diffLog2(subPageSize, runSize);
    subPageCaches = initByteBufCache(subPageLevelCount, subPageCacheCount);
    int quantumLevelCount = diffMultiply2(quantumSize, subPageSize);
    quantumCaches = initByteBufCache(quantumLevelCount, quantumCacheCount);
    int tinyLevelCount = diffLog2(tinySize, quantumSize);
    tinyCaches = initByteBufCache(tinyLevelCount, tinyCacheCount);
    this.runSize = runSize;
    this.log2RunSize = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(runSize);
    this.subPageSize = subPageSize;
    this.log2SubPageSize = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(subPageSize);
    this.quantumSize = quantumSize;
    this.tinySize = tinySize;
    this.log2TinySize = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(tinySize);
  }

  private ByteBufQueue[] initByteBufCache(int levelCount, int cacheCount) {
    ByteBufQueue[] queues = new ByteBufQueue[levelCount];
    for (int i = 0; i < levelCount; i++) {
      queues[i] = new ByteBufQueue(cacheCount);
    }
    return queues;
  }

  private int diffMultiply2(int quantumSize, int subPageSize) {
    int diff = 0;
    while (quantumSize < subPageSize) {
      diff++;
      quantumSize = quantumSize * diff;
    }
    return diff - 1;
  }

  private int diffLog2(int startSize, int targetSize) {
    int diff = 0;
    while (startSize < targetSize) {
      diff += 1;
      startSize = startSize << 1;
    }
    return diff;
  }

  public boolean cache(PooledByteBuf byteBuf, int capacity) {
    if(1 > 0){
      return false;
    }
    if (capacity >= runSize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2RunSize;
      return largeCaches[index].offer(byteBuf);
    }
    if (capacity >= subPageSize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2SubPageSize;
      return subPageCaches[index].offer(byteBuf);
    }
    if (capacity >= quantumSize) {
      int index = capacity / quantumSize - 1;
      return quantumCaches[index].offer(byteBuf);
    }
    if (capacity >= tinySize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2SubPageSize;
      return tinyCaches[index].offer(byteBuf);
    }
    throw new IllegalArgumentException("invalid capacity");
  }

  public PooledByteBuf alloc(int capacity) {
    if (capacity >= runSize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2RunSize;
      return largeCaches[index].poll();
    }
    if (capacity >= subPageSize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2SubPageSize;
      return subPageCaches[index].poll();
    }
    if (capacity >= quantumSize) {
      int index = capacity / quantumSize - 1;
      return subPageCaches[index].poll();
    }
    if (capacity >= tinySize) {
      int log2Cap = Integer.SIZE - Integer.numberOfLeadingZeros(capacity) - 1;
      int index = log2Cap - log2TinySize;
      return tinyCaches[index].poll();
    }
    throw new IllegalArgumentException("invalid capacity");
  }
}
