package net.mizukilab.pit.data.operator;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import nya.Skip;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Skip
public class SuPromise<E> extends ObjectArraySet<Consumer<E>> {
    private E e;
    private volatile boolean done = false;
    public void ret(E e) {
        List<Consumer<E>> callbacks;
        synchronized (this) {
            if (done) return;
            this.e = e;
            done = true;
            callbacks = new ArrayList<>(this);
            clear();
            this.notifyAll();
        }
        callbacks.forEach(callback -> invoke(callback, e));
    }

    public SuPromise<E> promise(Consumer<E> run) {
        Objects.requireNonNull(run, "callback");
        E result;
        synchronized (this) {
            if (!done) {
                this.add(run);
                return this;
            }
            result = e;
        }
        invoke(run, result);
        return this;
    }

    private void invoke(Consumer<E> callback, E result) {
        try {
            callback.accept(result);
        } catch (Throwable failure) {
            Logger.getLogger(SuPromise.class.getName()).log(Level.SEVERE, "Promise callback failed", failure);
        }
    }

    public SuPromise<E> join() {
        try {
            synchronized (this) {
                while (!done) {
                    this.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    public boolean isDone() {
        return done;
    }
}
