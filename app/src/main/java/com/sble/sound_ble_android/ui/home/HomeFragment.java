package com.example.sound_proof_android.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.sound_proof_android.MainActivity;
import com.example.sound_proof_android.R;

import java.security.KeyStore;

public class HomeFragment extends Fragment {

    private TextView currentActionText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_home, container, false);
        currentActionText = v.findViewById(R.id.currentActionText);

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias("spKey")) {
                currentActionText.setText("Please connect with an account");
                currentActionText.setTextColor(Color.RED);

            } else {
                // On transmet le TextView à MainActivity pour éviter le NPE
                ((MainActivity) getActivity()).setCurrentActionText(currentActionText);

                currentActionText.setText("Waiting for start record signal...");
                currentActionText.setTextColor(Color.YELLOW);

                // On démarre le polling HTTP pour le signal de démarrage
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    ((MainActivity) getActivity()).receiveRecordStartSignal();
                } else {
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            1001);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return v;
    }
}
