package com.comeforfood.saferoute.feature.shared;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import java.util.function.Consumer;

public final class ForegroundLocation {
    private ForegroundLocation() {}
    public static boolean permitted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @SuppressLint("MissingPermission")
    public static CancellationTokenSource request(Context context, Consumer<Location> result) {
        CancellationTokenSource cancellation = new CancellationTokenSource();
        if (!permitted(context)) { result.accept(null); return cancellation; }
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(15000).setMaxUpdateAgeMillis(15000).build();
        try {
            LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancellation.getToken())
                    .addOnSuccessListener(location -> { if (!cancellation.getToken().isCancellationRequested()) result.accept(location); })
                    .addOnFailureListener(error -> { if (!cancellation.getToken().isCancellationRequested()) result.accept(null); });
        } catch (SecurityException error) { result.accept(null); }
        return cancellation;
    }
}
