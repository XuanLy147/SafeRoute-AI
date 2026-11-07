package com.comeforfood.saferoute.feature.routing;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the device geocoding provider. No extra API key or UI-thread network work. */
public final class AndroidAddressSearch implements AddressSearch {
    private final Context context;
    private static final ThreadPoolExecutor LEGACY = new ThreadPoolExecutor(0, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), runnable -> {
                Thread thread = new Thread(runnable, "address-lookup");
                thread.setDaemon(true);
                return thread;
            });
    public AndroidAddressSearch(Context context) { this.context = context.getApplicationContext(); }

    @Override public Tv1DataSource.Request search(String query, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean finished = new AtomicBoolean();
        Runnable timeout = () -> {
            if (finished.compareAndSet(false, true)) callback.failure(Failure.NETWORK);
        };
        main.postDelayed(timeout, 15000);
        class Delivery {
            void deliver(List<Address> addresses, Failure error) {
                main.post(() -> {
                    if (!finished.compareAndSet(false, true)) return;
                    main.removeCallbacks(timeout);
                    if (error != null) { callback.failure(error); return; }
                    List<Place> result = new ArrayList<>();
                    if (addresses != null) for (Address address : addresses) {
                        if (address == null || !address.hasLatitude() || !address.hasLongitude()) continue;
                        try {
                            String label = address.getAddressLine(0);
                            String feature = address.getFeatureName();
                            if (label != null && feature != null && !feature.trim().isEmpty() && !label.contains(feature))
                                label = feature + ", " + label;
                            if (label == null || label.trim().isEmpty()) label = address.getFeatureName();
                            if (label == null || label.trim().isEmpty()) label = query;
                            Point point = new Point(address.getLatitude(), address.getLongitude());
                            boolean duplicate = false;
                            for (Place existing : result) if (existing.point.sameAs(point) && existing.label.equals(label)) duplicate = true;
                            if (!duplicate) result.add(new Place(label, point));
                        } catch (IllegalArgumentException ignored) { }
                    }
                    callback.success(result);
                });
            }
        }
        Delivery delivery = new Delivery();
        if (!Geocoder.isPresent()) delivery.deliver(null, Failure.UNAVAILABLE);
        else {
            Geocoder geocoder = new Geocoder(context, Locale.forLanguageTag("vi-VN"));
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    geocoder.getFromLocationName(query, 5, new Geocoder.GeocodeListener() {
                        @Override public void onGeocode(List<Address> addresses) { delivery.deliver(addresses, null); }
                        @Override public void onError(String message) { delivery.deliver(null, Failure.NETWORK); }
                    });
                } else {
                    LEGACY.execute(() -> {
                        if (finished.get()) return;
                        try { delivery.deliver(geocoder.getFromLocationName(query, 5), null); }
                        catch (Exception error) { delivery.deliver(null, Failure.NETWORK); }
                    });
                }
            } catch (RuntimeException error) { delivery.deliver(null, Failure.NETWORK); }
        }
        return () -> { finished.set(true); main.removeCallbacks(timeout); };
    }
}
