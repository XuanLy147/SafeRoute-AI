package com.comeforfood.saferoute.feature.routing;

import android.content.Context;
import androidx.core.content.ContextCompat;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.feature.shared.MapsSupport;
import com.comeforfood.saferoute.feature.shared.Point;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Draw only coordinates supplied by the adapter. No interpolation or fallback straight line. */
public final class RoutePolylineRenderer {
    private final List<Polyline> polylines = new ArrayList<>();
    private final List<Marker> endpoints = new ArrayList<>();
    public void render(Context context, GoogleMap map, RouteComparison comparison, String selected,
                       Point origin, Point destination, Consumer<String> selection) {
        clear();
        float density = context.getResources().getDisplayMetrics().density;
        for (Route route : comparison.routes) {
            boolean active = route.id.equals(selected);
            PolylineOptions options = new PolylineOptions().color(ContextCompat.getColor(context,
                    active ? R.color.primary : R.color.route_alternative)).width((active ? 6 : 4) * density)
                    .zIndex(active ? 2 : 1).clickable(true).startCap(new RoundCap()).endCap(new RoundCap());
            for (Point point : route.points) options.add(MapsSupport.latLng(point));
            Polyline line = map.addPolyline(options); line.setTag(route.id); polylines.add(line);
        }
        addEndpoint(map, origin, context.getString(R.string.origin));
        addEndpoint(map, destination, context.getString(R.string.destination));
        map.setOnPolylineClickListener(line -> { if (line.getTag() instanceof String) selection.accept((String) line.getTag()); });
    }
    private void addEndpoint(GoogleMap map, Point point, String label) {
        if (point == null) return;
        Marker marker = map.addMarker(new MarkerOptions().position(MapsSupport.latLng(point)).title(label));
        if (marker != null) endpoints.add(marker);
    }
    public void fit(Context context, GoogleMap map, Route selected) {
        LatLngBounds.Builder bounds = LatLngBounds.builder();
        for (Point point : selected.points) bounds.include(MapsSupport.latLng(point));
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),
                Math.round(40 * context.getResources().getDisplayMetrics().density)));
    }
    public void clear() {
        for (Polyline line : polylines) line.remove();
        for (Marker marker : endpoints) marker.remove();
        polylines.clear(); endpoints.clear();
    }
}
