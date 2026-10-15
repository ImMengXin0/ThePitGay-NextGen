package net.mizukilab.pit.data.operator;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SuPromiseTest {
    @Test(timeout = 15000)
    public void concurrentRegistrationNeverLosesOrDuplicatesCallback() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 1000; i++) {
                SuPromise<Integer> promise = new SuPromise<>();
                AtomicInteger calls = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                Future<?> register = pool.submit(() -> {
                    await(start);
                    promise.promise(value -> calls.addAndGet(value));
                });
                Future<?> complete = pool.submit(() -> { await(start); promise.ret(1); });
                start.countDown();
                register.get(2, TimeUnit.SECONDS);
                complete.get(2, TimeUnit.SECONDS);
                assertEquals(1, calls.get());
            }
        } finally { pool.shutdownNow(); }
    }

    @Test
    public void completionIsIdempotentAndLateSubscriberGetsFirstResult() {
        SuPromise<Integer> promise = new SuPromise<>();
        AtomicInteger value = new AtomicInteger();
        promise.ret(7);
        promise.ret(99);
        promise.promise(value::set);
        assertTrue(promise.isDone());
        assertEquals(7, value.get());
    }

    @Test
    public void failingCallbackDoesNotSkipOtherCallbacks() {
        SuPromise<Integer> promise = new SuPromise<>();
        AtomicInteger value = new AtomicInteger();
        promise.promise(v -> { throw new IllegalStateException("expected test failure"); });
        promise.promise(value::set);
        promise.ret(4);
        assertEquals(4, value.get());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
}
