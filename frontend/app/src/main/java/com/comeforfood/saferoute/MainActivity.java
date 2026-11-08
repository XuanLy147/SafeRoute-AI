package com.comeforfood.saferoute;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.fragment.app.*;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.databinding.ActivityMainBinding;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.auth.*;
import com.comeforfood.saferoute.feature.map.MapFragment;
import com.comeforfood.saferoute.feature.routing.*;
import com.comeforfood.saferoute.feature.report.ReportWizardFragment;
import com.comeforfood.saferoute.feature.reports.MyReportsFragment;
import com.comeforfood.saferoute.feature.profile.ProfileFragment;
import com.comeforfood.saferoute.feature.shared.*;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    public static final String REPORTS_CHANGED = "saferoute.reports.changed";
    private static final String TAB_TAG = "saferoute.tab.";
    private ActivityMainBinding binding;
    private boolean keyboardVisible;
    private String menuRole = "";
    private boolean hadSession, checkingSession;
    private int pendingDestination;
    private SessionManager session;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null)pendingDestination=saved.getInt("auth.destination",0);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime()); syncNavigation();
            return WindowInsetsCompat.CONSUMED;
        });
        binding.navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_role_home) { openRoleRoot(isAdmin()?FlowPageFragment.Page.ADMIN_HOME:FlowPageFragment.Page.REVIEW_HOME,id);return true; }
            if (id == R.id.nav_role_reports) { openRoleRoot(isAdmin()?FlowPageFragment.Page.ADMIN_REPORTS:FlowPageFragment.Page.REVIEW_QUEUE,id);return true; }
            if (id == R.id.nav_role_history) { openRoleRoot(isAdmin()?FlowPageFragment.Page.ADMIN_AUDIT:FlowPageFragment.Page.REVIEW_HISTORY,id);return true; }
            if (id == R.id.nav_role_users) { openRoleRoot(FlowPageFragment.Page.ADMIN_USERS,id);return true; }
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
            if (id == R.id.nav_map) {
                if (!(current instanceof MapFragment)) openRoot(new MapFragment(), id);
                return true;
            }
            if (id == R.id.nav_my_reports) { openRoot(new MyReportsFragment(), id); return true; }
            FeatureScreens screens = ((SafeRouteApp)getApplication()).getFeatureScreens();
            Fragment target = id == R.id.nav_report ? screens.report() : id == R.id.nav_profile ? screens.profile() : null;
            if (target == null) { Snackbar.make(binding.getRoot(), R.string.coming_later, Snackbar.LENGTH_LONG).show(); return false; }
            openRoot(target, id); return true;
        });
        binding.navigation.setOnItemReselectedListener(item -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0)
                getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        });
        getSupportFragmentManager().setFragmentResultListener(REPORTS_CHANGED, this, (key,result) ->
                new ViewModelProvider(this).get(MapRoutingViewModel.class).onReportsChanged());
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncNavigation);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentResumed(FragmentManager manager, Fragment fragment) { syncNavigation(); }
        }, false);
        session=new SessionManager(this);
        hadSession=session.isLoggedIn();
        session.changes().observe(this, ignored -> {
            if(getSupportFragmentManager().isStateSaved())return;
            if(hadSession && !session.isLoggedIn()) {
                new ViewModelProvider(this).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).clear();
                LoginFragment login=new LoginFragment();Bundle args=new Bundle();args.putBoolean("expired",true);login.setArguments(args);
                openRoot(login,R.id.nav_profile);
            } else if(session.isLoggedIn()) {
                Fragment current=getSupportFragmentManager().findFragmentById(R.id.content);
                if(session.isLoggedIn()&&!session.isVerified() && !(current instanceof SessionGateFragment)) {
                    openRoot(new SessionGateFragment(),R.id.nav_profile);
                } else if(current instanceof com.comeforfood.saferoute.feature.admin.AdminUsersFragment && !isAdmin())openSessionHome();
                if(current instanceof com.comeforfood.saferoute.feature.review.ModerationFragment && !((com.comeforfood.saferoute.feature.review.ModerationFragment)current).allowed(session))openSessionHome();
                if(current instanceof FlowPageFragment) {
                    FlowPageFragment.Page page=((FlowPageFragment)current).page();
                    if(page.restricted() && (!session.isReviewerOrAdmin() || (page.admin()&&!isAdmin())))openSessionHome();
                }
            }
            hadSession=session.isLoggedIn();syncNavigation();
        });
        if (saved == null) {
            if(session.isLoggedIn())openRoot(new SessionGateFragment(),R.id.nav_profile);
            else if(getPreferences(MODE_PRIVATE).getBoolean("intro.complete",false))openRoot(new LoginFragment(),R.id.nav_profile);
            else openRoot(new WelcomeFragment(),R.id.nav_profile);
        } else if(session.isLoggedIn()&&!session.isVerified())openRoot(new SessionGateFragment(),R.id.nav_profile);
    }
    @Override protected void onStart() {
        super.onStart();
        Fragment current=getSupportFragmentManager().findFragmentById(R.id.content);
        if(session!=null && session.isVerified() && current!=null && !(current instanceof SessionGateFragment))refreshSession();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("auth.destination",pendingDestination);super.onSaveInstanceState(out);
    }
    public void refreshSession() {
        if(checkingSession || !session.isLoggedIn())return;
        checkingSession=true;
        String token=session.getToken();long generation=session.generation();
        com.comeforfood.saferoute.core.network.ApiCalls.enqueueDetailed(this,((SafeRouteApp)getApplication()).api().service.me(),p->{
            checkingSession=false;
            if(SessionManager.knownRole(p.getRole()))session.saveIfUnchanged(generation,token,p.getEmail(),p.getRole(),p.getFullName());
        },(code,error)->{ checkingSession=false; /* 401 is handled centrally; offline keeps credentials. */ });
    }
    public void requireSignIn() {
        openRoot(new LoginFragment(),R.id.nav_profile);
    }

    private void openRoot(Fragment fragment, int tab) {
        SessionManager currentSession=new SessionManager(this);
        if((fragment instanceof ReportWizardFragment || fragment instanceof MyReportsFragment)
                && !currentSession.isVerified()) {
            pendingDestination=tab;
            fragment=currentSession.isLoggedIn()?new SessionGateFragment():new LoginFragment();tab=R.id.nav_profile;
        }
        getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment, TAB_TAG + tab).commit();
    }
    private boolean isAdmin() { return new SessionManager(this).isReviewerOrAdmin() && "ADMIN".equalsIgnoreCase(new SessionManager(this).getRole()); }
    public void openSessionHome() {
        SessionManager session = new SessionManager(this);
        if(session.isLoggedIn()&&!session.isVerified()){openRoot(new SessionGateFragment(),R.id.nav_profile);return;}
        if(session.isVerified() && pendingDestination!=0) {
            int destination=pendingDestination;pendingDestination=0;
            if(destination==R.id.nav_report){openRoot(ReportWizardFragment.forNavigation(),destination);return;}
            if(destination==R.id.nav_my_reports){openRoot(new MyReportsFragment(),destination);return;}
        }
        if (session.isReviewerOrAdmin()) openRoleRoot(isAdmin()?FlowPageFragment.Page.ADMIN_HOME:FlowPageFragment.Page.REVIEW_HOME,R.id.nav_role_home);
        else openMap();
    }
    private Fragment rolePage(FlowPageFragment.Page page) {
        if(com.comeforfood.saferoute.feature.review.ModerationFragment.supports(page))return com.comeforfood.saferoute.feature.review.ModerationFragment.at(page);
        return page==FlowPageFragment.Page.ADMIN_USERS||page==FlowPageFragment.Page.ADMIN_ROLES
            ?new com.comeforfood.saferoute.feature.admin.AdminUsersFragment():FlowPageFragment.at(page);
    }
    private void openRoleRoot(FlowPageFragment.Page page,int tab) { openRoot(rolePage(page),tab); }
    public void openFlow(FlowPageFragment.Page page) { openFeatureFragment(rolePage(page)); }
    public void logout() { pendingDestination=0; new ViewModelProvider(this).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).clear();hadSession=false;new SessionManager(this).clearSession();openRoot(new LoginFragment(),R.id.nav_profile); }
    public void openMapAt(Point point) {
        MapFragment fragment=new MapFragment();Bundle b=new Bundle();b.putDouble("center.lat",point.latitude);b.putDouble("center.lon",point.longitude);fragment.setArguments(b);openRoot(fragment,R.id.nav_map);
    }
    public void openMap() { openRoot(new MapFragment(), R.id.nav_map); }
    public void finishIntroduction() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("intro.complete", true).apply();
        openRoot(new LoginFragment(), R.id.nav_profile);
    }
    public void openRoutes(Point destination) {
        if (destination != null) new ViewModelProvider(this).get(MapRoutingViewModel.class).selectEndpoint(false, destination.format(), destination);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new RouteSearchFragment(), TAB_TAG + R.id.nav_map)
                .addToBackStack("route-search").commit();
    }
    public void openResults() {
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new RouteResultFragment(), TAB_TAG + R.id.nav_map)
                .addToBackStack("route-results").commit();
    }
    public void openFeatureFragment(Fragment fragment) {
        if (fragment instanceof ProfileFragment) { openRoot(fragment, R.id.nav_profile); return; }
        if (fragment instanceof ReportWizardFragment) { openRoot(fragment, R.id.nav_report); return; }
        if (fragment instanceof MyReportsFragment) {
            Bundle args=fragment.getArguments()==null?new Bundle():fragment.getArguments();
            args.putBoolean("return.profile",getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
            fragment.setArguments(args);openRoot(fragment, R.id.nav_my_reports);return;
        }
        int tab = binding.navigation.getSelectedItemId();
        getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment, TAB_TAG + tab)
                .addToBackStack("detail").commit();
    }
    public void navigateBack() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) getSupportFragmentManager().popBackStack();
        else if (getSupportFragmentManager().findFragmentById(R.id.content) instanceof com.comeforfood.saferoute.feature.review.ModerationFragment) openSessionHome();
        else if (getSupportFragmentManager().findFragmentById(R.id.content) instanceof FlowPageFragment && ((FlowPageFragment)getSupportFragmentManager().findFragmentById(R.id.content)).page().admin() && isAdmin()) openSessionHome();
        else if (getSupportFragmentManager().findFragmentById(R.id.content) instanceof MyReportsFragment
                && getSupportFragmentManager().findFragmentById(R.id.content).getArguments()!=null
                && getSupportFragmentManager().findFragmentById(R.id.content).getArguments().getBoolean("return.profile")) openFeatureFragment(new ProfileFragment());
        else if (getSupportFragmentManager().findFragmentById(R.id.content) instanceof LoginFragment) openRoot(new WelcomeFragment(),R.id.nav_profile);
        else openMap();
    }
    private void syncNavigation() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
        SessionManager session = new SessionManager(this);
        String role = session.isReviewerOrAdmin() ? (isAdmin()?"ADMIN":"REVIEWER") : "USER";
        if (!role.equals(menuRole)) {
            menuRole=role;binding.navigation.getMenu().clear();
            if ("USER".equals(role)) binding.navigation.inflateMenu(R.menu.main_navigation);
            else {
                android.view.Menu menu=binding.navigation.getMenu();
                menu.add(0,R.id.nav_role_home,0,isAdmin()?R.string.tab_admin:R.string.tab_overview).setIcon(R.drawable.ic_person);
                menu.add(0,R.id.nav_role_reports,1,isAdmin()?R.string.nav_report:R.string.tab_queue).setIcon(R.drawable.ic_report);
                menu.add(0,R.id.nav_role_history,2,isAdmin()?R.string.tab_audit:R.string.tab_history).setIcon(R.drawable.ic_history);
                menu.add(0,isAdmin()?R.id.nav_role_users:R.id.nav_map,3,isAdmin()?R.string.nav_account:R.string.nav_map).setIcon(isAdmin()?R.drawable.ic_person:R.drawable.ic_map);
            }
        }
        boolean fullScreen = current instanceof WelcomeFragment || current instanceof OnboardingFragment
                || current instanceof LoginFragment || current instanceof RegisterFragment || current instanceof SessionGateFragment || current instanceof ReportWizardFragment
                || (current instanceof FlowPageFragment && ((FlowPageFragment)current).isDecision());
        binding.navigation.setVisibility(keyboardVisible || fullScreen ? View.GONE : View.VISIBLE);
        if (current == null) return;
        String tag = current.getTag();
        if (tag != null && tag.startsWith(TAB_TAG)) {
            try {
                android.view.MenuItem item = binding.navigation.getMenu().findItem(Integer.parseInt(tag.substring(TAB_TAG.length())));
                if (item != null) item.setChecked(true);
            } catch (NumberFormatException ignored) {}
        }
    }
}
