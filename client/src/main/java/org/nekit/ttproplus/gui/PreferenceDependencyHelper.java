package org.nekit.ttproplus.gui;

import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PreferenceDependencyHelper {

    public static class DependencyInfo {
        public final Preference dependentPref;
        public final PreferenceGroup parentGroup;
        public final int order;
        public final String masterKey;

        public DependencyInfo(Preference dependentPref, PreferenceGroup parentGroup, int order, String masterKey) {
            this.dependentPref = dependentPref;
            this.parentGroup = parentGroup;
            this.order = order;
            this.masterKey = masterKey;
        }
    }

    // Map of preferences currently scheduled for entrance animation -> delay in ms
    private static final Map<Preference, Long> pendingEntranceAnimations = new WeakHashMap<>();

    public static void onBindPreferenceView(Preference pref, View view) {
        if (pref == null || view == null) return;
        view.setTag(pref);

        Long delay = pendingEntranceAnimations.remove(pref);
        if (delay != null) {
            float density = view.getContext() != null ? view.getContext().getResources().getDisplayMetrics().density : 2.5f;
            float slideDistance = 45f * density;

            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setScaleY(0f);
            view.setPivotY(0f);
            view.setTranslationY(-slideDistance);

            view.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        } else {
            // Normal binding, ensure state is clean
            if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
            if (view.getAlpha() != 1f) view.setAlpha(1f);
            if (view.getScaleY() != 1f) view.setScaleY(1f);
            if (view.getTranslationY() != 0f) view.setTranslationY(0f);
        }
    }

    public static void setup(final PreferenceFragment fragment) {
        if (fragment == null || fragment.getActivity() == null) return;
        PreferenceScreen screen = fragment.getPreferenceScreen();
        if (screen == null) return;

        List<DependencyInfo> dependencies = new ArrayList<>();
        collectDependencies(screen, dependencies);

        if (dependencies.isEmpty()) return;

        // Group dependencies by masterKey
        final Map<String, List<DependencyInfo>> masterMap = new HashMap<>();
        for (DependencyInfo info : dependencies) {
            List<DependencyInfo> list = masterMap.get(info.masterKey);
            if (list == null) {
                list = new ArrayList<>();
                masterMap.put(info.masterKey, list);
            }
            list.add(info);
            // Clear framework dependency so Android doesn't disable/grey out while we hide
            info.dependentPref.setDependency(null);
        }

        for (final Map.Entry<String, List<DependencyInfo>> entry : masterMap.entrySet()) {
            final String masterKey = entry.getKey();
            final List<DependencyInfo> depList = entry.getValue();

            Preference masterPref = screen.findPreference(masterKey);
            if (masterPref instanceof TwoStatePreference) {
                final TwoStatePreference twoStateMaster = (TwoStatePreference) masterPref;
                boolean isChecked = twoStateMaster.isChecked();

                if (!isChecked) {
                    for (DependencyInfo info : depList) {
                        info.parentGroup.removePreference(info.dependentPref);
                    }
                }

                final Preference.OnPreferenceChangeListener existingListener = masterPref.getOnPreferenceChangeListener();
                masterPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (existingListener != null) {
                            if (!existingListener.onPreferenceChange(preference, newValue)) {
                                return false;
                            }
                        }
                        boolean enabled = Boolean.TRUE.equals(newValue);
                        onMasterToggled(fragment, depList, enabled);
                        return true;
                    }
                });
            }
        }
    }

    private static void collectDependencies(PreferenceGroup group, List<DependencyInfo> list) {
        group.setOrderingAsAdded(false);
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref.getOrder() == Preference.DEFAULT_ORDER) {
                pref.setOrder(i);
            }
            String depKey = pref.getDependency();
            if (depKey != null && !depKey.trim().isEmpty()) {
                list.add(new DependencyInfo(pref, group, pref.getOrder(), depKey.trim()));
            }
            if (pref instanceof PreferenceGroup) {
                collectDependencies((PreferenceGroup) pref, list);
            }
        }
    }

    private static void onMasterToggled(final PreferenceFragment fragment, final List<DependencyInfo> list, boolean enabled) {
        if (fragment.getActivity() == null || fragment.getActivity().isFinishing()) return;

        View root = fragment.getView();
        final ListView listView = root != null ? (ListView) root.findViewById(android.R.id.list) : null;

        if (enabled) {
            if (listView != null) {
                for (DependencyInfo info : list) {
                    View v = findViewForPreference(listView, info.dependentPref);
                    if (v != null) {
                        v.animate().cancel();
                    }
                }
            }
            // Expand: register pending entrance animations with waterfall stagger, then add to group
            long delay = 0;
            for (DependencyInfo info : list) {
                pendingEntranceAnimations.put(info.dependentPref, delay);
                delay += 40; // 40ms stagger between each sub-item
                info.dependentPref.setOrder(info.order);
                info.parentGroup.addPreference(info.dependentPref);
            }
        } else {
            // Collapse: smoothly fold up into master switch, then remove from group
            if (listView != null) {
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        animateCollapse(listView, list);
                    }
                });
            } else {
                for (DependencyInfo d : list) {
                    d.parentGroup.removePreference(d.dependentPref);
                }
            }
        }
    }

    private static void animateCollapse(final ListView listView, final List<DependencyInfo> list) {
        final List<View> viewsToAnimate = new ArrayList<>();
        for (DependencyInfo info : list) {
            View itemView = findViewForPreference(listView, info.dependentPref);
            if (itemView != null) {
                viewsToAnimate.add(itemView);
            }
        }

        if (viewsToAnimate.isEmpty()) {
            for (DependencyInfo d : list) {
                d.parentGroup.removePreference(d.dependentPref);
            }
            return;
        }

        final int count = viewsToAnimate.size();
        final AtomicInteger remaining = new AtomicInteger(count);
        float density = listView.getContext() != null ? listView.getContext().getResources().getDisplayMetrics().density : 2.5f;
        final float slideDistance = 45f * density;

        for (int i = 0; i < count; i++) {
            final View itemView = viewsToAnimate.get(i);
            itemView.setPivotY(0f);
            long delay = (count - 1 - i) * 30L; // reverse cascade: bottom up into switch!

            itemView.animate()
                    .alpha(0f)
                    .scaleY(0f)
                    .translationY(-slideDistance)
                    .setDuration(240)
                    .setStartDelay(delay)
                    .setInterpolator(new AccelerateInterpolator(1.8f))
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            itemView.setVisibility(View.INVISIBLE);
                            if (remaining.decrementAndGet() == 0) {
                                for (DependencyInfo d : list) {
                                    d.parentGroup.removePreference(d.dependentPref);
                                }
                            }
                        }
                    })
                    .start();
        }
    }

    private static View findViewForPreference(ListView listView, Preference pref) {
        if (listView == null || pref == null) return null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child != null && child.getTag() == pref) {
                return child;
            }
        }
        return null;
    }
}
