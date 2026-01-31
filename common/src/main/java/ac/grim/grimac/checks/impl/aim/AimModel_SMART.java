package ac.grim.grimac.checks.impl.aim;

import java.io.*;
import java.util.*;

/**
 * УМНАЯ ML-модель для детекции аимботов
 * Использует логистическую регрессию с регуляризацией
 */
public class AimModel_SMART implements Serializable {
    private static final long serialVersionUID = 2L;

    // Параметры модели
    private double[] weights;           // Веса для каждой фичи
    private double bias;                // Смещение (bias)
    private double[] featureMeans;      // Средние значения фичей (для нормализации)
    private double[] featureStds;       // Стандартные отклонения (для нормализации)

    // Метрики обучения
    private double accuracy;            // Точность на валидации
    private double precision;           // Точность положительных предсказаний
    private double recall;              // Полнота
    private double f1Score;             // F1-мера
    private int trainingIterations;     // Количество итераций обучения
    private double finalLoss;           // Финальная ошибка

    public AimModel_SMART(int numFeatures) {
        this.weights = new double[numFeatures];
        this.bias = 0.0;
        this.featureMeans = new double[numFeatures];
        this.featureStds = new double[numFeatures];

        // Инициализация весов малыми случайными значениями
        Random rand = new Random(42);
        for (int i = 0; i < numFeatures; i++) {
            weights[i] = (rand.nextDouble() - 0.5) * 0.01;
        }
    }

    /**
     * Сигмоида - функция активации
     */
    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * Предсказание для нормализованных фичей
     */
    private double predictRaw(double[] features) {
        double z = bias;
        for (int i = 0; i < Math.min(features.length, weights.length); i++) {
            z += weights[i] * features[i];
        }
        return sigmoid(z);
    }

    /**
     * Публичное предсказание с нормализацией
     */
    public double predict(double[] features) {
        if (features == null || features.length == 0) {
            System.out.println("[MODEL DEBUG] predict() - NULL/EMPTY features!");
            return 0.0;
        }

        if (features.length != weights.length) {
            System.err.println("╔════════════════════════════════════════════╗");
            System.err.println("║  РАЗМЕРНОСТЬ НЕ СОВПАДАЕТ!                ║");
            System.err.println("╠════════════════════════════════════════════╣");
            System.err.println("║  Model weights: " + weights.length);
            System.err.println("║  Input features: " + features.length);
            System.err.println("║  ✗ Модель НЕ РАБОТАЕТ!                    ║");
            System.err.println("║  ✓ Решение: /grimaitrain                  ║");
            System.err.println("╚════════════════════════════════════════════╝");
            return 0.0;
        }

        double[] normalized = normalizeFeatures(features);
        double result = predictRaw(normalized);

        return result;
    }

    /**
     * Нормализация фичей (стандартизация)
     */
    /**
     * Нормализация фичей (стандартизация)
     * ИСПРАВЛЕНО: Проверка границ массива
     */
    private double[] normalizeFeatures(double[] features) {
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Проверяем ТОЧНОЕ совпадение!
        if (features.length != featureMeans.length) {
            System.err.println("╔════════════════════════════════════════════╗");
            System.err.println("║  КРИТИЧЕСКАЯ ОШИБКА РАЗМЕРНОСТИ!          ║");
            System.err.println("╠════════════════════════════════════════════╣");
            System.err.println("║  Модель обучена на: " + featureMeans.length + " фичах");
            System.err.println("║  Получено: " + features.length + " фичей");
            System.err.println("║  ⚠ ПЕРЕОБУЧИТЕ: /grimaitrain              ║");
            System.err.println("╚════════════════════════════════════════════╝");
            return new double[featureMeans.length]; // Нули
        }

        double[] normalized = new double[features.length];

        for (int i = 0; i < features.length; i++) {
            if (featureStds[i] > 1e-10) {
                normalized[i] = (features[i] - featureMeans[i]) / featureStds[i];
            } else {
                normalized[i] = 0.0;
            }
        }
        return normalized;
    }

