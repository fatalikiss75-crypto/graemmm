package ac.grim.grimac.platform.bukkit.player;

import ac.grim.grimac.checks.impl.aim.MLHologramBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;

/**
 * ИСПРАВЛЕНО: Голограммы показываются ВСЕГДА, даже при пустых strikes
 */
public class BukkitHologramBridge implements MLHologramBridge {

    private final Map<UUID, PlayerHologram> holograms = new ConcurrentHashMap<>();
    private static final int MAX_STRIKES = 24; // Было 8 → 24 фичи
    private static final double HOLOGRAM_HEIGHT = 2.5;
    private static final boolean DEBUG_MODE = false; // Выключаем дебаг
    private static final boolean SHOW_EMPTY_HOLOGRAMS = true; // ВСЕГДА показывать голограммы
    private BukkitRunnable updateTask;

    @Override
    public void initialize() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // ИСПРАВЛЕНО: Создаём голограммы для ВСЕХ онлайн игроков
                if (SHOW_EMPTY_HOLOGRAMS) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        holograms.computeIfAbsent(player.getUniqueId(),
                                uuid -> new PlayerHologram(uuid));
                    }
                }

                for (PlayerHologram hologram : holograms.values()) {
                    hologram.update();
                }
            }
        };

        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (plugin != null) {
            updateTask.runTaskTimer(plugin, 0L, 10L);
            System.out.println("[GrimAC ML] ✓ Bukkit голограммы инициализированы (SHOW_EMPTY=true)");
        } else {
            System.err.println("[GrimAC ML] ✗ ОШИБКА: Плагин GrimAC не найден!");
        }
    }

    @Override
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        for (PlayerHologram hologram : holograms.values()) {
            hologram.remove();
        }
        holograms.clear();

        System.out.println("[GrimAC ML] Bukkit голограммы отключены");
    }

    @Override
    public void addStrike(UUID playerUUID, double probability) {
        if (DEBUG_MODE) {
            Player player = Bukkit.getPlayer(playerUUID);
            String playerName = player != null ? player.getName() : playerUUID.toString();
            System.out.println("[GrimAC ML BRIDGE] addStrike(" + playerName + ", " +
                    String.format("%.4f", probability) + ")");
        }

        PlayerHologram hologram = holograms.computeIfAbsent(
                playerUUID,
                uuid -> {
                    if (DEBUG_MODE) {
                        System.out.println("[GrimAC ML BRIDGE] Создана новая голограмма для " + uuid);
                    }
                    return new PlayerHologram(uuid);
                }
        );

        hologram.addStrike(probability);
    }

    @Override
    public void removeHologram(UUID playerUUID) {
        PlayerHologram hologram = holograms.remove(playerUUID);
        if (hologram != null) {
            hologram.remove();
            if (DEBUG_MODE) {
                System.out.println("[GrimAC ML BRIDGE] Удалена голограмма для " + playerUUID);
            }
        }
    }

    public PlayerHologram getHologram(UUID playerUUID) {
        return holograms.get(playerUUID);
    }

    public int getHologramCount() {
        return holograms.size();
    }

    /**
     * Класс голограммы для одного игрока
     */
    public class PlayerHologram {
        private final UUID playerUUID;
        private final LinkedList<Double> strikes = new LinkedList<>();
        private final List<ArmorStand> armorStands = new ArrayList<>();

        private double averageProbability = 0.0;
        private long lastUpdate = 0;

        public PlayerHologram(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        public void addStrike(double probability) {
            strikes.addFirst(probability);

            while (strikes.size() > MAX_STRIKES) {
                strikes.removeLast();
            }

            updateAverage();
            lastUpdate = System.currentTimeMillis();

            if (DEBUG_MODE) {
                System.out.println("[GrimAC ML HOLOGRAM] " + playerUUID +
                        " - strikes.size=" + strikes.size() +
                        ", avgProb=" + String.format("%.4f", averageProbability));
            }
        }

        private void updateAverage() {
            if (strikes.isEmpty()) {
                averageProbability = 0.0;
                return;
            }

            double sum = 0.0;
            for (double strike : strikes) {
                sum += strike;
            }
            averageProbability = sum / strikes.size();
        }

        public void update() {
            GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(playerUUID);
            if (grimPlayer == null) {
                remove();
                return;
            }

            // ИСПРАВЛЕНО: УБРАН фильтр strikes.isEmpty() - показываем ВСЕГДА!
            // Старый код:
            // if (strikes.isEmpty()) {
            //     remove();
            //     return;
            // }

            double x = grimPlayer.x;
            double y = grimPlayer.y;
            double z = grimPlayer.z;

            World world = null;
            for (World w : Bukkit.getWorlds()) {
                Player p = w.getPlayers().stream()
                        .filter(player -> player.getUniqueId().equals(playerUUID))
                        .findFirst()
                        .orElse(null);
                if (p != null) {
                    world = w;
                    break;
                }
            }

            if (world == null) {
                remove();
                return;
            }

            Location baseLoc = new Location(world, x, y + HOLOGRAM_HEIGHT, z);

            // ИСПРАВЛЕНО: Если нет strikes, показываем хотя бы заголовок + "Нет данных" + AVG
            int requiredStands;
            if (strikes.isEmpty()) {
                requiredStands = 3; // Заголовок + "Нет данных" + AVG
            } else {
                requiredStands = strikes.size() + 2; // Заголовок + strikes + AVG
            }

            while (armorStands.size() > requiredStands) {
                ArmorStand stand = armorStands.remove(armorStands.size() - 1);
                stand.remove();
            }

            while (armorStands.size() < requiredStands) {
                ArmorStand stand = createArmorStand(baseLoc);
                armorStands.add(stand);
            }

            int index = 0;

            // Строка 0: Заголовок
            ArmorStand headerStand = armorStands.get(index++);
            headerStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));
            headerStand.setCustomName("§b§lПоследние проверки:");

            // Показываем strikes (до MAX_STRIKES = 24)
            for (int i = 0; i < Math.min(strikes.size(), 24); i++) {
                double prob = strikes.get(i);
                ArmorStand strikeStand = armorStands.get(index++);
                strikeStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));

                String strikeText = formatStrikeLegacy(prob);
                strikeStand.setCustomName(strikeText);
            }

            // Заполняем пустые слоты, если strikes < 24
            for (int i = strikes.size(); i < 24 && index < armorStands.size() - 1; i++) {
                ArmorStand emptyStand = armorStands.get(index++);
                emptyStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));
                emptyStand.setCustomName("§8§o...");
            }

            // Последняя строка: AVG (показываем N/A если 0.0)
            ArmorStand avgStand = armorStands.get(index);
            avgStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));
            avgStand.setCustomName(formatAverageLegacy());
        }

        // ЗАМЕНИ formatStrike() на:
        private String formatStrikeLegacy(double probability) {
            String color;
            if (probability >= 0.8) {
                color = "§4"; // DARK_RED
            } else if (probability >= 0.6) {
                color = "§c"; // RED
            } else if (probability >= 0.4) {
                color = "§e"; // YELLOW
            } else if (probability >= 0.2) {
                color = "§a"; // GREEN
            } else {
                color = "§2"; // DARK_GREEN
            }

            return color + String.format("%.4f", probability);
        }
        // ЗАМЕНИ formatAverage() на:
        private String formatAverageLegacy() {
            // Показываем N/A если среднее 0.0 (нет данных)
            if (averageProbability < 0.0001) {
                return "§7AVG: §7N/A";
            }

            String color;
            if (averageProbability >= 0.7) {
                color = "§4"; // DARK_RED
            } else if (averageProbability >= 0.5) {
                color = "§c"; // RED
            } else if (averageProbability >= 0.3) {
                color = "§e"; // YELLOW
            } else {
                color = "§a"; // GREEN
            }

            return "§7AVG: " + color + "§l" + String.format("%.4f", averageProbability);
        }

        private ArmorStand createArmorStand(Location loc) {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            return stand;
        }

        public void remove() {
            for (ArmorStand stand : armorStands) {
                stand.remove();
            }
            armorStands.clear();
        }

        public List<Double> getStrikes() {
            return new ArrayList<>(strikes);
        }

        public double getAverageProbability() {
            return averageProbability;
        }

        public long getLastUpdate() {
            return lastUpdate;
        }
    }
}
