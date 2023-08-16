package com.lonicera.alloc;

public interface Allocator {
  ByteBuf alloc(int capacity);
}
