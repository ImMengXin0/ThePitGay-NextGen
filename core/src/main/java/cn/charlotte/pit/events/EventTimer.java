package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.events.trigger.type.IEpicEvent;
import cn.charlotte.pit.events.trigger.type.INormalEvent;
import lombok.Getter;
import lombok.Setter;
import net.mizukilab.pit.util.cooldown.Cooldown;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/9 13:14
 */
@Getter
public class EventTimer implements Runnable {

    @Setter
    private Cooldown cooldown = new Cooldown(0L);

    private LocalDateTime lastMajorSlot;

    @Override
    public void run() {
        EventFactory factory = ThePit.getInstance().getEventFactory();
        if (factory.getActiveEpicEvent() != null && factory.getEpicEnd().hasExpired()) {
            factory.inactiveEvent(factory.getActiveEpicEvent());
            cooldown = new Cooldown(3, TimeUnit.MINUTES);
        }
        // Expiry is independent of the interval between attempts to start events.
        if (factory.getActiveNormalEvent() != null && factory.getNormalEnd().hasExpired()) {
            factory.inactiveEvent(factory.getActiveNormalEvent());
            cooldown = new Cooldown(3, TimeUnit.MINUTES);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slot = now.withSecond(0).withNano(0);
        if (now.getMinute() == 50 && !slot.equals(lastMajorSlot)
                && factory.getActiveEpicEvent() == null && factory.getNextEpicEvent() == null) {
            lastMajorSlot = slot;
            String major = EventsHandler.INSTANCE.nextEvent(true);
            factory.getEpicEvents().stream().filter(event ->
                    ((AbstractEvent) event).getEventInternalName().equals(major)).findFirst()
                    .ifPresent(factory::pushEvent);
            cooldown = new Cooldown(3, TimeUnit.MINUTES);
            return;
        }
        if (!cooldown.hasExpired() || factory.getActiveEpicEvent() != null
                || factory.getNextEpicEvent() != null || factory.getActiveNormalEvent() != null) return;
        if (now.getMinute() == 50 || now.getMinute() == 55) return;
        cooldown = new Cooldown(1, TimeUnit.MINUTES);
        String minor = EventsHandler.INSTANCE.nextEvent(false);
        factory.getNormalEvents().stream().filter(event ->
                ((AbstractEvent) event).getEventInternalName().equals(minor)).findFirst()
                .ifPresent(factory::pushEvent);
    }
}
