package com.comeforfood.saferoute.feature.shared;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.fragment.app.Fragment;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import java.util.function.Consumer;

public final class MapsSupport {
    private MapsSupport() {}
    public static LatLng latLng(Point point) { return new LatLng(point.latitude, point.longitude); }
    public static boolean configured(Context context) {
        try {
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            String key = app.metaData == null ? null : app.metaData.getString("com.google.android.geo.API_KEY");
            return com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==com.google.android.gms.common.ConnectionResult.SUCCESS && key != null && !key.trim().isEmpty() && !key.contains("YOUR_API_KEY");
        } catch (PackageManager.NameNotFoundException | RuntimeException error) { return false; }
    }
    public static void attach(Fragment owner, int containerId, Consumer<GoogleMap> ready) {
        try {
        Fragment existing = owner.getChildFragmentManager().findFragmentById(containerId);
        SupportMapFragment fragment = existing instanceof SupportMapFragment ? (SupportMapFragment) existing : SupportMapFragment.newInstance();
        if (existing == null) owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment).commitNow();
        android.view.View ownerView = owner.requireView();
        fragment.getMapAsync(map -> {
            if (owner.getView() == ownerView) ready.accept(map);
        });
        } catch(RuntimeException error) { /* Keep the coordinate/list fallback when Maps cannot initialize. */ }
    }
}
