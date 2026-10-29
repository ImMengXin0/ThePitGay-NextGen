package cn.mythicland.framework.bukkit.store;

import cn.mythicland.framework.bukkit.item.v1_8_R3.InventoryItemCodec;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.function.ToIntFunction;

public class InventoryItemSerializer {
    public static final int DECODE_CACHE_WEIGHT = 4 * 1024 * 1024;
    private final Cache<String, DecodedItems> decodedItems = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .weakKeys()
            .maximumWeight(DECODE_CACHE_WEIGHT)
            .weigher((String encoded, DecodedItems items) -> cacheWeight(encoded, items.weight()))
            .build();

    protected ItemStack[] copyItems(ItemStack[] items) {
        ItemStack[] snapshot = items.clone();
        for (int index = 0; index < snapshot.length; index++) {
            snapshot[index] = copyItem(snapshot[index]);
        }
        return snapshot;
    }

    public static ItemStack copyItem(ItemStack item) {
        if (item == null) return null;
        if (item.getClass() != ItemStack.class || item.getType() == Material.AIR || !item.hasItemMeta())
            return item.clone();
        return InventoryItemCodec.copy(item);
    }

    public record DecodedItems(ItemStack[] items, int weight) {
    }

    public static int cacheWeight(String encoded, int weight) {
        return (int) Math.min(Integer.MAX_VALUE, 64L + encoded.length() + weight);
    }

    public String serializeItems(ItemStack[] items) {
        try {
            return InventoryItemCodec.encode(items);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize inventory", exception);
        }
    }

    public ItemStack[] deserializeItems(String encoded) {
        ItemStack[] items = this.decodeItems(encoded);
        return items == null ? null : this.copyItems(items);
    }

    public ItemMatch findItem(String encoded, ToIntFunction<ItemStack> score) {
        return this.findItem(this.decodeItems(encoded), score);
    }

    public ItemMatch findItem(ItemStack[] items, ToIntFunction<ItemStack> score) {
        if (items == null) return null;
        int selectedSlot = -1, highestScore = -1;
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] == null) continue;
            int itemScore = score.applyAsInt(items[slot]);
            if (itemScore <= highestScore) continue;
            selectedSlot = slot;
            highestScore = itemScore;
        }
        return selectedSlot < 0 ? null : new ItemMatch(selectedSlot, highestScore);
    }

    public record ItemMatch(int slot, int score) {
    }

    private ItemStack[] decodeItems(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        DecodedItems cached = this.decodedItems.getIfPresent(encoded);
        if (cached != null) return cached.items();
        byte[] raw = Base64.getDecoder().decode(encoded);
        try (InventoryItemCodec.Reader input = new InventoryItemCodec.Reader(raw)) {
            int length = input.length();
            ItemStack[] items = new ItemStack[length];
            for (int index = 0; index < length; index++) {
                items[index] = input.read();
            }
            input.finish();
            this.decodedItems.put(encoded, new DecodedItems(items, input.weight()));
            return items;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize inventory", exception);
        }
    }

    public String serializeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == null || itemStack.getType() == Material.AIR) return null;
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream outputStream = new BukkitObjectOutputStream(byteStream)) {
            outputStream.writeObject(itemStack.serialize());
            outputStream.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize single item", exception);
        }
    }

    public ItemStack deserializeItem(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream objectInputStream = new BukkitObjectInputStream(inputStream)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> serialized = (Map<String, Object>) objectInputStream.readObject();
            if (serialized == null) return null;
            ItemStack itemStack = ItemStack.deserialize(serialized);
            return itemStack == null ? null : itemStack.clone();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize single item", exception);
        }
    }

    public ArrayDecoder prepareItems(String encoded) {
        return new ArrayDecoder(encoded, this.decodedItems);
    }

    public interface PreparedItems {
        boolean advance();

        ItemStack[] items();
    }

    public static final class ArrayDecoder implements PreparedItems {
        private final String encoded;
        private final byte[] raw;
        private final Cache<String, InventoryItemSerializer.DecodedItems> cache;
        private InventoryItemCodec.Reader input;
        private ItemStack[] items;
        private ItemStack[] cached;
        private int index;
        private int weight;
        private Phase phase = Phase.OPEN;

        public ArrayDecoder(String encoded, Cache<String, InventoryItemSerializer.DecodedItems> cache) {
            this.encoded = encoded;
            this.cache = cache;
            var snapshot = encoded == null || encoded.isEmpty() ? null : cache.getIfPresent(encoded);
            this.cached = snapshot == null ? null : snapshot.items();
            this.raw = encoded == null || encoded.isEmpty() || this.cached != null ? null :
                    Base64.getDecoder().decode(encoded);
        }

        public boolean finished() {
            return this.phase == Phase.DONE;
        }

        @Override
        public boolean advance() {
            this.advanceStep();
            return this.finished();
        }

        @Override
        public ItemStack[] items() {
            return this.items;
        }

        private void advanceStep() {
            try {
                switch (this.phase) {
                    case OPEN -> {
                        if (this.encoded == null || this.encoded.isEmpty()) {
                            this.phase = Phase.DONE;
                            return;
                        }
                        if (this.cached != null) {
                            this.items = new ItemStack[this.cached.length];
                            this.phase = Phase.COPY;
                        } else {
                            this.input = new InventoryItemCodec.Reader(this.raw);
                            this.items = new ItemStack[this.input.length()];
                            this.cached = new ItemStack[this.items.length];
                            this.phase = Phase.READ;
                        }
                    }
                    case READ -> {
                        if (this.index < this.items.length) {
                            this.items[this.index++] = this.input.read();
                            return;
                        }
                        this.index = 0;
                        this.input.finish();
                        this.weight = this.input.weight();
                        if (InventoryItemSerializer.cacheWeight(this.encoded, this.weight) >
                                InventoryItemSerializer.DECODE_CACHE_WEIGHT) {
                            this.cached = null;
                            this.phase = Phase.DONE;
                            return;
                        }
                        this.phase = Phase.CACHE;
                    }
                    case COPY, CACHE -> {
                        if (this.index < this.items.length) {
                            ItemStack original =
                                    this.phase == Phase.COPY ? this.cached[this.index] : this.items[this.index];
                            ItemStack copy = InventoryItemSerializer.copyItem(original);
                            if (this.phase == Phase.COPY) this.items[this.index] = copy;
                            else this.cached[this.index] = copy;
                            this.index++;
                            return;
                        }
                        if (this.phase == Phase.CACHE)
                            this.cache.put(this.encoded,
                                    new InventoryItemSerializer.DecodedItems(this.cached, this.weight));
                        this.phase = Phase.DONE;
                    }
                    case DONE -> {
                    }
                }
            } catch (IOException | ClassNotFoundException exception) {
                throw new IllegalStateException("Failed to deserialize inventory", exception);
            }
        }
    }

    private enum Phase {
        OPEN, READ, COPY, CACHE, DONE
    }
}
