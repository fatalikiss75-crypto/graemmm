package ac.grim.grimac.checks.impl.aim;

/**
 * Статический держатель бриджа для голограмм
 * Инициализируется при старте из bukkit модуля
 */
public class MLBridgeHolder {

    private static MLHologramBridge bridge = new MLHologramBridge.NoOpBridge();

    /**
     * Установить реализацию бриджа (вызывается из bukkit при старте)
     */
    public static void setBridge(MLHologramBridge newBridge) {
        if (newBridge != null) {
            bridge = newBridge;
            System.out.println("[GrimAC ML] Bridge установлен: " + newBridge.getClass().getSimpleName());
        }
    }

    /**
     * Получить текущий бридж
     */
    public static MLHologramBridge getBridge() {
        return bridge;
    }

    /**
     * Сбросить бридж к заглушке
     */
    public static void reset() {
        bridge = new MLHologramBridge.NoOpBridge();
        System.out.println("[GrimAC ML] Bridge сброшен к NoOp");
    }
}
