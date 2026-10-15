package net.mizukilab.pit.util;
import net.mizukilab.pit.item.IMythicItem;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ItemCacheConcurrencyTest {
    @Test public void recentlyReadItemsSurviveEvictionAndShrinkingLimitIsAppliedWithoutNewWrites() {
        AtomicLong limit=new AtomicLong(2); ItemGlobalReference cache=new ItemGlobalReference(limit::get);
        IMythicItem first=mock(IMythicItem.class), second=mock(IMythicItem.class), third=mock(IMythicItem.class);
        cache.putValue(1,first); cache.putValue(2,second); assertSame(first,cache.getValue(1));
        cache.putValue(3,third); cache.executeLRU();
        assertSame(first,cache.getValue(1)); assertNull(cache.getValue(2)); assertSame(third,cache.getValue(3));
        limit.set(0); cache.executeLRU(); assertEquals(0,cache.size());
    }
    @Test public void collidingUuidCannotRemoveAnotherItemsCacheEntry() {
        ItemGlobalReference cache=new ItemGlobalReference(() -> 8); UUID a=new UUID(0,101), b=new UUID(101,0);
        IMythicItem item=mock(IMythicItem.class); item.uuid=b; cache.putValue(b,item);
        assertNull(cache.remove(a)); assertSame(item,cache.getValue(b));
        assertSame(item,cache.remove(b)); assertEquals(0,cache.size());
    }
    @Test(timeout=15000) public void parallelReadersWritersAndEvictionLeaveAUsableCache() throws Exception {
        ItemGlobalReference cache=new ItemGlobalReference(() -> 64); IMythicItem item=mock(IMythicItem.class);
        ExecutorService workers=Executors.newFixedThreadPool(4); CountDownLatch start=new CountDownLatch(1);
        List<Future<?>> jobs=new ArrayList<>();
        try {
            for (int worker=0;worker<4;worker++) {
                final int id=worker;
                jobs.add(workers.submit(() -> {
                    start.await();
                    for (int i=0;i<15000;i++) {
                        long key=(i*17L+id)%256;
                        if (id==0) cache.putValue(key,item);
                        else if (id==1) { cache.getValue(key); cache.get(key); }
                        else if (id==2) cache.executeLRU();
                        else { cache.remove(key); if (i%400==0) cache.clear(); }
                    }
                    return null;
                }));
            }
            start.countDown(); for(Future<?> job:jobs) job.get(10,TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        cache.clear(); for(int i=0;i<100;i++)cache.putValue(i,item); cache.executeLRU();
        assertEquals(64,cache.size());
        for(int i=36;i<100;i++) assertSame(item,cache.getValue(i));
    }
}
