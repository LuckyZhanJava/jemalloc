package com.lonicera.alloc;

public class SmallAllocNode {

  private SmallAllocNode head;
  private PooledChunk chunk;
  private int runId;
  private int allocIndex;
  private ArenaAllocCache allocCache;
  private int runOffset;
  private long[] bitMaps;
  private int allocSize;
  private SmallAllocNode pre;
  private SmallAllocNode next;
  private int firstFreeBit = 0;
  private final int totalCount;
  private int freeCount;

  public SmallAllocNode(
      SmallAllocNode head,
      PooledChunk chunk,
      int runId,
      int runSize,
      int allocIndex,
      ArenaAllocCache allocCache,
      int runOffset,
      int allocSize
  ) {
    this.head = head;
    this.chunk = chunk;
    this.runId = runId;
    this.allocIndex = allocIndex;
    this.allocCache = allocCache;
    this.runOffset = runOffset;
    this.allocSize = allocSize;
    totalCount = runSize / allocSize;
    int longCount = totalCount % 64 == 0 ? totalCount / 64 : totalCount / 64 + 1;
    bitMaps = new long[longCount];
    freeCount = totalCount;
  }

  public boolean alloc(PooledByteBuf byteBuf, int alignCapacity) {
    if (freeCount == 0) {
      return false;
    }
    freeCount -= 1;
    if(freeCount == 0){
      moveToEnd();
    }
    int offset = runOffset + firstFreeBit * allocSize;
    long handle = ((long) allocIndex << Integer.SIZE) | firstFreeBit;
    byteBuf.initByteBuf(chunk, offset, allocSize, handle, allocCache);

    int bitMapIndex = firstFreeBit / Long.SIZE;
    int bitOffset = firstFreeBit % Long.SIZE;
    long bitmap = bitMaps[bitMapIndex];
    long marker = (1L << (Long.SIZE - 1 - bitOffset));
    bitmap = bitmap | marker;
    bitMaps[bitMapIndex] = bitmap;

    firstFreeBit = nextBit0(bitMaps, firstFreeBit + 1);

    return true;
  }

  private void moveToEnd() {
    if(head.pre == this){
      return;
    }
    SmallAllocNode pre = pre();
    SmallAllocNode next = next();

    pre.next(next);
    next.pre(pre);

    SmallAllocNode tail = head.pre;
    tail.next(this);
    this.pre(tail);

    head.pre = this;
  }

  private int nextBit0(long[] bitmaps, int bitOffset) {
    int bitmapIndex = bitOffset / Long.SIZE;
    int offset = bitOffset % Long.SIZE;
    for(int i = bitmapIndex; i < bitmaps.length; i++){
      long bitmap = bitmaps[bitmapIndex];
      long marker = 1L << (Long.SIZE - 1 - offset);
      while (offset < Long.SIZE){
        if((marker | bitmap) != bitmap){
          return bitOffset;
        }
        offset++;
        bitOffset++;
        marker = marker >> 1;
      }
      offset = 0;
    }
    return bitOffset;
  }

  public void free(int freeBitIndex) {
    if (freeBitIndex < firstFreeBit) {
      firstFreeBit = freeBitIndex;
    }
    int bitMapIndex = freeBitIndex / Long.SIZE;
    int bitOffset = freeBitIndex % Long.SIZE;
    long bitmap = bitMaps[bitMapIndex];
    long marker = ~(1L << (Long.SIZE - 1 - bitOffset));
    bitmap = bitmap & marker;
    bitMaps[bitMapIndex] = bitmap;
    freeCount += 1;
    if (freeCount == totalCount) {
      removeSelf();
      chunk.releaseRun(runId);
    } else {
      moveToHead();
    }
  }

  private void moveToHead() {
    if(head.next == this){
      return;
    }
    removeSelf();
    SmallAllocNode next = head.next;
    this.next = next;
    next.pre(this);

    head.next = this;
    this.pre = head;
  }

  private void removeSelf() {
    this.pre.next = next;
    if(head.pre == this){
      head.pre = null;
    }
    if(next != null){
      this.next.pre = this.pre;
    }
  }

  public SmallAllocNode pre() {
    return pre;
  }

  public void pre(SmallAllocNode pre) {
    this.pre = pre;
  }

  public SmallAllocNode next() {
    return next;
  }

  public void next(SmallAllocNode next) {
    this.next = next;
  }

}
