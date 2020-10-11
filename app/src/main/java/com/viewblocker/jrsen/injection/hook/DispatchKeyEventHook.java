package com.viewblocker.jrsen.injection.hook;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import com.viewblocker.jrsen.injection.BlockerInjector;
import com.viewblocker.jrsen.injection.ViewHelper;
import com.viewblocker.jrsen.injection.util.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

import static com.viewblocker.jrsen.injection.BlockerInjector.TAG;

public final class DispatchKeyEventHook extends XC_MethodHook {

    private int activityHashCode = 0;

    private List<WeakReference<View>> viewTree = new ArrayList<>();
    private int currentViewIndex = 0;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (BlockerInjector.switchProp.get()) {
            Activity activity = (Activity) param.thisObject;
            int currentActivityHashCode = System.identityHashCode(activity);
            if (activityHashCode != currentActivityHashCode) {
                activityHashCode = currentActivityHashCode;
                viewTree.clear();
                viewTree.addAll(ViewHelper.getChildList(activity.getWindow().getDecorView()));
                currentViewIndex = 0;
            }
            param.setResult(dispatchKeyEvent(activity, (KeyEvent) param.args[0]));
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private long volumeUpKeyUpTime, volumeDownKeyUpTime;

    private boolean dispatchKeyEvent(Activity activity, KeyEvent keyEvent) {
        Logger.d(TAG, keyEvent.toString());
        int action = keyEvent.getAction();
        int keyCode = keyEvent.getKeyCode();
        if (action == KeyEvent.ACTION_UP) {
            keyEvent.startTracking();
            Logger.d(TAG, String.format("正在显示的Activity%s %d:", activity.toString(), currentViewIndex));
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                View view = viewTree.get(currentViewIndex++).get();
                if (view == null) return true;
                Rect location = ViewHelper.getViewLocationInWindow(view);


//                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
//                    volumeUpKeyUpTime = SystemClock.uptimeMillis();
//                } else {
//                    volumeDownKeyUpTime = SystemClock.uptimeMillis();
//                }
//                Logger.d(TAG, "diff time:" + Math.abs(volumeDownKeyUpTime - volumeUpKeyUpTime));
//                if (Math.abs(volumeDownKeyUpTime - volumeUpKeyUpTime) < 50L) {
//                    Logger.d(TAG, "爆炸");
//                    mHandler.removeCallbacks(mMark);
//                    volumeDownKeyUpTime = volumeUpKeyUpTime = 0;
//                    WeakReference<View> viewReference = viewTree.get(currentViewIndex);
//                    View view = viewReference.get();
//                    ViewGroup container = (ViewGroup) activity.getWindow().getDecorView();
//                    MirrorView mirrorView = MirrorView.clone(view);
//                    mirrorView.attachToContainer(container);
//                    final ParticleView particleView = new ParticleView(activity, 1000);
//                    particleView.attachToContainer(container);
//                    particleView.setOnAnimationListener(new ParticleView.OnAnimationListener() {
//                        @Override
//                        public void onAnimationStart(View animView, Animator animation) {
//                            animView.setVisibility(View.INVISIBLE);
//                        }
//
//                        @Override
//                        public void onAnimationEnd(View animView, Animator animation) {
//                            try {
//                                ((MirrorView) animView).detachFromContainer();
//                                particleView.detachFromContainer();
//                            } finally {
//                            }
//                        }
//                    });
//                    particleView.boom(mirrorView);
//                    return true;
//                }
//                currentViewIndex = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? Math.max(currentViewIndex - 1, 0) : Math.min(currentViewIndex + 1, viewTree.size() - 1);
//                mHandler.postDelayed(mMark, 100L);
            }
        }
        return true;
    }

    private Runnable mMark = new Runnable() {
        @Override
        public void run() {
            WeakReference<View> viewReference = viewTree.get(currentViewIndex);
            View view = viewReference.get();
            if (view != null) {
                view.setForeground(new ColorDrawable(Color.parseColor("#995BE4E9")));
            }
        }
    };

}
