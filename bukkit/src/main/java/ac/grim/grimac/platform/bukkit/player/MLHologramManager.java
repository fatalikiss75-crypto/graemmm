package ac.grim.grimac.platform.bukkit.player;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер голограмм для отображения ML-данных над игроками
 * ВЕРСИЯ ДЛЯ GrimAC + PURPUR 1.21.1
 */
public class MLHologramManager {

    private static final Map<UUID, PlayerHologram> holograms = new ConcurrentHashMap<>();
    private static final int MAX_STRIKES = 6; // Максимум последних ударов
    private static final double HOLOGRAM_HEIGHT = 2.5; // Высота над головой игрока

    private static BukkitRunnable updateTask;

    /**
     * Инициализация системы голограмм
     */
    public static void initialize() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        // Обновляем голограммы каждые 10 тиков (0.5 сек)
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerHologram hologram : holograms.values()) {
                    hologram.update();
                }
            }
        };

        // Используем правильный способ получения плагина
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (plugin != null) {
            updateTask.runTaskTimer(plugin, 0L, 10L);
        }

        System.out.println("[GrimAC ML] Голограммы инициализированы");
    }

    /**
     * Остановка системы голограмм
     */
    public static void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        for (PlayerHologram hologram : holograms.values()) {
            hologram.remove();
        }
        holograms.clear();

        System.out.println("[GrimAC ML] Голограммы отключены");
    }

    /**
     * Добавить данные обударе
     */
    public static void addStrike(UUID playerUUID, double probability) {
        PlayerHologram hologram = holograms.computeIfAbsent(
                playerUUID,
                uuid -> new PlayerHologram(uuid)
        );
        hologram.addStrike(probability);
    }

    /**
     * Получить голограмму игрока
     */
    public static PlayerHologram getHologram(UUID playerUUID) {
        return holograms.get(playerUUID);
    }

    /**
     * Удалить голограмму игрока
     */
    public static void removeHologram(UUID playerUUID) {
        PlayerHologram hologram = holograms.remove(playerUUID);
        if (hologram != null) {
            hologram.remove();
        }
    }

    /**
     * Класс голограммы для одного игрока
     */
    public static class PlayerHologram {
        private final UUID playerUUID;
        private final LinkedList<Double> strikes = new LinkedList<>();
        private final List<ArmorStand> armorStands = new ArrayList<>();

        private double averageProbability = 0.0;
        private long lastUpdate = 0;

        public PlayerHologram(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        /**
         * Добавить новый удар
         */
        public void addStrike(double probability) {
            strikes.addFirst(probability);

            // Ограничиваем количество
            while (strikes.size() > MAX_STRIKES) {
                strikes.removeLast();
            }

            // Пересчитываем среднее
            updateAverage();
            lastUpdate = System.currentTimeMillis();
        }

        /**
         * Обновить среднее значение
         */
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

        /**
         * Обновить позицию и текст голограммы
         */
        public void update() {
            // Получаем GrimPlayer для проверки онлайна
            GrimPlayer grimPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(playerUUID);
            if (grimPlayer == null) {
                remove();
                return;
            }

            // Если нет ударов - скрываем голограмму
            if (strikes.isEmpty()) {
                remove();
                return;
            }

            // Получаем локацию из GrimPlayer напрямую
            // GrimPlayer хранит x, y, z координаты
            double x = grimPlayer.x;
            double y = grimPlayer.y;
            double z = grimPlayer.z;

            // Получаем мир (попробуем через Bukkit)
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

            // Создаём локацию
            Location baseLoc = new Location(world, x, y + HOLOGRAM_HEIGHT, z);

            // Удаляем старые стенды если изменилось количество строк
            int requiredStands = strikes.size() + 2; // Удары + AVG + заголовок
            while (armorStands.size() > requiredStands) {
                ArmorStand stand = armorStands.remove(armorStands.size() - 1);
                stand.remove();
            }

            // Создаём недостающие стенды
            while (armorStands.size() < requiredStands) {
                ArmorStand stand = createArmorStand(baseLoc);
                armorStands.add(stand);
            }

            // Обновляем позиции и текст
            int index = 0;

            // Строка 0: Заголовок "Последние проверки"
            ArmorStand headerStand = armorStands.get(index++);
            headerStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));
            headerStand.customName(Component.text("Последние проверки:")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD));

            // Строки 1-6: Последние удары
            for (double prob : strikes) {
                ArmorStand strikeStand = armorStands.get(index++);
                strikeStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));

                Component strikeText = formatStrike(prob);
                strikeStand.customName(strikeText);
            }

            // Последняя строка: Средний риск (AVG)
            ArmorStand avgStand = armorStands.get(index);
            avgStand.teleport(new Location(world, x, y + HOLOGRAM_HEIGHT + (index * 0.3), z));
            avgStand.customName(formatAverage());
        }

        /**
         * Форматировать строку с ударом
         */
        private Component formatStrike(double probability) {
            // Цвет в зависимости от вероятности
            TextColor color;
            if (probability >= 0.8) {
                color = NamedTextColor.DARK_RED;
            } else if (probability >= 0.6) {
                color = NamedTextColor.RED;
            } else if (probability >= 0.4) {
                color = NamedTextColor.YELLOW;
            } else if (probability >= 0.2) {
                color = NamedTextColor.GREEN;
            } else {
                color = NamedTextColor.DARK_GREEN;
            }

            return Component.text(String.format("%.4f", probability))
                    .color(color);
        }

        /**
         * Форматировать строку с AVG
         */
        private Component formatAverage() {
            TextColor color;
            if (averageProbability >= 0.7) {
                color = NamedTextColor.DARK_RED;
            } else if (averageProbability >= 0.5) {
                color = NamedTextColor.RED;
            } else if (averageProbability >= 0.3) {
                color = NamedTextColor.YELLOW;
            } else {
                color = NamedTextColor.GREEN;
            }

            return Component.text("AVG: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.4f", averageProbability))
                            .color(color)
                            .decorate(TextDecoration.BOLD));
        }

        /**
         * Создать невидимый ArmorStand для текста
         */
        private ArmorStand createArmorStand(Location loc) {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setMarker(true); // Делаем маркером (проходимый)
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            return stand;
        }

        /**
         * Удалить все стенды
         */
        public void remove() {
            for (ArmorStand stand : armorStands) {
                stand.remove();
            }
            armorStands.clear();
        }

        /**
         * Получить список ударов
         */
        public List<Double> getStrikes() {
            return new ArrayList<>(strikes);
        }

        /**
         * Получить среднюю вероятность
         */
        public double getAverageProbability() {
            return averageProbability;
        }

        /**
         * Получить время последнего обновления
         */
        public long getLastUpdate() {
            return lastUpdate;
        }
    }
}
