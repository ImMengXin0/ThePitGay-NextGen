package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.events.trigger.type.IEpicEvent;
import cn.charlotte.pit.events.trigger.type.INormalEvent;
import cn.charlotte.pit.events.trigger.type.addon.IPreparative;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.SneakyThrows;
import net.mizukilab.pit.config.PitGlobalConfig;
import net.mizukilab.pit.util.bossbar.BossBar;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.cooldown.Cooldown;
import net.mizukilab.pit.util.time.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.bukkit.Sound.BURP;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/9 12:59
 */
public class EventFactory {

    private final List<INormalEvent> normalEvents;
    private final List<IEpicEvent> epicEvents;
    private INormalEvent activeNormalEvent;
    private IEpicEvent activeEpicEvent;
    private Cooldown normalEnd;
    private long lastNormalEvent;

    private IEpicEvent nextEpicEvent;
    private Cooldown nextEpicEventTimer;
    private Cooldown epicEnd = new Cooldown(0);
    public Cooldown getEpicEnd() { return epicEnd; }

    EventTimer eventTimer;
    private org.bukkit.scheduler.BukkitTask timerTask, normalBarTask, preparationTask;
    private boolean closing;

    private boolean defer(Runnable action) {
        if (Bukkit.isPrimaryThread()) return false;
        if (!closing) Bukkit.getScheduler().runTask(ThePit.getInstance(), action);
        return true;
    }

    private void clearBar() {
        if (ThePit.getInstance().getBossBar() != null && ThePit.getInstance().getBossBar().getBossBar() != null)
            ThePit.getInstance().getBossBar().getBossBar().setTitle("");
    }

