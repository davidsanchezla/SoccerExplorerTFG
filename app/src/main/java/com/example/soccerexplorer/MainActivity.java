package com.example.soccerexplorer;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //llamada a metodo subirEquiposAFirebase();

    }

    // region subida equipos base de datos con json
    /*
    // 1. El traductor de nombres de liga a IDs de Firebase
    private String obtenerIdLiga(String nombreLiga) {
        switch (nombreLiga) {
            case "LaLiga EA Sports": return "PD";
            case "Premier League": return "PL";
            case "Serie A": return "SA";
            case "Bundesliga": return "BL1";
            case "Ligue 1": return "FL1";
            case "Primeira Liga": return "PPL";
            case "Championship": return "ELC";
            case "Eredivisie": return "DED";
            case "Serie A Brasil": return "BSA";
            default: return "OTRA";
        }
    }

    // 2. El método que sube todo a la base de datos
    private void subirEquiposAFirebase() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String myToken = "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";

        try {
            // Leer el archivo desde assets
            InputStream is = getAssets().open("equipos.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            JSONArray jsonArray = new JSONArray(json);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String ligaId = obtenerIdLiga(obj.getString("liga"));
                String equipoId = obj.getString("imagen");
                String carpeta = obj.getString("carpeta");

                // URL con mi Token para el repositorio privado
                String urlImagen = "https://" + myToken + "@raw.githubusercontent.com/davidsanchezla/SoccerExplorerTFG/David/ImagenesTFG/" + carpeta + "/" + equipoId + ".png";

                Map<String, Object> equipo = new HashMap<>();
                equipo.put("nombre", obj.getString("nombre"));
                equipo.put("equipo", obj.getString("equipo"));
                equipo.put("ciudad", obj.getString("ciudad"));
                equipo.put("latitud", obj.getDouble("latitud"));
                equipo.put("longitud", obj.getDouble("longitud"));
                equipo.put("escudo", urlImagen);

                // Subida a la sub-colección equipos
                db.collection("competiciones").document(ligaId)
                        .collection("equipos").document(equipoId)
                        .set(equipo)
                        .addOnSuccessListener(aVoid -> android.util.Log.d("FIREBASE", "Subido OK: " + equipoId))
                        .addOnFailureListener(e -> android.util.Log.e("FIREBASE", "Error en: " + equipoId, e));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
    // endregion

}