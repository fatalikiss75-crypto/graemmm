package ac.grim.grimac.platform.bukkit;

import ac.grim.grimac.checks.impl.aim.MLBridgeHolder;
import ac.grim.grimac.platform.bukkit.player.BukkitHologramBridge;

/**
 * Инициализатор ML Bridge для Bukkit платформы
 *
 * ВАЖНО: Этот класс должен быть вызван при старте Bukkit плагина GrimAC
 * Добавьте в ваш главный класс плагина (GrimBukkitPlugin или аналогичный):
 *
 * @Override
 * public void onEnable() {
 *     // ... существующий код ...
 *
 *     // Инициализируем ML Bridge
 *     MLBridgeInitializer.initialize(this);
 * }
 *
 * @Override
 * public void onDisable() {
 *     // ... существующий код ...
 *
 *     // Отключаем ML Bridge
 *     MLBridgeInitializer.shutdown();
 * }
 */
public class MLBridgeInitializer {

    private static BukkitHologramBridge bukkitBridge;

    /**
     * Инициализировать bridge при старте сервера
     * Вызывается из onEnable() главного класса плагина
     */
    public static void initialize(org.bukkit.plugin.Plugin plugin) {
        try {
            bukkitBridge = new BukkitHologramBridge();
            MLBridgeHolder.setBridge(bukkitBridge);

            bukkitBridge.initialize();  // <-- КРИТИЧЕСКИ ВАЖНО!

            System.out.println("╔═══════════════════════════════════════════════════");
            System.out.println("║ [GrimAC ML] Bukkit Bridge инициализирован!");
            System.out.println("║ Голограммы и GUI теперь доступны");
            System.out.println("╚═══════════════════════════════════════════════════");
        } catch (Exception e) {
            System.err.println("[GrimAC ML] ОШИБКА инициализации bridge:");
            e.printStackTrace();
        }
    }

    /**
     * Отключить bridge при выключении сервера
     * Вызывается из onDisable() главного класса плагина
     */
    public static void shutdown() {
        try {
            if (bukkitBridge != null) {
                bukkitBridge.shutdown();
                bukkitBridge = null;
            }

            MLBridgeHolder.reset();

            System.out.println("[GrimAC ML] Bukkit Bridge отключен");

        } catch (Exception e) {
            System.err.println("[GrimAC ML] ОШИБКА отключения bridge:");
            e.printStackTrace();
        }
    }

    /**
     * Получить bridge (для дополнительных операций)
     */
    public static BukkitHologramBridge getBridge() {
        return bukkitBridge;
    }
}
