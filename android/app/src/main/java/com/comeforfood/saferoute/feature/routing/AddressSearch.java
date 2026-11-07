package com.comeforfood.saferoute.feature.routing;

import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
import java.util.List;

/** Place lookup boundary, separate from the reports/routes HTTP contract. */
public interface AddressSearch {
    final class Place {
        public final String label;
        public final Point point;
        public Place(String label, Point point) {
            this.label = java.util.Objects.requireNonNull(label);
            this.point = java.util.Objects.requireNonNull(point);
        }
    }
    enum Failure { UNAVAILABLE, NETWORK }
    interface Callback {
        void success(List<Place> places);
        void failure(Failure error);
    }
    Tv1DataSource.Request search(String query, Callback callback);
}
