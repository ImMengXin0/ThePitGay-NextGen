package cn.mythicland.framework.bukkit;

import cn.mythicland.framework.component.ComponentRole;
import org.bukkit.event.Listener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ComponentRole(contract = Listener.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ListenerComponent {
}
