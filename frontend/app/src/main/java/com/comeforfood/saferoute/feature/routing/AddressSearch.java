package com.comeforfood.saferoute.feature.routing;

import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.MapRoutingDataSource;
import java.util.List;

/** Place lookup boundary, separate from the reports/routes HTTP contract. */
public interface AddressSearch {
    final class Place {
        public final String label;
        public final Point point;
        public final boolean streetAddress;
        public Place(String label, Point point) {
            this(label, point, false);
        }
        public Place(String label, Point point, boolean streetAddress) {
            this.label = java.util.Objects.requireNonNull(label);
            this.point = java.util.Objects.requireNonNull(point);
            this.streetAddress = streetAddress;
        }
    }
    enum Failure { UNAVAILABLE, NETWORK }
    interface Callback {
        void success(List<Place> places);
        void failure(Failure error);
    }
    MapRoutingDataSource.Request search(String query, Callback callback);
    default MapRoutingDataSource.Request reverse(Point point, Callback callback) {
        callback.failure(Failure.UNAVAILABLE);
        return () -> {};
    }
}
