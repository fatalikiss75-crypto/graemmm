package ac.grim.grimac.platform.bukkit.player;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.aim.MLBridgeHolder;
import ac.grim.grimac.player.GrimPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI меню "Курятник" для отображения ML-данных игроков
 * ИСПРАВЛЕНО: Использует bridge для доступа к голограммам
 */
public class MLMenuGUI {

    private static final int ITEMS_PER_PAGE = 28;
    private static final Map<UUID, Integer> viewerPages = new HashMap<>();

    public static void openMenu(Player viewer, int page) {
        viewerPages.put(viewer.getUniqueId(), page);

        Inventory inv;

        // Проверяем, есть ли метод с Component (Java Reflection)
        try {
            Bukkit.class.getMethod("createInventory", org.bukkit.inventory.InventoryHolder.class, int.class, net.kyori.adventure.text.Component.class);
            // Метод есть — используем Component
            inv = Bukkit.createInventory(
                    null,
                    54,
                    Component.text("Курятник")
                            .color(TextColor.color(255, 215, 0))
                            .decorate(TextDecoration.BOLD)
            );
        } catch (NoSuchMethodException e) {
            // Метод отсутствует — используем старый String
            inv = Bukkit.createInventory(
                    null,
                    54,
                    "§6§lКурятник"
            );
        }

        List<PlayerData> players = getAllPlayersData();
        players.sort((a, b) -> Double.compare(b.avgProbability, a.avgProbability));

        int totalPages = (int) Math.ceil((double) players.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, players.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            PlayerData data = players.get(i);

            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            ItemStack playerItem = createPlayerItem(data);
            inv.setItem(slot, playerItem);

            slot++;

            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        fillBorders(inv);

        if (page > 0) {
            inv.setItem(48, createNavigationItem(Material.ARROW, "§a← Назад", "§7Страница " + page));
        }

        if (page < totalPages - 1) {
            inv.setItem(50, createNavigationItem(Material.ARROW, "§aВперёд →", "§7Страница " + (page + 2)));
        }

        inv.setItem(49, createNavigationItem(
                Material.COMPASS,
                "§e⟳ Обновить",
                "§7Обновить данные",
                "§7Страница §e" + (page + 1) + "§7/§e" + totalPages
        ));

        inv.setItem(4, createInfoItem(players.size(), totalPages, page + 1));

        viewer.openInventory(inv);
    }


    private static ItemStack createPlayerItem(PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // ИСПРАВЛЕНО: Используем legacy String формат вместо Component
        String displayName;
        if (data.avgProbability >= 0.7) {
            displayName = "§4§l" + data.playerName; // DARK_RED + BOLD
        } else if (data.avgProbability >= 0.5) {
            displayName = "§c" + data.playerName; // RED
        } else if (data.avgProbability >= 0.3) {
            displayName = "§e" + data.playerName; // YELLOW
        } else {
            displayName = "§a" + data.playerName; // GREEN
        }

        meta.setDisplayName(displayName);

        // ИСПРАВЛЕНО: Используем legacy String список вместо Component
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§b§lПоследние проверки:");

        for (double strike : data.strikes) {
            String color = getLegacyProbabilityColor(strike);
            lore.add(color + String.format("  %.4f", strike));
        }

        if (data.strikes.isEmpty()) {
            lore.add("§7  Нет данных");
        }

        lore.add("");

        String avgDisplay;
        if (data.avgProbability < 0) {
            avgDisplay = "§7N/A"; // Показываем N/A вместо 0.0000
        } else {
            String avgColor = getLegacyProbabilityColor(data.avgProbability);
            avgDisplay = avgColor + "§l" + String.format("%.4f", data.avgProbability);
        }
        lore.add("§7Средний риск: §8AVG " + avgDisplay);

        lore.add("");
        lore.add("§7Последний сервер: §b" + (data.lastServer != null ? data.lastServer : "N/A"));

        if (data.lastStrikeTime > 0) {
            long secAgo = (System.currentTimeMillis() - data.lastStrikeTime) / 1000;
            String timeAgo = formatTime(secAgo);
            lore.add("§7Последний удар: §e" + timeAgo);
        }

        meta.setLore(lore);

        Player onlinePlayer = Bukkit.getPlayer(data.playerUUID);
        if (onlinePlayer != null) {
            meta.setOwningPlayer(onlinePlayer);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createInfoItem(int totalPlayers, int totalPages, int currentPage) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        // fallback на String
        meta.setDisplayName("§6§lИнформация");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("Всего игроков: §e" + totalPlayers);
        lore.add("Страниц: §e" + totalPages);
        lore.add("Текущая: §a" + currentPage);

        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }


    private static ItemStack createNavigationItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // displayName через String
        meta.setDisplayName(name);

        if (loreLines.length > 0) {
            List<String> lore = new ArrayList<>();
            Collections.addAll(lore, loreLines);
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryView view = event.getView();
        String title = view.title().toString();

        if (!title.contains("Курятник")) {
            return;
        }

        int slot = event.getRawSlot();

        // Разрешаем брать предметы из СВОЕГО инвентаря (нижняя часть)
        if (slot >= 54) {
            return; // Не отменяем событие для нижнего инвентаря
        }

        // Отменяем клики по GUI
        event.setCancelled(true);

        if (slot < 0) {
            return;
        }

        MLMenuGUI.handleClick(player, slot);
    }

    private static void fillBorders(Inventory inv) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();

        // Используем строку вместо Component
        meta.setDisplayName(""); // пустое название
        borderItem.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(45 + i, borderItem);
        }

        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, borderItem);
            inv.setItem(i * 9 + 8, borderItem);
        }
    }

    /**
     * Получить данные всех игроков ЧЕРЕЗ BRIDGE
     */
    private static List<PlayerData> getAllPlayersData() {
        List<PlayerData> result = new ArrayList<>();

        // Получаем bridge и проверяем что это Bukkit реализация
        if (!(MLBridgeHolder.getBridge() instanceof BukkitHologramBridge)) {
            System.out.println("[GrimAC ML GUI] Bridge не является BukkitHologramBridge!");
            return result;
        }

        BukkitHologramBridge bukkitBridge = (BukkitHologramBridge) MLBridgeHolder.getBridge();

        for (GrimPlayer grimPlayer : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            BukkitHologramBridge.PlayerHologram hologram = bukkitBridge.getHologram(grimPlayer.uuid);

            // ИСПРАВЛЕНО: Показываем ВСЕХ игроков, даже без данных, с 24 фичами
            PlayerData data = new PlayerData();
            data.playerUUID = grimPlayer.uuid;
            data.playerName = grimPlayer.getName();

            if (hologram != null) {
                data.strikes = hologram.getStrikes();
                data.avgProbability = hologram.getAverageProbability();
                data.lastStrikeTime = hologram.getLastUpdate();
            } else {
                // Нет данных ML - НЕ показываем 0.0000, показываем N/A
                data.strikes = new ArrayList<>();
                // Для пустых данных показываем "N/A" вместо 0.0000
                data.avgProbability = -1.0; // Специальное значение для N/A
                data.lastStrikeTime = 0;
            }

            data.lastServer = getCurrentServer();
            result.add(data);
        }

        return result;
    }

    /**
     * Получить legacy color код для вероятности
     */
    private static String getLegacyProbabilityColor(double probability) {
        if (probability >= 0.8) {
            return "§4"; // DARK_RED
        } else if (probability >= 0.6) {
            return "§c"; // RED
        } else if (probability >= 0.4) {
            return "§e"; // YELLOW
        } else if (probability >= 0.2) {
            return "§a"; // GREEN
        } else {
            return "§2"; // DARK_GREEN
        }
    }

    private static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " сек. назад";
        } else if (seconds < 3600) {
            return (seconds / 60) + " мин. назад";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " ч. назад";
        } else {
            return (seconds / 86400) + " дн. назад";
        }
    }

    private static String getCurrentServer() {
        return "?";
    }

    public static void handleClick(Player viewer, int slot) {
        Integer currentPage = viewerPages.get(viewer.getUniqueId());
        if (currentPage == null) currentPage = 0;

        if (slot == 48) {
            openMenu(viewer, currentPage - 1);
        } else if (slot == 50) {
            openMenu(viewer, currentPage + 1);
        } else if (slot == 49) {
            openMenu(viewer, currentPage);
        }
    }

    public static void removeViewer(UUID viewerUUID) {
        viewerPages.remove(viewerUUID);
    }

    private static class PlayerData {
        UUID playerUUID;
        String playerName;
        List<Double> strikes = new ArrayList<>();
        double avgProbability;
        long lastStrikeTime;
        String lastServer;
    }
}
