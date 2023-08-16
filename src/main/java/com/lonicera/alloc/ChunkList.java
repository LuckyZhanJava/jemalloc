package com.lonicera.alloc;


import java.util.HashMap;
import java.util.Map;

public class ChunkList {

  private static class ChunkNode {

    private ChunkNode pre;
    private Chunk chunk;
    private ChunkNode next;

    public ChunkNode(ChunkNode pre, Chunk chunk, ChunkNode next) {
      this.pre = pre;
      this.chunk = chunk;
      this.next = next;
    }
  }

  private String name;
  private final ChunkNode HEAD = new ChunkNode(null, null, null);
  private final ChunkNode chunkQueueHead;
  private ChunkNode chunkQueueTail;
  private Map<Chunk, ChunkNode> chunkNodeMap;
  private ChunkList pre;
  private ChunkList next;
  private int minUsage;
  private int maxUsage;

  public ChunkList(String name, ChunkList pre, ChunkList next, int minUsage, int maxUsage) {
    this.name = name;
    chunkQueueHead = HEAD;
    chunkQueueTail = null;
    chunkNodeMap = new HashMap<>();
    this.pre = pre;
    this.next = next;
    this.minUsage = minUsage;
    this.maxUsage = maxUsage;
  }

  public int maxUsage() {
    return maxUsage;
  }

  public int minUsage() {
    return minUsage;
  }

  public ChunkList next() {
    return next;
  }

  public void next(ChunkList next) {
    this.next = next;
  }

  public ChunkList pre() {
    return pre;
  }

  public void add(Chunk chunk) {
    chunk.chunkList(this);
    ChunkNode last = chunkQueueTail;
    if (last == null) {
      ChunkNode chunkNode = new ChunkNode(chunkQueueHead, chunk, null);
      chunkQueueHead.next = chunkNode;
      chunkQueueTail = chunkNode;
      chunkNodeMap.put(chunk, chunkNode);
    } else {
      ChunkNode chunkNode = new ChunkNode(last, chunk, null);
      last.next = chunkNode;
      chunkQueueTail = chunkNode;
      chunkNodeMap.put(chunk, chunkNode);
    }
  }


  public void moveUp(Chunk chunk) {
    if(chunk.usage() <= maxUsage()){
      return;
    }
    remove(chunk);
    ChunkList next = next();
    while (next != null) {
      if (chunk.usage() <= next.maxUsage()) {
        next.add(chunk);
        return;
      } else {
        next = next.next();
      }
    }
  }

  public void moveDown(Chunk chunk) {
    if(chunk.usage() >= minUsage()){
      return;
    }
    remove(chunk);
    ChunkList pre = pre();
    while (pre != null) {
      if (chunk.usage() >= pre.minUsage()) {
        pre.add(chunk);
        return;
      } else {
        pre = pre.pre();
      }
    }
  }

  public void remove(Chunk chunk) {
    ChunkNode chunkNode = chunkNodeMap.remove(chunk);
    if (chunkNode == null) {
      throw new IllegalArgumentException("Not In List Chunck");
    }
    ChunkNode pre = chunkNode.pre;
    ChunkNode next = chunkNode.next;
    if (pre == chunkQueueHead && next == null) {
      chunkQueueHead.next = next;
      chunkQueueTail = next;
      return;
    }
    if (pre == chunkQueueHead && next != null) {
      next.pre = chunkQueueHead;
      chunkQueueHead.next = next;
      return;
    }
    if (pre != chunkQueueHead && next == null) {
      pre.next = null;
      chunkQueueTail = pre;
      return;
    }
    if (pre != chunkQueueHead && next != null) {
      pre.next = next;
      next.pre = pre;
    }
  }

  public boolean alloc(PooledByteBuf byteBuf, int capacity) {
    ChunkNode next = HEAD.next;
    while (next != null) {
      if (next.chunk.alloc(byteBuf, capacity)) {
        return true;
      }
      next = next.next;
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    ChunkNode next = chunkQueueHead.next;
    while (next != null) {
      sb.append(next.chunk);
      next = next.next;
      if(next != null){
        sb.append(" -> ");
      }
    }
    return String.format("%5s : %s", name, sb.toString());
  }
}
