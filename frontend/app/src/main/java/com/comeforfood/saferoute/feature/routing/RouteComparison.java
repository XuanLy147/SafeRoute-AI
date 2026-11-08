package com.comeforfood.saferoute.feature.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The source supplies the recommendation and score description; the UI never invents either. */
public final class RouteComparison {
    public final List<Route> routes;
    public final String recommendedId, scoreDescription, recommendationBasis;
    public RouteComparison(List<Route> routes, String recommendedId, String scoreDescription) {
        this(routes,recommendedId,scoreDescription,"");
    }
    public RouteComparison(List<Route> routes, String recommendedId, String scoreDescription, String basis) {
        recommendationBasis = basis == null ? "" : basis;
        if (routes == null) throw new IllegalArgumentException("Missing routes");
        Set<String> ids = new HashSet<>();
        for (Route route : routes)
            if (route == null || !ids.add(route.id)) throw new IllegalArgumentException("Duplicate route");
        if (recommendedId != null && !ids.contains(recommendedId))
            throw new IllegalArgumentException("Unknown recommendation");
        this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
        this.recommendedId = recommendedId;
        this.scoreDescription = scoreDescription == null ? "" : scoreDescription;
    }
    public Route initialSelection() {
        for (Route route : routes) if (route.id.equals(recommendedId)) return route;
        return routes.isEmpty() ? null : routes.get(0);
    }
}
