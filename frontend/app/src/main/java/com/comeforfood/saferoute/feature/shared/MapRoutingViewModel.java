package com.comeforfood.saferoute.feature.shared;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.comeforfood.saferoute.SafeRouteApp;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.map.HazardFilter;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapRoutingViewModel extends AndroidViewModel {
    public static final class State<T> {
        public final boolean loading;
        public final T data;
        public final MapRoutingDataSource.Failure error;
        public final boolean requiresRefresh;
        private State(boolean loading, T data, MapRoutingDataSource.Failure error) {
            this(loading, data, error, false);
        }
        private State(boolean loading, T data, MapRoutingDataSource.Failure error, boolean requiresRefresh) {
            this.loading = loading; this.data = data; this.error = error;
            this.requiresRefresh = requiresRefresh;
        }
    }
    private final SavedStateHandle saved;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State<List<Hazard>>> reports = new MutableLiveData<>(new State<>(false, null, null));
    private final MutableLiveData<State<RouteComparison>> routes = new MutableLiveData<>(new State<>(false, null, null));
    private MapRoutingDataSource.Request reportsRequest, routeRequest;
    private int reportsGeneration, routeGeneration;
    private Runnable reportsTimeout, routeTimeout;
    public MapRoutingViewModel(@NonNull Application app, SavedStateHandle saved) { super(app); this.saved = saved; }
    public LiveData<State<List<Hazard>>> reports() { return reports; }
    public LiveData<State<RouteComparison>> routes() { return routes; }
    public String origin() { String s = saved.get("a"); return s == null ? "" : s; }
    public String destination() { String s = saved.get("b"); return s == null ? "" : s; }
    public void input(String a, String b) { saved.set("a", a); saved.set("b", b); saved.set("aLabel", a); saved.set("bLabel", b); }
    public String originText() { String value = saved.get("aLabel"); return value == null ? origin() : value; }
    public String destinationText() { String value = saved.get("bLabel"); return value == null ? destination() : value; }
    public void editEndpoints(String a, String b) {
        editEndpoint(true, a); editEndpoint(false, b);
    }
    private void editEndpoint(boolean first, String text) {
        String previous = first ? originText() : destinationText();
        if (previous.equals(text)) return;
        saved.set(first ? "aLabel" : "bLabel", text);
        String coordinates = "";
        try { Point.parse(text); coordinates = text; } catch (IllegalArgumentException ignored) {}
        saved.set(first ? "a" : "b", coordinates);
    }
    public void selectEndpoint(boolean first, String label, Point point) {
        saved.set(first ? "aLabel" : "bLabel", label);
        saved.set(first ? "a" : "b", point.format());
    }
    public void swapEndpoints() {
        String a = origin(), b = destination(), aLabel = originText(), bLabel = destinationText();
        saved.set("a", b); saved.set("b", a);
        saved.set("aLabel", bLabel); saved.set("bLabel", aLabel);
    }

    public String selectedRoute() { return saved.get("route"); }
    public void selectRoute(String id) { saved.set("route", id); }
    public HazardFilter.Category category() {
        String value = saved.get("category");
        return value == null ? HazardFilter.Category.ALL : HazardFilter.Category.valueOf(value);
    }
    public boolean verifiedOnly() { return Boolean.TRUE.equals(saved.get("verified")); }
    public void filters(HazardFilter.Category category, boolean verified) {
        saved.set("category", category.name()); saved.set("verified", verified);
    }
    private MapRoutingDataSource source() { return ((SafeRouteApp) getApplication()).getDataSource(); }

    public void refreshReports() {
        if (reportsRequest != null) reportsRequest.cancel();
        if (reportsTimeout != null) main.removeCallbacks(reportsTimeout);
        int generation = ++reportsGeneration;
        reports.setValue(new State<>(true, null, null));
        MapRoutingDataSource.Callback<List<Hazard>> callback = new MapRoutingDataSource.Callback<>() {
            @Override public void success(List<Hazard> value) {
                if (value == null || value.stream().anyMatch(java.util.Objects::isNull)) { failure(MapRoutingDataSource.Failure.INVALID_DATA); return; }
                List<Hazard> copy = Collections.unmodifiableList(new ArrayList<>(value));
                publish(new State<>(false, copy, null));
            }
            @Override public void failure(MapRoutingDataSource.Failure error) {
                publish(new State<>(false, null, error == null ? MapRoutingDataSource.Failure.SERVER : error));
            }
            private void publish(State<List<Hazard>> state) {
                main.post(() -> {
                    if (generation != reportsGeneration || !reports.getValue().loading) return;
                    main.removeCallbacks(reportsTimeout); reports.setValue(state);
                });
            }
        };
        reportsTimeout = () -> {
            if (generation == reportsGeneration && reports.getValue().loading) {
                reportsGeneration++;
                if (reportsRequest != null) reportsRequest.cancel();
                reports.setValue(new State<>(false, null, MapRoutingDataSource.Failure.NETWORK));
            }
        };
        main.postDelayed(reportsTimeout, 30000);
        try { reportsRequest = source().loadReports(callback); }
        catch (RuntimeException error) { callback.failure(MapRoutingDataSource.Failure.INVALID_DATA); }
    }

    public void compare() {
        Point a = Point.parse(origin()), b = Point.parse(destination());
        if (a.sameAs(b)) throw new IllegalArgumentException("Same endpoints");
        if (routeRequest != null) routeRequest.cancel();
        if (routeTimeout != null) main.removeCallbacks(routeTimeout);
        int generation = ++routeGeneration;
        selectRoute(null);
        routes.setValue(new State<>(true, null, null));
        MapRoutingDataSource.Callback<RouteComparison> callback = new MapRoutingDataSource.Callback<>() {
            @Override public void success(RouteComparison value) {
                if (value == null) { failure(MapRoutingDataSource.Failure.INVALID_DATA); return; }
                publish(new State<>(false, value, null));
            }
            @Override public void failure(MapRoutingDataSource.Failure error) {
                publish(new State<>(false, null, error == null ? MapRoutingDataSource.Failure.SERVER : error));
            }
            private void publish(State<RouteComparison> state) {
                main.post(() -> {
                    if (generation != routeGeneration || !routes.getValue().loading) return;
                    main.removeCallbacks(routeTimeout); routes.setValue(state);
                });
            }
        };
        routeTimeout = () -> {
            if (generation == routeGeneration && routes.getValue().loading) {
                routeGeneration++;
                if (routeRequest != null) routeRequest.cancel();
                routes.setValue(new State<>(false, null, MapRoutingDataSource.Failure.NETWORK));
            }
        };
        main.postDelayed(routeTimeout, 30000);
        try { routeRequest = source().compare(a, b, callback); }
        catch (RuntimeException error) { callback.failure(MapRoutingDataSource.Failure.INVALID_DATA); }
    }
    /** A successful server-side report/review update invalidates risk and all old route callbacks. */
    public void onReportsChanged() {
        routeGeneration++;
        if (routeRequest != null) routeRequest.cancel();
        routeRequest = null;
        if (routeTimeout != null) main.removeCallbacks(routeTimeout);
        selectRoute(null);
        routes.setValue(new State<>(false, null, null, true));
        refreshReports();
    }

    public void cancelRouteRequest() {
        if(routeRequest!=null&&routes.getValue()!=null&&routes.getValue().loading) {
            routeGeneration++;routeRequest.cancel();main.removeCallbacks(routeTimeout);
            routes.setValue(new State<>(false,null,null));
        }
    }
    @Override protected void onCleared() {
        reportsGeneration++; routeGeneration++;
        if (reportsRequest != null) reportsRequest.cancel();
        if (routeRequest != null) routeRequest.cancel();
        main.removeCallbacksAndMessages(null);
    }
}
