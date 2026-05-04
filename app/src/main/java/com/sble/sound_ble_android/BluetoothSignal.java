 package com.example.sound_proof_android;

public class BluetoothSignal {
    private final long timestamp;
    private final String deviceAddress;
    private final int rssi;

    public BluetoothSignal(long timestamp, String deviceAddress, int rssi) {
        this.timestamp = timestamp;
        this.deviceAddress = deviceAddress;
        this.rssi = rssi;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public int getRssi() {
        return rssi;
    }
}
