package com.comeforfood.saferoute;

import android.app.Application;
import com.comeforfood.saferoute.feature.shared.MapRoutingDataSource;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import java.util.List;
import java.util.Objects;

public class SafeRouteApp extends Application {
    private com.comeforfood.saferoute.feature.routing.AddressSearch addressSearch;
    public com.comeforfood.saferoute.feature.routing.AddressSearch getAddressSearch() {
        if (addressSearch == null) addressSearch = new com.comeforfood.saferoute.feature.routing.AndroidAddressSearch(this);
        return addressSearch;
    }
    public void setAddressSearch(com.comeforfood.saferoute.feature.routing.AddressSearch search) { addressSearch = Objects.requireNonNull(search); }

    private com.comeforfood.saferoute.feature.shared.FeatureScreens featureScreens =
            new com.comeforfood.saferoute.feature.shared.FeatureScreens() {
                @Override public androidx.fragment.app.Fragment report() { return com.comeforfood.saferoute.feature.report.ReportWizardFragment.forNavigation(); }
                @Override public androidx.fragment.app.Fragment profile() { return new com.comeforfood.saferoute.feature.profile.ProfileFragment(); }
            };
    public com.comeforfood.saferoute.feature.shared.FeatureScreens getFeatureScreens() { return featureScreens; }
    public void setFeatureScreens(com.comeforfood.saferoute.feature.shared.FeatureScreens screens) {
        featureScreens = Objects.requireNonNull(screens);
    }

    private com.comeforfood.saferoute.core.network.ApiClient api;
    public com.comeforfood.saferoute.core.network.ApiClient api() {
        if(api==null)api=new com.comeforfood.saferoute.core.network.ApiClient(this);
        return api;
    }
    private MapRoutingDataSource dataSource;
    public MapRoutingDataSource getDataSource() {
        if(dataSource==null)dataSource=new com.comeforfood.saferoute.core.network.NetworkDataSource(api().service);
        return dataSource;
    }
    /** Install once during app startup. UI tests install fixtures only in the test process. */
    public void setDataSource(MapRoutingDataSource source) { dataSource = Objects.requireNonNull(source); }
}
