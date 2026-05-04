// WavRecorder.java
package com.example.sound_proof_android;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class WavRecorder {
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 48000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;
    private int bufferSize;
    private Thread recordingThread;
    private boolean isRecording;
    private String outputPath;

    public WavRecorder(String path) {
        bufferSize = AudioRecord.getMinBufferSize(
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING) * 3;
        outputPath = path;
    }

    @SuppressLint("MissingPermission")
    public void startRecording() {
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);

        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
            recorder.startRecording();

            int sessionId = recorder.getAudioSessionId();
            try {
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(sessionId);
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(sessionId);
                }
            } catch (Exception e) {
                Log.w("WavRecorder", "AGC/NS init failed", e);
            }

            isRecording = true;
            recordingThread = new Thread(this::writeAudioDataToFile, "AudioRecorder Thread");
            recordingThread.start();
        } else {
            Log.e("WavRecorder", "AudioRecord init failed");
        }
    }

    private void writeAudioDataToFile() {
        byte[] data = new byte[bufferSize];
        String filename = getTempFilename();
        try (FileOutputStream os = new FileOutputStream(filename)) {
            while (isRecording) {
                int read = recorder.read(data, 0, bufferSize);
                if (read > 0 && read != AudioRecord.ERROR_INVALID_OPERATION) {
                    // Amplification brute : ×2
                    for (int i = 0; i < read; i += 2) {
                        short sample = (short) ((data[i] & 0xff) | (data[i+1] << 8));
                        sample = (short) Math.max(Short.MIN_VALUE,
                                Math.min(Short.MAX_VALUE, sample * 2));
                        data[i]   = (byte) (sample & 0xff);
                        data[i+1] = (byte) ((sample >> 8) & 0xff);
                    }
                    // Écrire uniquement les octets lus
                    os.write(data, 0, read);
                }
            }
        } catch (IOException e) {
            Log.e("WavRecorder", "writeAudioDataToFile failed", e);
        }
    }

    public void stopRecording() {
        if (recorder != null) {
            isRecording = false;
            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                recorder.stop();
            }
            recorder.release();
            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(), outputPath + "/soundproof.wav");
        deleteTempFile();
    }

    private String getTempFilename() {
        File dir = new File(outputPath, AUDIO_RECORDER_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        File temp = new File(outputPath, AUDIO_RECORDER_TEMP_FILE);
        if (temp.exists()) temp.delete();
        return dir.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE;
    }

    private void deleteTempFile() {
        new File(getTempFilename()).delete();
    }

    private void copyWaveFile(String inFile, String outFile) {
        try (FileInputStream in = new FileInputStream(inFile);
             FileOutputStream out = new FileOutputStream(outFile)) {
            long totalAudioLen = in.getChannel().size();
            long totalDataLen = totalAudioLen + 36;
            int channels = (RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO) ? 1 : 2;
            long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    RECORDER_SAMPLERATE, channels, byteRate);

            byte[] buf = new byte[bufferSize];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        } catch (IOException e) {
            Log.e("WavRecorder", "copyWaveFile failed", e);
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out,
                                     long totalAudioLen,
                                     long totalDataLen,
                                     long sampleRate,
                                     int channels,
                                     long byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * RECORDER_BPP / 8);
        header[33] = 0; header[34] = RECORDER_BPP; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
