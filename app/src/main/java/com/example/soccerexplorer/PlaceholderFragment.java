package com.example.soccerexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_MESSAGE = "arg_message";

    public static PlaceholderFragment newInstance(String title, String message) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_placeholder, container, false);
        TextView tvTitle = view.findViewById(R.id.tvPlaceholderTitle);
        TextView tvMessage = view.findViewById(R.id.tvPlaceholderMessage);

        Bundle args = getArguments();
        if (args != null) {
            tvTitle.setText(args.getString(ARG_TITLE, ""));
            tvMessage.setText(args.getString(ARG_MESSAGE, ""));
        }

        return view;
    }
}
