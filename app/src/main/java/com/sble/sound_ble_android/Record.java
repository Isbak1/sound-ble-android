package com.example.sound_proof_android;

import android.content.Context;
import android.content.ContextWrapper;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class Record {

    Context context;
    WavRecorder wavRecorder;
    BluetoothRecorder bluetoothRecorder;
    TimeZone deviceTimeZone;
    AudioRecord recorder;
    MediaPlayer player;
    long recordStopTime;
    List<BluetoothSignal> recordedBluetoothSignals;

    public Record(Context context) {
        this.context = context;
        recorder = null;
        player = null;
        deviceTimeZone = Calendar.getInstance().getTimeZone();
        wavRecorder = new WavRecorder(getSoundRecordingPath());
        bluetoothRecorder = new BluetoothRecorder(context);
    }

    // Lance l’enregistrement audio + Bluetooth en synchro
    public void startRecording(long durationMs) {
        try {
            long startTimestamp = System.currentTimeMillis();
            Log.d("DEBUG", "startRecording() called, timestamp = " + startTimestamp);

            wavRecorder.startRecording();
            bluetoothRecorder.startRecording(durationMs, startTimestamp);

            Toast.makeText(context, "Recording Has Started", Toast.LENGTH_LONG).show();

            Thread.sleep(durationMs);
            stopRecording();
        } catch (Exception e) {
            Log.e("ERROR", "startRecording() failed", e);
        }
    }

    // Arrête l’enregistrement et récupère le temps de fin via HTTP
    public void stopRecording() {
        wavRecorder.stopRecording();
        bluetoothRecorder.stopRecording();
        recorder = null;

        Toast.makeText(context, "Recording Has Stopped.", Toast.LENGTH_LONG).show();

        calculateRecordStopTime();
    }

    // Lecture de l’enregistrement (test local)
    public void playRecording() {
        try {
            player = new MediaPlayer();
            player.setDataSource(getSoundRecordingPath() + "/soundproof.wav");
            player.prepare();
            player.start();
            Toast.makeText(context, "Playback Audio... Duration:" + player.getDuration(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(context, "Playback has failed", Toast.LENGTH_SHORT).show();
        }
    }

    // 🔗 Récupère l'heure serveur pour déterminer le temps d'arrêt
    private void calculateRecordStopTime() {
        Thread thread = new Thread(() -> {
            try {
                URL url = new URL("https://aa722bc9784d.ngrok-free.app/servertime");
                long requestTime = System.currentTimeMillis();
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                String stopServerTimeStr = readStream(in);
                long responseTime = System.currentTimeMillis();
                long latency = (responseTime - requestTime) / 2;
                double stopServerTime = Double.parseDouble(stopServerTimeStr);
                recordStopTime = latency + (long) stopServerTime;
                urlConnection.disconnect();
            } catch (Exception e) {
                Log.d("*** serverExceptionTag", "Server time exception: " + e);
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(is), 1000);
        for (String line = r.readLine(); line != null; line = r.readLine()) {
            sb.append(line);
        }
        is.close();
        return sb.toString();
    }

    public String getSoundRecordingPath() {
        ContextWrapper contextWrapper = new ContextWrapper(context.getApplicationContext());
        File audioDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        return audioDirectory.getPath();
    }

    public long getRecordStopTime() {
        return recordStopTime;
    }

    public List<BluetoothSignal> getRecordedBluetoothSignals() {
        return bluetoothRecorder.getRecordedSignals();
    }
}
