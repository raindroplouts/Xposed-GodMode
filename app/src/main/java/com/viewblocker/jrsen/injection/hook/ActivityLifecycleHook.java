package com.viewblocker.jrsen.injection.hook;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.viewblocker.jrsen.injection.ViewController;
import com.viewblocker.jrsen.injection.util.Logger;
import com.viewblocker.jrsen.injection.util.Property;
import com.viewblocker.jrsen.rule.ActRules;
import com.viewblocker.jrsen.rule.ViewRule;
import com.viewblocker.jrsen.util.Preconditions;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Created by jrsen on 17-10-15.
 */

public final class ActivityLifecycleHook extends XC_MethodHook implements Property.OnPropertyChangeListener<ActRules> {

    private static final WeakHashMap<Activity, HierarchyObserver> sActivities = new WeakHashMap<>();
    private static final ActRules sActRules = new ActRules();

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        super.afterHookedMethod(param);
        Activity activity = (Activity) param.thisObject;
        String methodName = param.method.getName();
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        /*!!!这里有坑不要hook onCreate和onResume 因为getDecorView会执行installContentView的操作
         所以在Activity的子类中有可能去requestFeature会导致异常所以尽量找一个很靠后的生命周期函数*/
        if ("onPostResume".equals(methodName)) {
            if (!sActivities.containsKey(activity)) {
                HierarchyObserver observer = new HierarchyObserver(activity);
                observer.register(activity.getWindow().getDecorView());
                sActivities.put(activity, observer);
            }
            Logger.d("ActivityHook", "resume:" + sActivities);
        } else if ("onDestroy".equals(methodName)) {
            HierarchyObserver observer = sActivities.remove(activity);
            Logger.d("ActivityHook", "destroy:" + sActivities);
            if (observer != null) observer.unregister(activity.getWindow().getDecorView());
        }
    }

    @Override
    public void onPropertyChange(ActRules newActRules) {
        Set<Map.Entry<String, List<ViewRule>>> entries = newActRules.entrySet();
        for (Map.Entry<String, List<ViewRule>> entry : entries) {
            String key = entry.getKey();
            List<ViewRule> oldRules = sActRules.get(key);
            List<ViewRule> newRules = entry.getValue();
            if (newRules != null && oldRules != null) {
                oldRules.removeAll(newRules);
                if (oldRules.isEmpty()) sActRules.remove(key);
            }
        }
        //revoke old rules
        entries = sActRules.entrySet();
        for (Map.Entry<String, List<ViewRule>> entry : entries) {
            List<ViewRule> rules = entry.getValue();
            for (Activity activity : sActivities.keySet()) {
                if (TextUtils.equals(activity.getComponentName().getClassName(), entry.getKey())) {
                    ViewController.revokeRuleBatch(activity, rules);
                }
            }
        }
        //apply new rules
        sActRules.clear();
        sActRules.putAll(newActRules);
        entries = sActRules.entrySet();
        for (Map.Entry<String, List<ViewRule>> entry : entries) {
            List<ViewRule> rules = entry.getValue();
            for (Activity activity : sActivities.keySet()) {
                if (TextUtils.equals(activity.getComponentName().getClassName(), entry.getKey())) {
                    ViewController.applyRuleBatch(activity, rules);
                }
            }
        }
    }

    static final class HierarchyObserver implements ViewGroup.OnHierarchyChangeListener {

        final WeakReference<Activity> activityReference;

        HierarchyObserver(Activity activity) {
            activityReference = new WeakReference<>(activity);
        }

        @Override
        public void onChildViewAdded(View parent, View child) {
            Logger.d("ViewBlocker", "add view:" + child);
            register(child);
            applyRuleIfMatchCondition();
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
            Logger.d("ViewBlocker", "remove view:" + child);
            unregister(child);
            applyRuleIfMatchCondition();
        }

        void register(View view) {
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                viewGroup.setOnHierarchyChangeListener(this);
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    register(viewGroup.getChildAt(i));
                }
            }
        }

        void unregister(View view) {
            if (view instanceof ViewGroup) {
                ViewGroup childGroup = (ViewGroup) view;
                for (int i = 0; i < childGroup.getChildCount(); i++) {
                    onChildViewRemoved(childGroup, childGroup.getChildAt(i));
                }
                childGroup.setOnHierarchyChangeListener(null);
            }
        }

        private void applyRuleIfMatchCondition() {
            try {
                Activity activity = Preconditions.checkNotNull(activityReference.get());
                List<ViewRule> rules = Preconditions.checkNotNull(sActRules.get(activity.getComponentName().getClassName()));
                if (!rules.isEmpty()) {
                    ViewController.applyRuleBatch(activity, rules);
                }
            } catch (Exception ignore) {
//                ignore.printStackTrace();
            }
        }
    }

}
