package net.mizukilab.pit.util.chat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import java.util.ArrayList;
import java.util.List;

/** Legacy fluent facade over public Bungee components. */
public class ChatComponentBuilder {
    private final List<BaseComponent> components = new ArrayList<>();

    public ChatComponentBuilder(String text) { parse(text); }
    public TextComponent getCurrent() {
        if (components.isEmpty()) components.add(new TextComponent(""));
        BaseComponent current = components.get(components.size() - 1);
        if (current instanceof TextComponent text) return text;
        TextComponent wrapper = new TextComponent(current);
        components.set(components.size() - 1, wrapper);
        return wrapper;
    }
    public void setCurrent(TextComponent component) {
        getCurrent(); components.set(components.size() - 1, component);
    }
    public List<BaseComponent> getParts() {
        getCurrent(); return components.subList(0, components.size() - 1);
    }
    public ChatComponentBuilder setCurrentHoverEvent(HoverEvent event) { getCurrent().setHoverEvent(event); return this; }
    public ChatComponentBuilder setCurrentClickEvent(ClickEvent event) { getCurrent().setClickEvent(event); return this; }
    public ChatComponentBuilder attachToEachPart(HoverEvent event) {
        components.forEach(part -> { if (part.getHoverEvent() == null) part.setHoverEvent(event); }); return this;
    }
    public ChatComponentBuilder attachToEachPart(ClickEvent event) {
        components.forEach(part -> { if (part.getClickEvent() == null) part.setClickEvent(event); }); return this;
    }
    public ChatComponentBuilder parse(String text) { return append(TextComponent.fromLegacyText(CC.translate(text))); }
    public ChatComponentBuilder append(String text) { return parse(text); }
    public ChatComponentBuilder append(BaseComponent[] parts) {
        for (BaseComponent part : parts) append(part); return this;
    }
    public ChatComponentBuilder append(BaseComponent part) {
        if (part != null) components.add(part.duplicate()); return this;
    }
    public ChatComponentBuilder append(TextComponent part) { return append((BaseComponent) part); }
    public ChatComponentBuilder color(ChatColor value) { getCurrent().setColor(value); return this; }
    public ChatComponentBuilder bold(boolean value) { getCurrent().setBold(value); return this; }
    public ChatComponentBuilder italic(boolean value) { getCurrent().setItalic(value); return this; }
    public ChatComponentBuilder underlined(boolean value) { getCurrent().setUnderlined(value); return this; }
    public ChatComponentBuilder strikethrough(boolean value) { getCurrent().setStrikethrough(value); return this; }
    public ChatComponentBuilder obfuscated(boolean value) { getCurrent().setObfuscated(value); return this; }
    public ChatComponentBuilder event(HoverEvent event) { return setCurrentHoverEvent(event); }
    public ChatComponentBuilder event(ClickEvent event) { return setCurrentClickEvent(event); }
    public BaseComponent[] create() { return components.toArray(BaseComponent[]::new); }
}
