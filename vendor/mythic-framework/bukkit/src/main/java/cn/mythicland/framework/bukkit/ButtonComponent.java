package cn.mythicland.framework.bukkit;

import cn.mythicland.framework.component.ComponentRole;
import cn.mythicland.framework.menu.AbstractButton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ComponentRole(contract = AbstractButton.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ButtonComponent {
}
