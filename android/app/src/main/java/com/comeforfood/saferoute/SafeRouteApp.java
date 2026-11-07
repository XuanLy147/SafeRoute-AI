package com.comeforfood.saferoute;

import android.app.Application;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
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

    private com.comeforfood.saferoute.feature.shared.Tv2Screens tv2Screens =
            new com.comeforfood.saferoute.feature.shared.Tv2Screens() {
                @Override public androidx.fragment.app.Fragment report() { return com.comeforfood.saferoute.feature.report.ReportWizardFragment.forNavigation(); }
                @Override public androidx.fragment.app.Fragment profile() { return new com.comeforfood.saferoute.feature.profile.ProfileFragment(); }
            };
    public com.comeforfood.saferoute.feature.shared.Tv2Screens getTv2Screens() { return tv2Screens; }
    public void setTv2Screens(com.comeforfood.saferoute.feature.shared.Tv2Screens screens) {
        tv2Screens = Objects.requireNonNull(screens);
    }

    private com.comeforfood.saferoute.core.network.ApiClient api;
    public com.comeforfood.saferoute.core.network.ApiClient api() {
        if(api==null)api=new com.comeforfood.saferoute.core.network.ApiClient(this);
        return api;
    }
    private Tv1DataSource dataSource;
    public Tv1DataSource getDataSource() {
        if(dataSource==null)dataSource=new com.comeforfood.saferoute.core.network.NetworkDataSource(api().service);
        return dataSource;
    }
    /** Install once during app startup. UI tests install fixtures only in the test process. */
    public void setDataSource(Tv1DataSource source) { dataSource = Objects.requireNonNull(source); }
}
