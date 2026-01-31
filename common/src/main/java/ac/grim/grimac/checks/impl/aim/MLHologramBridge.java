package ac.grim.grimac.checks.impl.aim;

import java.util.UUID;

/**
 * Bridge интерфейс для взаимодействия между common и bukkit модулями
 * Common -> Bridge <- Bukkit
 */
public interface MLHologramBridge {

    /**
     * Добавить данные об ударе (вызывается из common)
     */
    void addStrike(UUID playerUUID, double probability);

    /**
     * Инициализировать систему голограмм
     */
    void initialize();

    /**
     * Остановить систему голограмм
     */
    void shutdown();

    /**
     * Удалить голограмму игрока
     */
    void removeHologram(UUID playerUUID);

    /**
     * Заглушка - ничего не делает (для серверов без поддержки голограмм)
     */
    class NoOpBridge implements MLHologramBridge {
        @Override
        public void addStrike(UUID playerUUID, double probability) {
            // Ничего не делаем
        }

        @Override
        public void initialize() {
            // Ничего не делаем
        }

        @Override
        public void shutdown() {
            // Ничего не делаем
        }

        @Override
        public void removeHologram(UUID playerUUID) {
            // Ничего не делаем
        }
    }
}
