package com.comeforfood.saferoute.feature.shared;

import androidx.fragment.app.Fragment;
import com.comeforfood.saferoute.MainActivity;
import com.comeforfood.saferoute.R;
import com.comeforfood.saferoute.feature.auth.LoginFragment;
import com.comeforfood.saferoute.feature.auth.RegisterFragment;
import com.comeforfood.saferoute.feature.report.ReportWizardFragment;
import com.comeforfood.saferoute.feature.reports.MyReportsFragment;
import com.comeforfood.saferoute.feature.profile.ProfileFragment;
import com.comeforfood.saferoute.feature.review.ReviewerFragment;
import com.comeforfood.saferoute.feature.community.CommunityFeedbackFragment;

/** Adapts TV2 destinations to TV1's single activity/back stack. */
public final class Tv2Navigation {
    private Tv2Navigation() {}
    public static void back(Fragment from) {
        ((MainActivity) from.requireActivity()).navigateBack();
    }
    public static void navigate(Fragment from, int action) {
        Fragment target;
        if (action == R.id.action_welcome_to_login) target = new LoginFragment();
        else if (action == R.id.action_login_to_register) target = new RegisterFragment();
        else if (action == R.id.action_register_to_login) { back(from); return; }
        else if (action == R.id.action_login_to_report) {
            if(new com.comeforfood.saferoute.core.session.SessionManager(from.requireContext()).isLoggedIn())
                ((MainActivity)from.requireActivity()).openSessionHome();
            return;
        }
        else if (action == R.id.action_report_to_profile || action == R.id.action_myReports_to_profile) target = new ProfileFragment();
        else if (action == R.id.action_report_to_myReports || action == R.id.action_profile_to_myReports) target = new MyReportsFragment();
        else if (action == R.id.action_report_to_reviewer || action == R.id.action_profile_to_reviewer) {
            if(new com.comeforfood.saferoute.core.session.SessionManager(from.requireContext()).isReviewerOrAdmin()) ((MainActivity)from.requireActivity()).openSessionHome();
            else ((MainActivity)from.requireActivity()).openTv2Fragment(new ReviewerFragment());
            return;
        }
        else if (action == R.id.action_profile_to_feedback || action == R.id.action_report_to_feedback) target = new CommunityFeedbackFragment();
        else throw new IllegalArgumentException("Unknown TV2 navigation action");
        ((MainActivity) from.requireActivity()).openTv2Fragment(target);
    }
}
