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

    @Override public Tv1DataSource.Request reverse(Point point, Callback callback) {
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
                    Place bestPlace = null;
                    int bestPrecision = -1;
                    if (addresses != null) for (Address address : addresses) {
                        if (address == null || !address.hasLatitude() || !address.hasLongitude()) continue;
                        String label = reverseAddressLabel(address);
                        if (label == null || label.trim().isEmpty()) continue;
                        try {
                            Point resultPoint = new Point(address.getLatitude(), address.getLongitude());
                            int precision = (hasText(address.getSubThoroughfare()) ? 2 : 0)
                                    + (hasText(address.getThoroughfare()) ? 1 : 0);
                            if (bestPlace == null || precision > bestPrecision) {
                                bestPlace = new Place(label, resultPoint, precision > 0);
                                bestPrecision = precision;
                            }
                        } catch (IllegalArgumentException ignored) { }
                    }
                    List<Place> result = new ArrayList<>();
                    if (bestPlace != null) result.add(bestPlace);
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
                    geocoder.getFromLocation(point.latitude, point.longitude, 5, new Geocoder.GeocodeListener() {
                        @Override public void onGeocode(List<Address> addresses) { delivery.deliver(addresses, null); }
                        @Override public void onError(String message) { delivery.deliver(null, Failure.NETWORK); }
                    });
                } else {
                    LEGACY.execute(() -> {
                        if (finished.get()) return;
                        try { delivery.deliver(geocoder.getFromLocation(point.latitude, point.longitude, 5), null); }
                        catch (Exception error) { delivery.deliver(null, Failure.NETWORK); }
                    });
                }
            } catch (RuntimeException error) { delivery.deliver(null, Failure.NETWORK); }
        }
        return () -> { finished.set(true); main.removeCallbacks(timeout); };
    }

    private static String reverseAddressLabel(Address address) {
        String line = address.getAddressLine(0);
        if (line != null) line = line.trim();
        if (line != null && line.isEmpty()) line = null;
        String number = address.getSubThoroughfare();
        if (number != null) number = number.trim();
        if (number != null && number.isEmpty()) number = null;
        String street = address.getThoroughfare();
        if (street != null) street = street.trim();
        if (street != null && street.isEmpty()) street = null;
        String feature = address.getFeatureName();
        if (feature != null) feature = feature.trim();
        if (feature != null && feature.isEmpty()) feature = null;

        if (number != null) {
            if (line != null && line.toLowerCase(Locale.ROOT).contains(number.toLowerCase(Locale.ROOT)))
                return line;
            if (street != null) {
                if (line != null && line.toLowerCase(Locale.ROOT).contains(street.toLowerCase(Locale.ROOT)))
                    return number + " " + line;
                return line == null ? number + " " + street : number + " " + street + ", " + line;
            }
            if (line != null) return number + ", " + line;
            return feature == null ? number : number + ", " + feature;
        }
        if (line != null) return line;
        return feature;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
