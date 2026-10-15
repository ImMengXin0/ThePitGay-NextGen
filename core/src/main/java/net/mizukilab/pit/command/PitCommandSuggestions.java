package net.mizukilab.pit.command;

import cn.charlotte.pit.ThePit;
import dev.rollczi.litecommands.LiteCommandsBuilder;
import dev.rollczi.litecommands.argument.ArgumentKey;
import dev.rollczi.litecommands.argument.suggester.Suggester;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.quoted.QuotedStringArgumentResolver;
import dev.rollczi.litecommands.suggestion.Suggestion;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.item.MythicColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Argument suggestions use in-memory registries only; permission checks remain in LiteCommands. */
public final class PitCommandSuggestions {
    private final ThePit plugin;
    private EnchantmentFactor cachedFactor;
    private long cachedRevision = Long.MIN_VALUE;
    private List<Suggestion> enchantments = List.of();

    private PitCommandSuggestions(ThePit plugin) { this.plugin = plugin; }

    public static void register(LiteCommandsBuilder<CommandSender, ?, ?> builder, ThePit plugin) {
        PitCommandSuggestions suggestions = new PitCommandSuggestions(plugin);
        // Free text (names for renaming, reasons, CDKs, UUIDs) has no useful automatic value.
        builder.argumentSuggester(String.class, (inv, argument, context) -> SuggestionResult.empty());
        builder.argumentSuggester(String.class, ArgumentKey.of(QuotedStringArgumentResolver.KEY),
                (inv, argument, context) -> SuggestionResult.empty());
        builder.argumentSuggester(Player.class, (inv, argument, context) -> suggestions.players(inv, context));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.players"),
                (inv, argument, context) -> suggestions.players(inv, context));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.enchants"),
                (inv, argument, context) -> matching(suggestions.enchantments(), context));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.enchant-level"),
                (inv, argument, context) -> suggestions.enchantLevels(inv, context));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.items"), suggestions.dynamic(suggestions::items));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.give-items"), suggestions.dynamic(suggestions::giveItems));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.take-items"), suggestions.dynamic(() -> {
            List<String> names = new ArrayList<>(suggestions.items());
            names.addAll(List.of("exp", "coins", "mythic_sword", "mythic_bow", "mythic_leggings"));
            return names;
        }));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.map-name"), suggestions.dynamic(() ->
                plugin.getConfigManager().getPitConfigs().values().stream().map(config -> config.getWorldName()).toList()));
        builder.argumentSuggester(Integer.class, ArgumentKey.of("pit.map-id"), (inv, argument, context) ->
                strings(plugin.getConfigManager().getPitConfigs().keySet().stream().map(String::valueOf).toList(), context));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.sound"), suggestions.dynamic(() ->
                plugin.getSoundFactory().getSoundNames()));
        builder.argumentSuggester(String.class, ArgumentKey.of("pit.plugins"), suggestions.dynamic(() ->
                Arrays.stream(plugin.getServer().getPluginManager().getPlugins()).map(p -> p.getName()).toList()));
        fixed(builder, "pit.color", Arrays.stream(MythicColor.values()).map(Enum::name).toList());
        fixed(builder, "pit.menu", List.of("shop", "warehouse", "admin_enchant", "admin_item", "sewers", "heresy", "ender_chest"));
        fixed(builder, "pit.event", List.of("rspf", "rage", "rvb", "qm", "hunt", "package", "100g", "cake",
                "dragon_egg", "hamburger", "spire", "squads", "auction", "bh", "cancel"));
        fixed(builder, "pit.debug", List.of("infNpc", "toggle", "enchNpc", "toPublic", "toPrivate"));
        fixed(builder, "pit.corner", List.of("a", "b"));
        fixed(builder, "pit.stat", List.of("coin", "prestige", "renown", "streak", "abounty", "level", "bounty", "maxhealth"));
        fixed(builder, "pit.boolean", List.of("true", "false"));
        List<Suggestion> tiers = compile(List.of("0", "1", "2", "3"));
        builder.argumentSuggester(Integer.class, ArgumentKey.of("pit.tier"),
                (inv, argument, context) -> matching(tiers, context));
        builder.argumentSuggester(int.class, ArgumentKey.of("pit.tier"),
                (inv, argument, context) -> matching(tiers, context));
    }

    private static void fixed(LiteCommandsBuilder<CommandSender, ?, ?> builder, String key, Collection<String> values) {
        List<Suggestion> compiled = compile(values);
        builder.argumentSuggester(String.class, ArgumentKey.of(key),
                (inv, argument, context) -> matching(compiled, context));
    }

    private Suggester<CommandSender, String> dynamic(Supplier<Collection<String>> values) {
        return (inv, argument, context) -> strings(values.get(), context);
    }

    private SuggestionResult players(Invocation<CommandSender> invocation, SuggestionContext context) {
        SuggestionResult result = SuggestionResult.empty();
        CommandSender sender = invocation.sender();
        String prefix = context.getCurrent().multilevel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!matches(player.getName(), prefix)) continue;
            if (sender instanceof Player viewer && viewer != player && !viewer.canSee(player)) continue;
            result.add(Suggestion.of(player.getName()));
        }
        return result;
    }

    private List<Suggestion> enchantments() {
        EnchantmentFactor factor = plugin.getEnchantmentFactor();
        if (factor == null) return List.of();
        if (cachedFactor != factor || cachedRevision != factor.getRevision()) {
            enchantments = compile(factor.getEnchantmentMap().keySet());
            cachedFactor = factor;
            cachedRevision = factor.getRevision();
        }
        return enchantments;
    }

    private SuggestionResult enchantLevels(Invocation<CommandSender> invocation, SuggestionContext context) {
        List<String> args = invocation.arguments().asList();
        // Only attached to the level argument: the preceding token is the selected enchantment NBT name.
        if (args.size() < 2 || plugin.getEnchantmentFactor() == null) return SuggestionResult.empty();
        AbstractEnchantment enchantment = plugin.getEnchantmentFactor().getEnchantmentMap().get(args.get(args.size() - 2));
        if (enchantment == null) return SuggestionResult.empty();
        SuggestionResult result = SuggestionResult.empty();
        int maximum = Math.max(0, enchantment.getMaxEnchantLevel());
        // A malformed external maximum must not allocate an unbounded result on the server thread.
        for (int level = 0; level <= Math.min(maximum, 100); level++) {
            String candidate = Integer.toString(level);
            if (matches(candidate, context.getCurrent().multilevel())) result.add(Suggestion.of(candidate));
        }
        return result;
    }

    private Collection<String> items() {
        return plugin.getItemFactor() == null ? List.of() : plugin.getItemFactor().getItemMap().keySet();
    }

    private Collection<String> giveItems() {
        List<String> names = new ArrayList<>(items());
        names.addAll(List.of("exp", "coins", "funky_feather",
                "chunk_of_vile_item", "chunkofvile", "mythic_reel", "mythicbook", "cactus", "cherry", "eggs",
                "cake", "package", "sugar", "wheat", "yummy"));
        for (MythicColor color : MythicColor.values()) names.add("mythic_leggings:" + color.name().toLowerCase(Locale.ROOT));
        return names;
    }

    private static boolean matches(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static List<Suggestion> compile(Collection<String> values) {
        return values.stream().filter(value -> value != null && !value.isEmpty()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).map(Suggestion::of).toList();
    }

    private static SuggestionResult strings(Collection<String> values, SuggestionContext context) {
        SuggestionResult result = SuggestionResult.empty();
        String prefix = context.getCurrent().multilevel();
        for (String value : values) if (matches(value, prefix)) result.add(Suggestion.of(value));
        return result;
    }

    private static SuggestionResult matching(List<Suggestion> values, SuggestionContext context) {
        SuggestionResult result = SuggestionResult.empty();
        String prefix = context.getCurrent().multilevel();
        for (Suggestion value : values) if (matches(value.multilevel(), prefix)) result.add(value);
        return result;
    }
}
