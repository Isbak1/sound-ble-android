package com.example.sound_proof_android;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BluetoothProcess {

    private Context context;
    /**
     * Construit une série temporelle RSSI (échantillonnée régulièrement)
     */

    public BluetoothProcess(Context context) throws IOException {
        this.context = context; }

    public static Map<String, double[]> buildTimeSeries(
            List<BluetoothSignal> signals,
            long startTime,
            int intervalMs,
            int totalMs) {

        int samples = totalMs / intervalMs;
        double defaultRssi = -70;
        Map<String, double[]> map = new HashMap<>();

        for (BluetoothSignal sig : signals) {
            String addr = sig.getDeviceAddress();
            long delta = sig.getTimestamp() - startTime;
            int idx = (int) (delta / intervalMs);
            if (idx < 0 || idx >= samples) continue;

            map.putIfAbsent(addr, initArray(samples, defaultRssi));
            map.get(addr)[idx] = sig.getRssi();
        }

        return map;
    }

    private static double[] initArray(int size, double value) {
        double[] arr = new double[size];
        for (int i = 0; i < size; i++) arr[i] = value;
        return arr;
    }

    /**
     * Calcule la corrélation croisée maximale entre deux séries RSSI
     */
    public static double maxCrossCorrelation(double[] x, double[] y, int maxLag) {
        int n = x.length;
        double best = 0;

        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double num = 0, denX = 0, denY = 0;

            for (int i = 0; i < n; i++) {
                int j = i + lag;
                if (j < 0 || j >= n) continue;

                num += x[i] * y[j];
                denX += x[i] * x[i];
                denY += y[j] * y[j];
            }

            double corr = Math.abs(num) / (Math.sqrt(denX * denY) + 1e-9);
            if (corr > best) best = corr;
        }

        return best;
    }

    /**
     * Calcule la similarité globale BLE en combinant :
     * - Jaccard (présence)
     * - Corrélation croisée (forme du signal RSSI)
     */
    public static double computeSimilarity(
            List<BluetoothSignal> mobileSignals, long mobileStart,
            List<BluetoothSignal> browserSignals, long browserStart,
            int intervalMs, int totalMs, int maxLag) {

        // Présence : adresses détectées
        Set<String> mobAddrs = new HashSet<>();
        Set<String> webAddrs = new HashSet<>();

        for (BluetoothSignal s : mobileSignals) mobAddrs.add(s.getDeviceAddress());
        for (BluetoothSignal s : browserSignals) webAddrs.add(s.getDeviceAddress());

        Set<String> intersection = new HashSet<>(mobAddrs);
        intersection.retainAll(webAddrs);

        Set<String> union = new HashSet<>(mobAddrs);
        union.addAll(webAddrs);

        double presenceScore = union.isEmpty()
                ? 0.0
                : (double) intersection.size() / union.size();

        // Séries temporelles
        Map<String, double[]> mobSeries = buildTimeSeries(mobileSignals, mobileStart, intervalMs, totalMs);
        Map<String, double[]> webSeries = buildTimeSeries(browserSignals, browserStart, intervalMs, totalMs);

        // Corrélation croisée moyenne sur les adresses communes
        double corrSum = 0.0;
        int count = 0;

        for (String addr : mobSeries.keySet()) {
            if (webSeries.containsKey(addr)) {
                double[] x = mobSeries.get(addr);
                double[] y = webSeries.get(addr);
                double corr = maxCrossCorrelation(x, y, maxLag);
                corrSum += corr;
                count++;
            }
        }

        double correlationScore = (count > 0) ? corrSum / count : 0.0;

        // Logs
        Log.i("SBLE", String.format("BLE Scores — Presence: %.3f, Correlation: %.3f", presenceScore, correlationScore));

        // Score final : pondération possible (ici moyenne simple)
        double score = presenceScore;

        //double score = (presenceScore + correlationScore) / 2.0;

        return score;

    }


    public static void debugBLECorrelation(
            Context context,
            List<BluetoothSignal> mobileSignals, long mobileStart,
            List<BluetoothSignal> browserSignals, long browserStart,
            int intervalMs, int totalMs, int maxLag) {

        // Étape 1 : vérifier les adresses détectées
        Set<String> mobAddrs = new HashSet<>();
        Set<String> webAddrs = new HashSet<>();

        for (BluetoothSignal s : mobileSignals) mobAddrs.add(s.getDeviceAddress());
        for (BluetoothSignal s : browserSignals) webAddrs.add(s.getDeviceAddress());

        Set<String> intersection = new HashSet<>(mobAddrs);
        intersection.retainAll(webAddrs);

        Log.d("BLE_DEBUG", "🔍 Mob Addresses: " + mobAddrs.size());
        Log.d("BLE_DEBUG", "🔍 Web Addresses: " + webAddrs.size());
        Log.d("BLE_DEBUG", "🔍 Common Addresses: " + intersection.size());



        //Toast.makeText(context, "Signals Mobile: " + mobAddrs.size(), Toast.LENGTH_SHORT).show();
        //Toast.makeText(context, "Signals Web: " + webAddrs.size(), Toast.LENGTH_SHORT).show();
        //Toast.makeText(context, "intersection: " + intersection.size(), Toast.LENGTH_SHORT).show();

        // Étape 2 : construire les séries temporelles
        Map<String, double[]> mobSeries = buildTimeSeries(mobileSignals, mobileStart, intervalMs, totalMs);
        Map<String, double[]> webSeries = buildTimeSeries(browserSignals, browserStart, intervalMs, totalMs);

        // Étape 3 : afficher les séries
        for (String addr : intersection) {
            if (!mobSeries.containsKey(addr) || !webSeries.containsKey(addr)) {
                Log.w("BLE_DEBUG", "⚠️ Données manquantes pour l’adresse: " + addr);
                Toast.makeText(context, "données manquantes adresse: " + addr, Toast.LENGTH_SHORT).show();

                continue;
            }

            double[] x = mobSeries.get(addr);
            double[] y = webSeries.get(addr);

            Log.d("BLE_DEBUG", "📶 RSSI Mobile [" + addr + "]: " + java.util.Arrays.toString(x));
            Log.d("BLE_DEBUG", "📶 RSSI Web    [" + addr + "]: " + java.util.Arrays.toString(y));

            double corr = maxCrossCorrelation(x, y, maxLag);
            Log.d("BLE_DEBUG", "🔗 Corrélation croisée = " + corr);
            Toast.makeText(context, "Corrélation croisée: " + corr, Toast.LENGTH_SHORT).show();
        }

        Log.d("BLE_DEBUG", "✅ Fin du diagnostic BLE.");
    }

}