    private boolean valid(AbstractEvent event) {
        try { EventLocations.validate(event); return true; }
        catch (RuntimeException failure) {
            ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "Activity cannot start: " + event.getEventInternalName(), failure);
            return false;
        }
    }

    public void abort(AbstractEvent event, Throwable failure) {
        event.markAborting();
        ThePit.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                "Activity failed: " + event.getEventInternalName(), failure);
        if (event instanceof INormalEvent normal) inactiveEvent(normal);
        if (event instanceof IEpicEvent epic) inactiveEvent(epic);
    }

    public void shutdown() {
        closing = true;
        if (timerTask != null) timerTask.cancel();
        if (preparationTask != null) preparationTask.cancel();
        if (normalBarTask != null) normalBarTask.cancel();
        nextEpicEvent = null;
        nextEpicEventTimer = null;
        if (activeNormalEvent != null) {
            ((AbstractEvent) activeNormalEvent).markAborting();
            inactiveEvent(activeNormalEvent);
        }
        if (activeEpicEvent != null) {
            ((AbstractEvent) activeEpicEvent).markAborting();
            inactiveEvent(activeEpicEvent);
        }
    }

    public EventFactory() {
        this.normalEvents = new ObjectArrayList<>();
        this.epicEvents = new ObjectArrayList<>();
        this.normalEnd = new Cooldown(0);
    }
    public EventTimer getEventTimer(){
        return eventTimer;
    }

    public void pushEvent(IEpicEvent event) {
        pushEvent(event, false);
    }

    public void pushEvent(IEpicEvent event, boolean force) {
        if (defer(() -> pushEvent(event, force)) || closing) return;
        if (force || Bukkit.getOnlinePlayers().size() >= ((AbstractEvent) event).requireOnline()) {
            readyEpicEvent(event);
        }
    }

    public void pushEvent(INormalEvent event) {
        pushEvent(event, false);
    }

    public void pushEvent(INormalEvent event, boolean force) {
        if (defer(() -> pushEvent(event, force)) || closing) return;
        if (force || Bukkit.getOnlinePlayers().size() >= ((AbstractEvent) event).requireOnline()) {
            activeEvent(event);
        }
    }

    @SneakyThrows
    public void init(List<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            if (IEpicEvent.class.isAssignableFrom(clazz)) {
                try {
                    Constructor<?> declaredConstructor = clazz.getDeclaredConstructor();
                    declaredConstructor.setAccessible(true);
                    this.epicEvents.add((IEpicEvent) declaredConstructor.newInstance());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            if (INormalEvent.class.isAssignableFrom(clazz)) {
                try {
                    Constructor<?> declaredConstructor = clazz.getDeclaredConstructor();
                    declaredConstructor.setAccessible(true);
                    this.normalEvents.add((INormalEvent) declaredConstructor.newInstance());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        if (timerTask != null) timerTask.cancel();
        timerTask = Bukkit.getScheduler()
                .runTaskTimer(ThePit.getInstance(), eventTimer = new EventTimer(), 20, 20);

    }

    public String getActiveEpicEventName() {
        if (activeEpicEvent == null) {
            return null;
        }
        return ((AbstractEvent) activeEpicEvent).getEventInternalName();
    }

    public String getActiveNormalEventName() {
        if (activeNormalEvent == null) {
            return null;
        }
        return ((AbstractEvent) activeNormalEvent).getEventInternalName();
    }

    public void activeEvent(INormalEvent event) {
        if (defer(() -> activeEvent(event)) || closing || event == null) return;
        if (activeNormalEvent != null || activeEpicEvent != null || nextEpicEvent != null) return;
        AbstractEvent iEvent = (AbstractEvent) event;
        this.lastNormalEvent = System.currentTimeMillis();
        this.activeNormalEvent = event;
        this.normalEnd = new Cooldown(5, TimeUnit.MINUTES);
        iEvent.beginLifecycle();
        try { iEvent.onActive(); } catch (Throwable failure) { abort(iEvent, failure); return; }
        // One-shot events and invalid configurations can stop themselves in onActive.
        if (activeNormalEvent != event) return;

        normalBarTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!event.equals(activeNormalEvent)) {
                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setTitle("");
                    cancel();
                    return;
                }

                if (normalEnd.hasExpired()) {
                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setTitle("");
                    cancel();
                    return;
                }

                if (tick >= 15) {
                    tick = 0;
                }
                String start;
                if (tick % 2 == 0) {
                    start = "&a&l小型事件! ";
                } else {
                    start = "&2&l小型事件! ";
                }

                final String title = CC.translate(start + "&6&l" + iEvent.getEventName() + " &7将在 &e" + TimeUtil.millisToTimer(normalEnd.getRemaining()) + " &7后结束!");

                BossBar bossBar = ThePit.getInstance()
                        .getBossBar()
                        .getBossBar();
                bossBar
                        .setTitle(title);
                bossBar.setProgress(normalEnd.getRemaining() / (5 * 1000 * 60f));

                tick++;
            }
        }.runTaskTimer(ThePit.getInstance(), 10, 10);
    }

    public void inactiveEvent(INormalEvent event) {
        if (event == null || defer(() -> inactiveEvent(event)) || activeNormalEvent != event) return;
        this.activeNormalEvent = null;
        if (normalBarTask != null) { normalBarTask.cancel(); normalBarTask = null; }
        AbstractEvent iEvent = (AbstractEvent) event;
        iEvent.endLifecycle();
        try { iEvent.onInactive(); }
        catch (Throwable failure) { ThePit.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Activity cleanup failed", failure); }
        finally { clearBar(); }
    }

    public void safeInactiveEvent(INormalEvent event) {
        if (defer(() -> safeInactiveEvent(event))) return;
        inactiveEvent(event);
        if (eventTimer != null) eventTimer.getCooldown().fastExpired();
    }

    public void cooldown() {
        if (eventTimer == null) return;
        eventTimer.setCooldown(new Cooldown(1, TimeUnit.MINUTES));
    }

    public void readyEpicEvent(IEpicEvent event) {
        if (defer(() -> readyEpicEvent(event)) || closing || event == null) return;
        if (activeEpicEvent != null || nextEpicEvent != null) return;
        if (!valid((AbstractEvent) event)) return;
        if (activeNormalEvent != null) inactiveEvent(activeNormalEvent);
        this.nextEpicEvent = event;
        this.nextEpicEventTimer = new Cooldown(5, TimeUnit.MINUTES);
        AbstractEvent iEvent = (AbstractEvent) event;

        if (event instanceof IPreparative) {
            try { ((IPreparative) event).onPreActive(); }
            catch (Throwable failure) {
                nextEpicEvent = null;
                nextEpicEventTimer = null;
                abort(iEvent, failure);
                return;
            }
        }
        PitGlobalConfig pitWorldConfig = ThePit.getInstance().getGlobalConfig();
        List<String> animationForEpicEvent = pitWorldConfig.animationForEpicEvent == null
                ? java.util.Collections.emptyList() : pitWorldConfig.animationForEpicEvent;
        int periodForEpicEvent = pitWorldConfig.periodForEpicEvent;
        preparationTask = new BukkitRunnable() {
            Iterator<String> iterator = animationForEpicEvent.iterator();
            @Override
            public void run() {
                if (nextEpicEvent != event || closing) { cancel(); return; }
                if (nextEpicEventTimer.hasExpired()) {
                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setTitle("");
                    activeEvent(event);
                    cancel();
                    return;
                }
                if(!iterator.hasNext()){
                    iterator = animationForEpicEvent.iterator();
                }
                String start = "Epic Event Bossbar";
                if(iterator.hasNext()) {
                     start = iterator.next(); //check
                }
                final String title = CC.translate(
                        start + "&6&l" + iEvent.getEventName() + " &7将在 &e" + TimeUtil.millisToTimer(nextEpicEventTimer.getRemaining()) + " &7后开始!");

                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setTitle(title);
                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setProgress(nextEpicEventTimer.getRemaining() / (5 * 1000 * 60f));
            }
        }.runTaskTimer(ThePit.getInstance(), 10, Math.max(1, periodForEpicEvent));
    }

    public void activeEvent(IEpicEvent event) {
        if (defer(() -> activeEvent(event)) || closing || event == null) return;
        if (activeEpicEvent != null || (nextEpicEvent != null && nextEpicEvent != event)) return;
        if (!valid((AbstractEvent) event)) {
            if (preparationTask != null) preparationTask.cancel();
            nextEpicEvent = null;
            nextEpicEventTimer = null;
            clearBar();
            return;
        }
        if (activeNormalEvent != null) inactiveEvent(activeNormalEvent);
        if (preparationTask != null) { preparationTask.cancel(); preparationTask = null; }
        this.nextEpicEvent = null;
        this.nextEpicEventTimer = null;
        this.activeEpicEvent = event;
        this.epicEnd = new Cooldown(5, TimeUnit.MINUTES);
        AbstractEvent iEvent = (AbstractEvent) event;
        iEvent.beginLifecycle();
        try { iEvent.onActive(); } catch (Throwable failure) { abort(iEvent, failure); }
    }

    public void inactiveEvent(IEpicEvent event) {
        if (event == null || defer(() -> inactiveEvent(event)) || activeEpicEvent != event) return;
        this.activeEpicEvent = null;
        AbstractEvent iEvent = (AbstractEvent) event;
        iEvent.endLifecycle();
        try { iEvent.onInactive(); }
        catch (Throwable failure) { ThePit.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Activity cleanup failed", failure); }
        finally { clearBar(); }
        if (!closing) Bukkit.getOnlinePlayers().forEach(player -> player.playSound(player.getLocation(), BURP, 1, 0.72F));
    }

    public List<INormalEvent> getNormalEvents() {
        return normalEvents;
    }

    public List<IEpicEvent> getEpicEvents() {
        return epicEvents;
    }

    public INormalEvent getActiveNormalEvent() {
        return activeNormalEvent;
    }

    public IEpicEvent getActiveEpicEvent() {
        return activeEpicEvent;
    }

    public Cooldown getNormalEnd() {
        return normalEnd;
    }

    public void setNormalEnd(Cooldown cooldown) {
        this.normalEnd = cooldown;
    }

    public long getLastNormalEvent() {
        return lastNormalEvent;
    }

    public IEpicEvent getNextEpicEvent() {
        return nextEpicEvent;
    }

    public Cooldown getNextEpicEventTimer() {
        return nextEpicEventTimer;
    }
}
