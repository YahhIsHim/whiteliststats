package me.whiteliststats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public final class WhitelistStatsPlugin extends JavaPlugin implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;
    private static final int BACK = 49;

    private static final int TAB_GENERAL = 2;
    private static final int PLAYER_HEAD_SLOT = 0;
    private static final int TAB_ITEMS = 4;
    private static final int TAB_MOBS = 6;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_SIZE = 36;
    private static final int STAT_PREVIOUS = 45;
    private static final int STAT_NEXT = 53;

    private enum Tab { GENERAL, ITEMS, MOBS }

    /** One icon in a statistics tab. */
    private record Entry(Material icon, Component name, List<String> lines) {
    }

    /** Every vanilla "General" statistic, so that zero values are listed too. */
    private static final Map<String, String> GENERAL = new LinkedHashMap<>();

    static {
        String data = """
animals_bred=Animals Bred
clean_armor=Armor Pieces Cleaned
clean_banner=Banners Cleaned
open_barrel=Barrels Opened
bell_ring=Bells Rung
eat_cake_slice=Cake Slices Eaten
fill_cauldron=Cauldrons Filled
open_chest=Chests Opened
damage_absorbed=Damage Absorbed
damage_blocked_by_shield=Damage Blocked by Shield
damage_dealt=Damage Dealt
damage_dealt_absorbed=Damage Dealt (Absorbed)
damage_dealt_resisted=Damage Dealt (Resisted)
damage_resisted=Damage Resisted
damage_taken=Damage Taken
inspect_dispenser=Dispensers Searched
climb_one_cm=Distance Climbed
crouch_one_cm=Distance Crouched
fall_one_cm=Distance Fallen
fly_one_cm=Distance Flown
sprint_one_cm=Distance Sprinted
swim_one_cm=Distance Swum
walk_one_cm=Distance Walked
walk_on_water_one_cm=Distance Walked on Water
walk_under_water_one_cm=Distance Walked under Water
boat_one_cm=Distance by Boat
aviate_one_cm=Distance by Elytra
happy_ghast_one_cm=Distance by Happy Ghast
horse_one_cm=Distance by Horse
minecart_one_cm=Distance by Minecart
nautilus_one_cm=Distance by Nautilus
pig_one_cm=Distance by Pig
strider_one_cm=Distance by Strider
inspect_dropper=Droppers Searched
open_enderchest=Ender Chests Opened
fish_caught=Fish Caught
leave_game=Games Quit
inspect_hopper=Hoppers Searched
interact_with_anvil=Interactions with Anvil
interact_with_beacon=Interactions with Beacon
interact_with_blast_furnace=Interactions with Blast Furnace
interact_with_brewingstand=Interactions with Brewing Stand
interact_with_campfire=Interactions with Campfire
interact_with_cartography_table=Interactions with Cartography Table
interact_with_crafting_table=Interactions with Crafting Table
interact_with_furnace=Interactions with Furnace
interact_with_grindstone=Interactions with Grindstone
interact_with_lectern=Interactions with Lectern
interact_with_loom=Interactions with Loom
interact_with_smithing_table=Interactions with Smithing Table
interact_with_smoker=Interactions with Smoker
interact_with_stonecutter=Interactions with Stonecutter
drop=Items Dropped
enchant_item=Items Enchanted
jump=Jumps
mob_kills=Mob Kills
play_record=Music Discs Played
play_noteblock=Note Blocks Played
tune_noteblock=Note Blocks Tuned
deaths=Number of Deaths
pot_flower=Plants Potted
player_kills=Player Kills
raid_trigger=Raids Triggered
raid_win=Raids Won
clean_shulker_box=Shulker Boxes Cleaned
open_shulker_box=Shulker Boxes Opened
sneak_time=Sneak Time
talked_to_villager=Talked to Villagers
target_hit=Targets Hit
play_time=Time Played
time_since_death=Time Since Last Death
time_since_rest=Time Since Last Rest
total_world_time=Time with World Open
sleep_in_bed=Times Slept in a Bed
traded_with_villager=Traded with Villagers
trigger_trapped_chest=Trapped Chests Triggered
use_cauldron=Water Taken from Cauldron
""";
        for (String line : data.strip().split("\n")) {
            String[] parts = line.strip().split("=", 2);
            GENERAL.put("minecraft:" + parts[0], parts[1]);
        }
    }

    /** Marks our menus so clicks in them can be recognised. */
    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        private final boolean list;
        private final List<OfflinePlayer> players = new ArrayList<>();
        // Used by statistics menus only.
        private OfflinePlayer target;
        private JsonObject stats;
        private Tab tab;
        private int statPage;
        private int statPages;

        private Menu(int page, boolean list) {
            this.page = page;
            this.list = list;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Close our menus so their items can't be taken once the listener is gone.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        openList(player, 0);
        return true;
    }

    private void openList(Player viewer, int requestedPage) {
        List<OfflinePlayer> whitelist = new ArrayList<>(Bukkit.getWhitelistedPlayers());
        whitelist.sort(Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (whitelist.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Menu menu = new Menu(page, true);
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("Whitelisted Players"));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, whitelist.size());
        for (int i = start; i < end; i++) {
            OfflinePlayer target = whitelist.get(i);
            menu.players.add(target);
            String status = target.isOnline() ? "Online"
                    : target.hasPlayedBefore() ? "Offline" : "Never joined";
            menu.inventory.setItem(i - start, head(target, status, "Click to view statistics"));
        }

        if (page > 0) {
            menu.inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous page"));
        }
        menu.inventory.setItem(INFO, item(Material.BOOK, "Page " + (page + 1) + " of " + pages,
                "Whitelisted: " + whitelist.size()));
        if (page < pages - 1) {
            menu.inventory.setItem(NEXT, item(Material.ARROW, "Next page"));
        }

        viewer.openInventory(menu.inventory);
    }

    private void openStats(Player viewer, OfflinePlayer target, int returnPage) {
        JsonObject stats;
        try {
            stats = loadStats(target);
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.WARNING, "Could not read statistics for " + nameOf(target), e);
            viewer.sendMessage(text("Could not read that player's statistics. Check the console.",
                    NamedTextColor.RED));
            return;
        }
        showStats(viewer, target, stats, Tab.GENERAL, 0, returnPage);
    }

    private JsonObject loadStats(OfflinePlayer target) throws IOException {
        // Online players' latest numbers are in memory, so save them to disk first.
        if (target.isOnline()) {
            Bukkit.savePlayers();
        }

        // Stats live in <world>/players/stats. Search upwards, because the folder
        // reported for a dimension can be nested inside the main world folder.
        File folder = Bukkit.getWorlds().get(0).getWorldFolder().getAbsoluteFile();
        File statsFolder = null;
        while (folder != null && statsFolder == null) {
            File candidate = new File(folder, "players/stats");
            if (candidate.isDirectory()) {
                statsFolder = candidate;
            }
            folder = folder.getParentFile();
        }
        if (statsFolder == null) {
            throw new IOException("Could not find the players/stats folder");
        }

        File file = new File(statsFolder, target.getUniqueId() + ".json");
        if (!file.isFile()) {
            if (target.isOnline() || target.hasPlayedBefore()) {
                throw new IOException("Missing statistics file: " + file);
            }
            return new JsonObject(); // never joined, so everything is zero
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.has("stats") ? root.getAsJsonObject("stats") : new JsonObject();
        }
    }

    private void showStats(Player viewer, OfflinePlayer target, JsonObject stats, Tab tab,
                           int requestedPage, int returnPage) {
        List<Entry> entries = switch (tab) {
            case GENERAL -> generalEntries(stats);
            case ITEMS -> itemEntries(stats);
            case MOBS -> mobEntries(stats);
        };
        int pages = Math.max(1, (entries.size() + CONTENT_SIZE - 1) / CONTENT_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Menu menu = new Menu(returnPage, false);
        menu.target = target;
        menu.stats = stats;
        menu.tab = tab;
        menu.statPage = page;
        menu.statPages = pages;
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text(nameOf(target) + "'s Stats"));

        menu.inventory.setItem(PLAYER_HEAD_SLOT, head(target, target.isOnline() ? "Online"
                : target.hasPlayedBefore() ? "Offline" : "Never joined"));
        menu.inventory.setItem(TAB_GENERAL, tabIcon(Material.BOOK, "General", tab == Tab.GENERAL));
        menu.inventory.setItem(TAB_ITEMS, tabIcon(Material.CHEST, "Items", tab == Tab.ITEMS));
        menu.inventory.setItem(TAB_MOBS, tabIcon(Material.ZOMBIE_HEAD, "Mobs", tab == Tab.MOBS));

        int start = page * CONTENT_SIZE;
        int end = Math.min(start + CONTENT_SIZE, entries.size());
        for (int i = start; i < end; i++) {
            Entry entry = entries.get(i);
            menu.inventory.setItem(CONTENT_START + i - start,
                    icon(entry.icon(), entry.name(), entry.lines()));
        }
        if (entries.isEmpty()) {
            menu.inventory.setItem(22, item(Material.BARRIER, "Nothing recorded yet"));
        }

        if (page > 0) {
            menu.inventory.setItem(STAT_PREVIOUS, item(Material.ARROW, "Previous page"));
        }
        menu.inventory.setItem(48, item(Material.PAPER, "Page " + (page + 1) + " of " + pages));
        menu.inventory.setItem(BACK, item(Material.BARRIER, "Back to players"));
        if (page < pages - 1) {
            menu.inventory.setItem(STAT_NEXT, item(Material.ARROW, "Next page"));
        }
        viewer.openInventory(menu.inventory);
    }

    private ItemStack tabIcon(Material material, String name, boolean selected) {
        return item(material, name, selected ? "Selected" : "Click to view");
    }

    private JsonObject category(JsonObject stats, String name) {
        JsonElement element = stats.get("minecraft:" + name);
        return element != null ? element.getAsJsonObject() : new JsonObject();
    }

    private long value(JsonObject category, String key) {
        JsonElement element = category.get(key);
        return element != null ? element.getAsLong() : 0L;
    }

    private List<Entry> generalEntries(JsonObject stats) {
        JsonObject custom = category(stats, "custom");
        Map<String, String> labels = new HashMap<>(GENERAL);
        // Also include anything in the file that is not in the list above.
        for (String key : custom.keySet()) {
            labels.putIfAbsent(key, pretty(key));
        }

        List<Map.Entry<String, String>> sorted = new ArrayList<>(labels.entrySet());
        sorted.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, String> stat : sorted) {
            String key = stat.getKey();
            Component name = Component.translatable("stat." + key.replace(':', '.'), stat.getValue());
            entries.add(new Entry(generalIcon(key), name, List.of(format(key, value(custom, key)))));
        }
        return entries;
    }

    private Material generalIcon(String key) {
        if (key.endsWith("_one_cm")) return Material.LEATHER_BOOTS;
        if (key.contains("damage")) return Material.IRON_SWORD;
        if (key.contains("time")) return Material.CLOCK;
        return Material.PAPER;
    }

    private String format(String key, long raw) {
        if (key.endsWith("_one_cm")) {
            double metres = raw / 100.0;
            double kilometres = metres / 1000.0;
            if (kilometres > 0.5) return decimal(kilometres) + " km";
            if (metres > 0.5) return decimal(metres) + " m";
            return raw + " cm";
        }
        if (key.startsWith("minecraft:damage_")) {
            return decimal(raw / 10.0);
        }
        if (key.endsWith("_time") || key.startsWith("minecraft:time_since_")) {
            double seconds = raw / 20.0; // stored in ticks
            double minutes = seconds / 60.0;
            double hours = minutes / 60.0;
            double days = hours / 24.0;
            double years = days / 365.0;
            if (years > 0.5) return decimal(years) + " y";
            if (days > 0.5) return decimal(days) + " d";
            if (hours > 0.5) return decimal(hours) + " h";
            if (minutes > 0.5) return decimal(minutes) + " min";
            return decimal(seconds) + " s";
        }
        return String.format(Locale.UK, "%,d", raw);
    }

    private String decimal(double number) {
        return String.format(Locale.ROOT, "%.2f", number);
    }

    private List<Entry> itemEntries(JsonObject stats) {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("mined", "Times Mined");
        columns.put("broken", "Times Broken");
        columns.put("crafted", "Times Crafted");
        columns.put("used", "Times Used");
        columns.put("picked_up", "Picked Up");
        columns.put("dropped", "Dropped");

        List<Entry> entries = new ArrayList<>();
        for (String key : keys(stats, columns.keySet())) {
            Material material = Material.matchMaterial(key);
            Component name = material != null
                    ? Component.translatable(material.translationKey(), pretty(key))
                    : Component.text(pretty(key));
            Material shown = material != null && material.isItem() && !material.isAir()
                    ? material : Material.PAPER;
            entries.add(new Entry(shown, name, lines(stats, columns, key)));
        }
        return entries;
    }

    private List<Entry> mobEntries(JsonObject stats) {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("killed", "Killed by this player");
        columns.put("killed_by", "Times it killed this player");

        List<Entry> entries = new ArrayList<>();
        for (String key : keys(stats, columns.keySet())) {
            Material egg = Material.matchMaterial(key + "_spawn_egg");
            Component name = Component.translatable("entity." + key.replace(':', '.'), pretty(key));
            entries.add(new Entry(egg != null ? egg : Material.SKELETON_SKULL, name,
                    lines(stats, columns, key)));
        }
        return entries;
    }

    /** Every item or mob that appears in at least one of the categories. */
    private Set<String> keys(JsonObject stats, Set<String> categories) {
        Set<String> keys = new TreeSet<>();
        for (String name : categories) {
            keys.addAll(category(stats, name).keySet());
        }
        return keys;
    }

    private List<String> lines(JsonObject stats, Map<String, String> columns, String key) {
        List<String> lines = new ArrayList<>();
        columns.forEach((id, label) ->
                lines.add(label + ": " + format(id, value(category(stats, id), key))));
        return lines;
    }

    private String pretty(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        StringBuilder result = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        int slot = event.getRawSlot();
        Runnable action = null;
        if (menu.list) {
            if (slot >= 0 && slot < menu.players.size()) {
                OfflinePlayer target = menu.players.get(slot);
                action = () -> openStats(viewer, target, menu.page);
            } else if (slot == PREVIOUS && menu.page > 0) {
                action = () -> openList(viewer, menu.page - 1);
            } else if (slot == NEXT && event.getCurrentItem() != null) {
                action = () -> openList(viewer, menu.page + 1);
            }
        } else if (slot == BACK) {
            action = () -> openList(viewer, menu.page);
        } else {
            Tab tab = null;
            int statPage = 0;
            if (slot == TAB_GENERAL) {
                tab = Tab.GENERAL;
            } else if (slot == TAB_ITEMS) {
                tab = Tab.ITEMS;
            } else if (slot == TAB_MOBS) {
                tab = Tab.MOBS;
            } else if (slot == STAT_PREVIOUS && menu.statPage > 0) {
                tab = menu.tab;
                statPage = menu.statPage - 1;
            } else if (slot == STAT_NEXT && menu.statPage < menu.statPages - 1) {
                tab = menu.tab;
                statPage = menu.statPage + 1;
            }
            if (tab != null) {
                Tab nextTab = tab;
                int nextPage = statPage;
                action = () -> showStats(viewer, menu.target, menu.stats, nextTab, nextPage, menu.page);
            }
        }

        if (action != null) {
            // Switch menus after the click has finished processing.
            Runnable next = action;
            viewer.getScheduler().run(this, task -> {
                if (viewer.hasPermission("whiteliststats.use")) {
                    next.run();
                }
            }, null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    private String nameOf(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }

    private ItemStack head(OfflinePlayer owner, String... lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, nameOf(owner), lore);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(owner);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, String... lore) {
        return icon(material, Component.text(name), Arrays.asList(lore));
    }

    private ItemStack icon(Material material, Component name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> text(line, NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String value, TextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
