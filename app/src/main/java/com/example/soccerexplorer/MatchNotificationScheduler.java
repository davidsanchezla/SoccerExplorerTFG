package com.example.soccerexplorer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class MatchNotificationScheduler {

    private static final String WORK_NAME_DAILY_MATCH_NOTIFICATION = "daily_match_notification";

    private MatchNotificationScheduler() {
    }

    public static void schedule(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                PartidoHoyWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();

        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueueUniquePeriodicWork(
                WORK_NAME_DAILY_MATCH_NOTIFICATION,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest
        );

        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(PartidoHoyWorker.class)
                .setConstraints(constraints)
                .build();
        workManager.enqueue(oneTimeWorkRequest);
    }
}
