package com.lonicera;

import com.lonicera.alloc.ByteBuf;
import com.lonicera.alloc.MemoryAllocator;
import io.netty.buffer.ByteBufAllocator;
import java.nio.ByteBuffer;

/**
 * Hello world!
 */
public class App {

  public static void main(String[] args) {

    ByteBufAllocator alloc = ByteBufAllocator.DEFAULT;

    alloc.buffer(4096);

    MemoryAllocator allocator = new MemoryAllocator();
    allocator.alloc(1);

    long start = System.nanoTime();
    for (int i = 0; i < 2048; i++) {
      //ByteBuf byteBuf = allocator.alloc(4);
      ByteBuf byteBuf = allocator.alloc(40960);
      byteBuf.release();
      //ByteBuffer byteBuf = ByteBuffer.allocateDirect(40960);
      //byteBuf.writeByte((byte) 1);
    }
    long end = System.nanoTime();
    System.out.print(end - start);
  }
}
