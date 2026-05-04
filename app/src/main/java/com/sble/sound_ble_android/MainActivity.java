package com.example.sound_proof_android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.sound_proof_android.databinding.ActivityMainBinding;
import com.example.sound_proof_android.ui.home.HomeFragment;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import com.example.sound_proof_android.Record;
import com.example.sound_proof_android.Cryptography;
import com.example.sound_proof_android.SoundProcess;
import com.example.sound_proof_android.BluetoothRecorder;
import com.example.sound_proof_android.BluetoothSignal;
import com.example.sound_proof_android.BluetoothProcess;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private Record record;
    private BluetoothRecorder btRecorder;
    private long recordStartTime;
    private Handler handler;

    private TextView currentActionText;
    private boolean inProcess = false;

    private static final int REQUEST_PERMISSION_CODE = 200;

    double simThreshold = 0.07;
    private static final double BleThreshold = 0.2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                    1002);
        }


        // Init recorders and handler
        record = new Record(this);
        btRecorder = new BluetoothRecorder(this);
        handler = new Handler(Looper.getMainLooper());

        requestPermission();

        setSupportActionBar(binding.appBarMain.toolbar);
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_connect)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this,
                R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        currentActionText = findViewById(R.id.currentActionText);
    }

    /**
     * Demande les permissions nécessaires.
     */
    private void requestPermission() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[0]),
                    REQUEST_PERMISSION_CODE);
        }
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isLocationEnabled) {
            Toast.makeText(this, "⚠️ Active la localisation pour le Bluetooth BLE", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

    }

    /**
     * Long-polling HTTP pour démarrer l'enregistrement.
     */
    public void receiveRecordStartSignal() {
        inProcess = true;
        Log.i("MainActivity", "*** trying to receive record start signal ***");


        String url = "https://aa722bc9784d.ngrok-free.app/login/2farecordpolling";
        RequestQueue queue = Volley.newRequestQueue(this);

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey = keyStore.getCertificate("spKey").getPublicKey();
            JSONObject postData = new JSONObject();
            postData.put("key", "-----BEGIN PUBLIC KEY-----" +
                    Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT)
                            .replaceAll("\n", "") +
                    "-----END PUBLIC KEY-----");
            String mRequestBody = postData.toString();

            StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                    response -> {
                        if (response.equals("204")) {
                            currentActionText.setText("Waiting for start record signal...");
                            currentActionText.setTextColor(Color.YELLOW);
                            receiveRecordStartSignal();
                        } else if (response.equals("200")) {
                            currentActionText.setText("Recording...");
                            currentActionText.setTextColor(Color.MAGENTA);



                            record.startRecording(3000);
                            receiveBrowserAudio();
                        }
                    }, error -> Log.e("LOG_RESPONSE", error.toString())) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get bytes of %s using %s",
                                mRequestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };
            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                    30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            queue.add(stringRequest);
        } catch (KeyStoreException | CertificateException |
                 IOException | NoSuchAlgorithmException | JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Récupère et compare les données audio & Bluetooth du navigateur.
     */
    public void receiveBrowserAudio() {
        Log.i("MainActivity", "*** comparing sound and bluetooth ***");
        currentActionText.setText("Comparing Sound and Bluetooth...");
        currentActionText.setTextColor(Color.CYAN);

        String url = "https://aa722bc9784d.ngrok-free.app/login/2farecordingdata";
        RequestQueue queue = Volley.newRequestQueue(this);

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey = keyStore.getCertificate("spKey").getPublicKey();

            JSONObject postData = new JSONObject();
            postData.put("key", "-----BEGIN PUBLIC KEY-----" +
                    Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT)
                            .replaceAll("\n", "") +
                    "-----END PUBLIC KEY-----");

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                    Request.Method.POST, url, postData,
                    response -> {
                        try {
                            // AUDIO ------------------------
                            long browserStopTime = response.getLong("time");
                            String key = response.getString("key");
                            String iv = response.getString("iv");
                            String b64audio = response.getString("b64audio");

                            Log.d("DEBUG", "browserStopTime (web) = " + browserStopTime);
                           // Toast.makeText(this, "Browser time = " + browserStopTime, Toast.LENGTH_SHORT).show();
                          //  Toast.makeText(this, "Mobile time = " + record.getRecordStopTime(), Toast.LENGTH_SHORT).show();

                            Cryptography crypt = new Cryptography(MainActivity.this);
                            String decryptedAESkey = crypt.rsaDecrypt(Base64.decode(key, Base64.DEFAULT));
                            byte[] wavBytes = crypt.aesDecrypt(b64audio, decryptedAESkey, iv);
                            crypt.saveWav(wavBytes);

                            SoundProcess sp = new SoundProcess(
                                          MainActivity.this,
                                          record.getRecordStopTime(),  // stop mobile
                                          browserStopTime);           // stop web
                            double audioScore = sp.startProcess();

                            //Log.d("DEBUG", "Audio score = " + (audioScore));
                            Toast.makeText(this, "Audio Score = " + (audioScore), Toast.LENGTH_SHORT).show();

                            int lag = (int) Math.abs(((record.getRecordStopTime() - 3000) - browserStopTime));


                            if (lag > 1000) {
                                Toast.makeText(this, "Lag: " + lag + "\nLag is too high to compare", Toast.LENGTH_LONG).show();
                                postResultResponse(false);
                                return;
                            }


                            // BLUETOOTH ------------------------
                            List<BluetoothSignal> btMob = record.getRecordedBluetoothSignals();
                            long browserBluetoothTime = browserStopTime;
                            List<BluetoothSignal> btWeb = new ArrayList<>();

                            if (response.has("ble_data")) {
                                JSONArray bleArray = response.getJSONArray("ble_data");
                                for (int i = 0; i < bleArray.length(); i++) {
                                    JSONObject obj = bleArray.getJSONObject(i);
                                    String address = obj.getString("address");
                                    int rssi = obj.getInt("rssi");
                                    btWeb.add(new BluetoothSignal(browserBluetoothTime, address, rssi));
                                }
                            }

                            //Log.d("DEBUG", "Nombre de signaux Bluetooth Mobile = " + btMob.size());
                            //Log.d("DEBUG", "Nombre de signaux Bluetooth Web = " + btWeb.size());
                            //Toast.makeText(this, "Signals Mobile: " + btMob.size(), Toast.LENGTH_SHORT).show();
                            //Toast.makeText(this, "Signals Web: " + btWeb.size(), Toast.LENGTH_SHORT).show();

                            double btScore = BluetoothProcess.computeSimilarity(
                                    btMob, record.getRecordStopTime(),
                                    btWeb, browserBluetoothTime,
                                    100, 4000, 200);





                            Log.d("DEBUG", "Bluetooth score = " + btScore);
                            Toast.makeText(this, "Bluetooth Score = " + btScore, Toast.LENGTH_SHORT).show();

                            // FINAL DECISION ------------------------
                            double finalScore = (audioScore + btScore);

                            if(audioScore >= simThreshold && btScore >= BleThreshold){
                                //Toast.makeText(this, "Lag: " + lag + "\nSimilarity Score: " + finalScore , Toast.LENGTH_LONG).show();
                                System.out.println("Login Accepted - Similarity score passed.");
                                postResultResponse(true);
                            }else {
                                //Toast.makeText(this, "Lag: " + lag + "\nSimilarity Score: " + finalScore, Toast.LENGTH_LONG).show();
                                System.out.println("Login Rejected - Similarity score failed.");
                                postResultResponse(false);
                            }

                            //Log.d("DEBUG", "Final score = " + finalScore);
                            //Toast.makeText(this, "Final Score = " + finalScore, Toast.LENGTH_LONG).show();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    error -> Log.e("LOG_RESPONSE", "Volley error: " + error.toString())
            );

            jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                    25000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            queue.add(jsonObjectRequest);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /**
     * Envoie le résultat final au serveur.
     */
    public void postResultResponse(boolean loginStatus) {
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        String url = "https://aa722bc9784d.ngrok-free.app/login/2faresponse";

        if (loginStatus) {
            currentActionText.setText("Login Successful");
            currentActionText.setTextColor(Color.GREEN);
        } else {
            currentActionText.setText("Login Failed\n\nTry again");
            currentActionText.setTextColor(Color.RED);
        }

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            PublicKey publicKey = keyStore.getCertificate("spKey").getPublicKey();
            JSONObject postData = new JSONObject();
            postData.put("valid", String.valueOf(loginStatus));
            postData.put("key", "-----BEGIN PUBLIC KEY-----" +
                    Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT)
                            .replaceAll("\n", "") +
                    "-----END PUBLIC KEY-----");

            String mRequestBody = postData.toString();
            StringRequest stringRequest = new StringRequest(
                    Request.Method.POST, url,
                    response -> {
                        if (response.equals("200")) {
                            receiveRecordStartSignal();
                        }
                    },
                    error -> Log.e("LOG_RESPONSE", error.toString())) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return mRequestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get bytes of %s using %s",
                                mRequestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(
                        NetworkResponse response) {
                    return Response.success(
                            response == null ? "" : String.valueOf(response.statusCode),
                            HttpHeaderParser.parseCacheHeaders(response));
                }
            };
            requestQueue.add(stringRequest);
        } catch (KeyStoreException | CertificateException |
                 IOException | NoSuchAlgorithmException | JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean getInProcessStatus() {
        return inProcess;
    }

    public void setCurrentActionText(TextView text) {
        this.currentActionText = text;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }



    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(
                this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
