package com.lonicera.alloc;

import java.nio.ByteBuffer;
import java.util.Objects;

class PooledChunk implements Chunk {

  private final int totalSize;
  private Arena arena;
  private int allocatedSize;
  private int runSize;
  private byte[] binaryTree;
  private byte[] runCaps;
  private int[] runOffsets;
  private SmallAllocNode[] smallAllocNodes;
  private final int depth;
  private ChunkList chunkList;
  private ByteBuffer memory;
  private int log2Chunk;
  private int log2Run;
  private int runCount;
  private ArenaAllocCache allocCache;

  public PooledChunk(Arena arena, int chunkSize, int runSize, ChunkList chunkList,
      ArenaAllocCache allocCache) {
    this.arena = arena;
    this.chunkList = chunkList;
    this.totalSize = chunkSize;
    this.runSize = runSize;
    this.allocatedSize = 0;
    runCount = chunkSize / runSize;
    smallAllocNodes = new SmallAllocNode[runCount << 1];
    depth = log2(runCount) + 1;
    log2Chunk = log2(chunkSize);
    log2Run = log2(runSize);
    runCaps = new byte[runCount << 1];
    runOffsets = new int[runCount << 1];
    binaryTree = binaryTree(runCount, depth, log2Run, runCaps, runOffsets);
    memory = ByteBuffer.allocateDirect(chunkSize);
    this.allocCache = allocCache;
  }

  private int log2(int number) {
    return Integer.SIZE - Integer.numberOfLeadingZeros(number) - 1;
  }

  private byte[] binaryTree(int runCount, int depth, int log2Run, byte[] runCaps,
      int[] runOffsets) {
    byte[] bytes = new byte[runCount << 1];
    for (int iDepth = 0; iDepth < depth; iDepth++) {
      int childStart = 1 << iDepth;
      int childEnd = 1 << (iDepth + 1);
      for (int index = childStart; index < childEnd; index++) {
        bytes[index] = (byte) (depth - 1 - iDepth + log2Run);//代表节点容量
        runCaps[index] = bytes[index];
        int offset = ((index << (depth - 1 - iDepth)) - runCount) * runSize;
        runOffsets[index] = offset;
      }
    }
    return bytes;
  }

  @Override
  public ByteBuffer memory() {
    return memory;
  }

  @Override
  public int usage() {
    return (int) Math.ceil(((double) (allocatedSize * 100)) / totalSize);
  }

  public boolean alloc(PooledByteBuf byteBuf, int capacity) {
    if (capacity < runSize) {
      return allocSmall(byteBuf, capacity);
    } else {
      return allocLarge(byteBuf, capacity);
    }
  }

  private boolean allocSmall(PooledByteBuf byteBuf, int capacity) {
    int runId = allocRun(runSize);
    if (runId < 0) {
      return false;
    }
    SmallAllocNode head = arena.smallAllocHead(capacity);
    SmallAllocNode newAllocNode = new SmallAllocNode(
        head,
        this,
        runId,
        runSize,
        runId,
        allocCache,
        runOffsets[runId],
        capacity
    );
    smallAllocNodes[runId] = newAllocNode;
    SmallAllocNode next = head.next();
    if (next != null) {
      next.pre(newAllocNode);
    }
    newAllocNode.next(next);
    head.next(newAllocNode);
    if(head.pre() == null){
      head.pre(newAllocNode);
    }
    newAllocNode.pre(head);

    boolean success = newAllocNode.alloc(byteBuf, capacity);

    return success;
  }

  private boolean allocLarge(PooledByteBuf byteBuf, int capacity) {
    int runId = allocRun(capacity);
    if (runId < 0) {
      return false;
    }
    int offset = runOffsets[runId];
    long handle = (long) runId;
    byteBuf.initByteBuf(this, offset, capacity, handle, allocCache);
    return true;
  }

  private int allocRun(int capacity) {
    int log2Cap = log2(capacity);
    byte reqCap = (byte) (log2Cap);
    int reqDept = log2Chunk - log2Cap;

    if (binaryTree[1] < reqCap) {
      return -1;
    }

    int i = 2;
    int nodeDept = 1;
    int scanEnd = 4;
    while (i < scanEnd) {
      byte nodeCap = binaryTree[i];
      if (nodeCap == reqCap && nodeDept == reqDept) {
        int runId = i;
        binaryTree[i] = 0;
        int parent = i >> 1;
        int oneChild = i;
        int anotherChild = i ^ 1;
        while (parent > 0) {
          byte oneCap = binaryTree[oneChild];
          byte anotherCap = binaryTree[anotherChild];
          binaryTree[parent] = oneCap > anotherCap ? oneCap : anotherCap;
          oneChild = parent;
          anotherChild = oneChild ^ 1;
          parent = parent >> 1;
        }
        allocatedSize += capacity;
        moveUpIfNecessary();
        return runId;
      } else {
        if (nodeDept < reqDept && nodeCap >= reqCap) { // walk down
          i = i << 1;
          nodeDept++;
          scanEnd = i + 2;
        } else {
          i++; //walk ahead
        }
      }
    }
    return -1;
  }

  private void moveUpIfNecessary() {
    chunkList.moveUp(this);
  }

  private void moveDownIfNecessary() {
    chunkList.moveDown(this);
  }

  @Override
  public void release(long handle) {
    if (handle < Integer.MAX_VALUE) {
      releaseRun(handle);
    } else {
      int smallAllocIndex = (int) (handle >> Integer.SIZE);
      int bitIndex = (int) handle;
      smallAllocNodes[smallAllocIndex].free(bitIndex);
    }
  }

  public void releaseRun(long handle) {
    int runId = (int) handle;
    byte runCap = runCaps[runId];
    binaryTree[runId] = runCap;
    int parentIndex = runId >> 1;
    int anotherRunId = runId ^ 1;
    byte oneCap = runCap;
    byte anotherCap = binaryTree[anotherRunId];
    while (parentIndex > 0) {
      byte parentCap = oneCap == anotherCap ? (byte) (oneCap + 1) : max(runCap, anotherCap);
      binaryTree[parentIndex] = parentCap;
      oneCap = parentCap;
      anotherCap = binaryTree[parentIndex ^ 1];
      parentIndex = parentIndex >> 1;
    }
    allocatedSize -= (1 << runCap);
    moveDownIfNecessary();
  }

  private byte max(byte a, byte b) {
    if (a > b) {
      return a;
    } else {
      return b;
    }
  }

  @Override
  public void chunkList(ChunkList chunkList) {
    this.chunkList = chunkList;
  }

  public String toString() {
    return "Chunk@" + Objects.hashCode(this) + "[" + usage() + "%]";
  }

}
