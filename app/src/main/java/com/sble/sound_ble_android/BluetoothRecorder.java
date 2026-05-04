package com.example.sound_proof_android;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class BluetoothRecorder {
    private final Context context;
    private final BluetoothLeScanner scanner;
    private final Handler handler;
    private final List<BluetoothSignal> recordedSignals = new ArrayList<>();
    private ScanCallback callback;

    public BluetoothRecorder(Context context) {
        this.context = context;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            Log.e("DEBUG", "❌ Bluetooth désactivé ou indisponible !");
            scanner = null;
        } else {
            scanner = adapter.getBluetoothLeScanner();
        }

        handler = new Handler(Looper.getMainLooper());
    }

    public void startRecording(long durationMs, long startTimestamp) {
        if (scanner == null) {
            Log.e("DEBUG", "❌ Scanner BLE indisponible !");
            Toast.makeText(context, "Bluetooth désactivé ou indisponible !", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("DEBUG", "❌ Permission BLUETOOTH_SCAN manquante !");
            Toast.makeText(context, "Permission BLUETOOTH_SCAN refusée", Toast.LENGTH_LONG).show();
            return;
        }

        recordedSignals.clear();

        callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d("DEBUG", "✅ Signal détecté : " + result.getDevice().getAddress()
                        + " RSSI: " + result.getRssi());
                long ts = System.currentTimeMillis() - startTimestamp;
                recordedSignals.add(new BluetoothSignal(ts, result.getDevice().getAddress(), result.getRssi()));
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                long ts = System.currentTimeMillis() - startTimestamp;
                for (ScanResult r : results) {
                    recordedSignals.add(new BluetoothSignal(ts, r.getDevice().getAddress(), r.getRssi()));
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("DEBUG", "❌ Scan BLE échoué. Code : " + errorCode);
            }
        };

        Log.d("DEBUG", "🔄 Scan BLE démarré...");
        scanner.startScan(callback);

        handler.postDelayed(() -> {
            scanner.stopScan(callback);
            Log.d("DEBUG", "⏹️ Scan BLE terminé (timeout " + durationMs + " ms)");
            Log.d("DEBUG", "📊 Signaux captés : " + recordedSignals.size());

            if (recordedSignals.isEmpty()) {
                Toast.makeText(context, "⚠️ Aucun signal Bluetooth détecté", Toast.LENGTH_LONG).show();
            }
        }, durationMs);
    }

    public void stopRecording() {
        if (scanner != null && callback != null) {
            scanner.stopScan(callback);
        }
    }

    public List<BluetoothSignal> getRecordedSignals() {
        return recordedSignals;
    }
}
