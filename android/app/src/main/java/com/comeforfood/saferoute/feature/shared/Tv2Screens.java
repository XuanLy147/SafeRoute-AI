package com.comeforfood.saferoute.feature.shared;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** App-shell extension point. TV2 registers its own screens at application startup. */
public interface Tv2Screens {
    @Nullable default Fragment report() { return null; }
    @Nullable default Fragment profile() { return null; }
}
