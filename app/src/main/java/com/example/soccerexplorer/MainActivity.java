package com.example.soccerexplorer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {

    private final NoticiasFragment noticiasFragment = new NoticiasFragment();
    private final PartidosFragment partidosFragment = new PartidosFragment();
    private Fragment mapFragment;
    private Fragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbarMain);
        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                abrirFragmento(noticiasFragment);
                return true;
            }

            if (item.getItemId() == R.id.nav_matches) {
                abrirFragmento(partidosFragment);
                return true;
            }

            if (item.getItemId() == R.id.nav_map) {
                if (mapFragment == null) {
                    mapFragment = PlaceholderFragment.newInstance(
                            getString(R.string.tab_map_placeholder_title),
                            getString(R.string.tab_map_placeholder_message)
                    );
                }
                abrirFragmento(mapFragment);
                return true;
            }

            if (item.getItemId() == R.id.nav_profile) {
                if (profileFragment == null) {
                    profileFragment = PlaceholderFragment.newInstance(
                            getString(R.string.tab_profile_placeholder_title),
                            getString(R.string.tab_profile_placeholder_message)
                    );
                }
                abrirFragmento(profileFragment);
                return true;
            }

            return false;
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void abrirFragmento(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
