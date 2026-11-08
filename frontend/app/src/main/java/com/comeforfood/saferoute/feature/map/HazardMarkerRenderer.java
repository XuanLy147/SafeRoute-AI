package com.comeforfood.saferoute.feature.map;

import android.content.Context;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.feature.shared.MapsSupport;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.util.ArrayList;
import java.util.List;

public final class HazardMarkerRenderer {
    private final List<Marker> markers = new ArrayList<>();
    public void render(Context context, GoogleMap map, List<Hazard> reports) {
        clear();
        for (Hazard report : reports) {
            if (report.status == Hazard.Status.REJECTED) continue;
            boolean verified = report.status == Hazard.Status.VERIFIED;
            Marker marker = map.addMarker(new MarkerOptions().position(MapsSupport.latLng(report.point))
                    .title(report.title).snippet(report.type.label + " · " + context.getString(verified ? R.string.verified : R.string.pending))
                    .icon(BitmapDescriptorFactory.defaultMarker(verified ? BitmapDescriptorFactory.HUE_AZURE : BitmapDescriptorFactory.HUE_ORANGE)));
            if (marker != null) { marker.setTag(report); markers.add(marker); }
        }
    }
    public void clear() { for (Marker marker : markers) marker.remove(); markers.clear(); }
}
