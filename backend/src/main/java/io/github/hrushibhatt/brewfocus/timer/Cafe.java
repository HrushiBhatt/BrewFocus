package io.github.hrushibhatt.brewfocus.timer;

import java.util.List;

/**
 * The pixel café that fills up as full brews add up: each decoration and the number of full brews
 * that unlocks it. The IDs match the sprites in the frontend.
 */
public final class Cafe {

    record Item(String id, int at) {}

    static final List<Item> ITEMS = List.of(
            new Item("sprout", 1), new Item("lamp", 3), new Item("cat", 5), new Item("books", 10),
            new Item("moka", 20), new Item("vinyl", 35), new Item("candle", 50), new Item("golden-mug", 100));

    private Cafe() {}

    /** The decoration earned on reaching exactly {@code fullBrews}, or null. */
    public static String unlockedAt(int fullBrews) {
        return ITEMS.stream().filter(item -> item.at() == fullBrews).map(Item::id).findFirst().orElse(null);
    }
}
