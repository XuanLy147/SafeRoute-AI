package com.comeforfood.saferoute.feature.shared;

import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import java.util.List;

/**
 * Presentation boundary, NOT an HTTP/JSON contract.
 * TV2 supplies an adapter to the one shared Retrofit repository after TV3/TV5 API-LOCK.
 * Callbacks may run on any thread. An empty successful list means no results, not no connection.
 */
public interface Tv1DataSource {
    enum Failure { NOT_CONNECTED, NETWORK, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, INVALID_DATA, SERVER }
    interface Callback<T> { void success(T value); void failure(Failure error); }
    interface Request { void cancel(); }
    Request loadReports(Callback<List<Hazard>> callback);
    Request compare(Point origin, Point destination, Callback<RouteComparison> callback);
}