    /**
     * Обучение модели методом градиентного спуска
     */
    public void train(List<TrainingExample> trainingData, List<TrainingExample> validationData,
                      double learningRate, double l2Lambda, int maxIterations) {

        if (trainingData.isEmpty()) {
            throw new IllegalArgumentException("Training data is empty!");
        }

        System.out.println("[GrimAC ML] Начинаем обучение модели...");
        System.out.println("[GrimAC ML] Обучающая выборка: " + trainingData.size());
        System.out.println("[GrimAC ML] Валидационная выборка: " + validationData.size());

        // Вычисляем статистику для нормализации
        computeNormalizationStats(trainingData);

        // Нормализуем данные
        List<TrainingExample> normalizedTrain = new ArrayList<>();
        for (TrainingExample ex : trainingData) {
            normalizedTrain.add(new TrainingExample(
                    normalizeFeatures(ex.features),
                    ex.label
            ));
        }

        List<TrainingExample> normalizedValid = new ArrayList<>();
        for (TrainingExample ex : validationData) {
            normalizedValid.add(new TrainingExample(
                    normalizeFeatures(ex.features),
                    ex.label
            ));
        }

        // Градиентный спуск
        double bestValidLoss = Double.MAX_VALUE;
        double[] bestWeights = null;
        double bestBias = 0.0;
        int patience = 50;
        int patienceCounter = 0;

        for (int iter = 0; iter < maxIterations; iter++) {
            // Перемешиваем данные
            Collections.shuffle(normalizedTrain);

            // Одна эпоха обучения
            double totalLoss = 0.0;

            for (TrainingExample example : normalizedTrain) {
                // Forward pass
                double prediction = predictRaw(example.features);
                double error = prediction - example.label;

                // Backward pass (градиенты)
                for (int i = 0; i < weights.length; i++) {
                    double gradient = error * example.features[i];
                    // L2 регуляризация
                    gradient += l2Lambda * weights[i];
                    weights[i] -= learningRate * gradient;
                }
                bias -= learningRate * error;

                // Loss (binary cross-entropy)
                double loss = -example.label * Math.log(prediction + 1e-10)
                        - (1 - example.label) * Math.log(1 - prediction + 1e-10);
                totalLoss += loss;
            }

            // Валидация каждые 10 итераций
            if (iter % 10 == 0) {
                double validLoss = computeLoss(normalizedValid);
                double trainLoss = totalLoss / normalizedTrain.size();

                System.out.printf("[GrimAC ML] Iter %d: train_loss=%.4f, valid_loss=%.4f%n",
                        iter, trainLoss, validLoss);

                // Early stopping
                if (validLoss < bestValidLoss) {
                    bestValidLoss = validLoss;
                    bestWeights = weights.clone();
                    bestBias = bias;
                    patienceCounter = 0;
                } else {
                    patienceCounter++;
                    if (patienceCounter >= patience) {
                        System.out.println("[GrimAC ML] Early stopping на итерации " + iter);
                        break;
                    }
                }
            }
        }

        // Восстанавливаем лучшие веса
        if (bestWeights != null) {
            weights = bestWeights;
            bias = bestBias;
        }

        // Финальная оценка
        evaluateModel(normalizedValid);

        this.trainingIterations = maxIterations;
        this.finalLoss = bestValidLoss;

        System.out.println("[GrimAC ML] ═══════════════════════════════════");
        System.out.println("[GrimAC ML] Обучение завершено!");
        System.out.println("[GrimAC ML] Точность: " + String.format("%.2f%%", accuracy * 100));
        System.out.println("[GrimAC ML] Precision: " + String.format("%.2f%%", precision * 100));
        System.out.println("[GrimAC ML] Recall: " + String.format("%.2f%%", recall * 100));
        System.out.println("[GrimAC ML] F1-Score: " + String.format("%.2f", f1Score));
        System.out.println("[GrimAC ML] ═══════════════════════════════════");
    }

    /**
     * Вычисление статистики для нормализации
     */
    private void computeNormalizationStats(List<TrainingExample> data) {
        int numFeatures = data.get(0).features.length;

        // Средние
        for (TrainingExample ex : data) {
            for (int i = 0; i < numFeatures; i++) {
                featureMeans[i] += ex.features[i];
            }
        }
        for (int i = 0; i < numFeatures; i++) {
            featureMeans[i] /= data.size();
        }

        // Стандартные отклонения
        for (TrainingExample ex : data) {
            for (int i = 0; i < numFeatures; i++) {
                double diff = ex.features[i] - featureMeans[i];
                featureStds[i] += diff * diff;
            }
        }
        for (int i = 0; i < numFeatures; i++) {
            featureStds[i] = Math.sqrt(featureStds[i] / data.size());
        }
    }

    /**
     * Вычисление loss на выборке
     */
    private double computeLoss(List<TrainingExample> data) {
        double totalLoss = 0.0;
        for (TrainingExample ex : data) {
            double pred = predictRaw(ex.features);
            double loss = -ex.label * Math.log(pred + 1e-10)
                    - (1 - ex.label) * Math.log(1 - pred + 1e-10);
            totalLoss += loss;
        }
        return totalLoss / data.size();
    }

    /**
     * Оценка модели на валидационной выборке
     */
    private void evaluateModel(List<TrainingExample> validData) {
        int truePositives = 0;
        int falsePositives = 0;
        int trueNegatives = 0;
        int falseNegatives = 0;

        for (TrainingExample ex : validData) {
            double pred = predictRaw(ex.features);
            boolean predicted = pred >= 0.5;
            boolean actual = ex.label >= 0.5;

            if (predicted && actual) truePositives++;
            else if (predicted && !actual) falsePositives++;
            else if (!predicted && !actual) trueNegatives++;
            else falseNegatives++;
        }

        this.accuracy = (truePositives + trueNegatives) / (double) validData.size();

        if (truePositives + falsePositives > 0) {
            this.precision = truePositives / (double) (truePositives + falsePositives);
        } else {
            this.precision = 0.0;
        }

        if (truePositives + falseNegatives > 0) {
            this.recall = truePositives / (double) (truePositives + falseNegatives);
        } else {
            this.recall = 0.0;
        }

        if (precision + recall > 0) {
            this.f1Score = 2 * (precision * recall) / (precision + recall);
        } else {
            this.f1Score = 0.0;
        }
    }

    /**
     * Сохранение модели
     */
    public void save(String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }

        System.out.println("[GrimAC ML] Модель сохранена: " + path);
    }

    /**
     * Загрузка модели
     */
    public static AimModel_SMART load(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (AimModel_SMART) ois.readObject();
        }
    }

    /**
     * Получить метрики модели
     */
    public String getMetrics() {
        return String.format(
                "Accuracy: %.2f%% | Precision: %.2f%% | Recall: %.2f%% | F1: %.2f",
                accuracy * 100, precision * 100, recall * 100, f1Score
        );
    }

    /**
     * Класс для обучающего примера
     */
    public static class TrainingExample implements Serializable {
        private static final long serialVersionUID = 1L;

        public final double[] features;
        public final double label; // 0.0 = legit, 1.0 = cheat

        public TrainingExample(double[] features, double label) {
            this.features = features;
            this.label = label;
        }
    }
}
