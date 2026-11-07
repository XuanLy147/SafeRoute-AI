package com.comeforfood.saferoute;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.databinding.ActivityMainBinding;
import com.comeforfood.saferoute.feature.map.MapFragment;
import com.comeforfood.saferoute.feature.routing.RouteSearchFragment;
import com.comeforfood.saferoute.feature.routing.RouteResultFragment;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1ViewModel;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    /** TV2 emits this on the activity FragmentManager after a successful report/review write. */
    public static final String REPORTS_CHANGED = "saferoute.reports.changed";
    private static final String TAB_TAG = "saferoute.tab.";
    private ActivityMainBinding binding;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            binding.navigation.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return WindowInsetsCompat.CONSUMED;
        });
        binding.navigation.setOnItemSelectedListener(item -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
            if (item.getItemId() == R.id.nav_map) {
                if (!(current instanceof MapFragment)) openRoot(new MapFragment(), R.id.nav_map);
                return true;
            }
            if (item.getItemId() == R.id.nav_routes) {
                if (!(current instanceof RouteSearchFragment) && !(current instanceof RouteResultFragment))
                    openRoot(new RouteSearchFragment(), R.id.nav_routes);
                return true;
            }
            com.comeforfood.saferoute.feature.shared.Tv2Screens screens =
                    ((SafeRouteApp) getApplication()).getTv2Screens();
            Fragment destination = item.getItemId() == R.id.nav_report ? screens.report()
                    : item.getItemId() == R.id.nav_profile ? screens.profile() : null;
            if (destination != null) {
                openRoot(destination, item.getItemId());
                return true;
            }
            Snackbar.make(binding.getRoot(), R.string.coming_later, Snackbar.LENGTH_LONG).show();
            return false;
        });
        binding.navigation.setOnItemReselectedListener(item -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0)
                getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        });
        getSupportFragmentManager().setFragmentResultListener(REPORTS_CHANGED, this, (key, result) ->
                new ViewModelProvider(this).get(Tv1ViewModel.class).onReportsChanged());
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncNavigation);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentResumed(FragmentManager manager, Fragment fragment) { syncNavigation(); }
        }, false);
        if (savedInstanceState == null) openRoot(new MapFragment(), R.id.nav_map);
    }
    private void openRoot(Fragment fragment, int tabId) {
        getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment, TAB_TAG + tabId).commit();
    }
    public void openRoutes(Point destination) {
        if (destination != null) {
            Tv1ViewModel model = new ViewModelProvider(this).get(Tv1ViewModel.class);
            model.selectEndpoint(false, destination.format(), destination);
        }
        openRoot(new RouteSearchFragment(), R.id.nav_routes);
    }
    public void openResults() {
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new RouteResultFragment(), TAB_TAG + R.id.nav_routes)
                .addToBackStack("route-results").commit();
    }
    public void openTv2Fragment(Fragment fragment) {
        if (fragment instanceof com.comeforfood.saferoute.feature.profile.ProfileFragment) {
            openRoot(fragment, R.id.nav_profile); return;
        }
        if (fragment instanceof com.comeforfood.saferoute.feature.report.ReportWizardFragment) {
            openRoot(fragment, R.id.nav_report); return;
        }
        int tab = binding.navigation.getSelectedItemId();
        getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment, TAB_TAG + tab)
                .addToBackStack("tv2-detail").commit();
    }
    public void navigateBack() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0)
            getSupportFragmentManager().popBackStack();
        else openRoot(new MapFragment(), R.id.nav_map);
    }
    private void syncNavigation() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
        if (current == null) return;
        int tabId = current instanceof MapFragment ? R.id.nav_map : R.id.nav_routes;
        String tag = current.getTag();
        if (tag != null && tag.startsWith(TAB_TAG)) {
            try { tabId = Integer.parseInt(tag.substring(TAB_TAG.length())); }
            catch (NumberFormatException ignored) { return; }
        }
        android.view.MenuItem item = binding.navigation.getMenu().findItem(tabId);
        if (item != null) item.setChecked(true);
    }
}
