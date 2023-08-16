package com.lonicera.alloc;


public class MemoryAllocator implements Allocator {

  private int arenaCount;

  private final ThreadLocal<Arena> arenaThreadLocal;


  public MemoryAllocator(int arenaCount) {
    if (arenaCount < 1) {
      throw new IllegalArgumentException("arena count require > 0");
    }
    this.arenaCount = arenaCount;
    this.arenaThreadLocal = arenaThreadLocal(arenaCount);
  }

  private ThreadLocal<Arena> arenaThreadLocal(final int arenaCount) {
    return new ThreadLocal<Arena>() {
      private int index = 0;
      private Arena[] arenas = initAreas(arenaCount);

      private Arena[] initAreas(int arenaCount){
        Arena[] arenas = new Arena[arenaCount];
        for(int i = 0; i < arenas.length; i++){
          arenas[i] = new Arena();
        }
        return arenas;
      }

      @Override
      protected Arena initialValue() {
        synchronized (this) {
          if (index > arenaCount) {
            index = index % arenaCount;
          }
          Arena arena = arenas[index];
          index++;
          return arena;
        }
      }
    };
  }

  public MemoryAllocator() {
    this(Runtime.getRuntime().availableProcessors());
  }

  @Override
  public ByteBuf alloc(int capacity) {
    if(capacity < 1){
      throw new IllegalArgumentException("capacity require > 0");
    }
    Arena arena = arenaThreadLocal.get();
    ByteBuf byteBuf = arena.alloc(capacity);
    return byteBuf;
  }

}
