/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import android.provider.Settings;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private Drawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    private Drawable mRecentIcon;
    private Drawable mRecentLandIcon;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mLayoutTransitionsEnabled;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = true;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = false;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker(true /* showAuxiliarySubtypes */);
        }
    };

    /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
    private final OnClickListener mNotiBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if(mBar != null) {
                mBar.onNotiBarClick();
            }
        }
    };

    private final OnClickListener mHideBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
//           Settings.Global.putInt(mContext.getContentResolver(), "hided", 1);
//           /*getWindow().getDecorView().*/setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            Intent intent = new Intent("com.action.hide_navigationbar");
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            mContext.sendBroadcast(intent);
         }
    };
    /* @} */

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }
    /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
    private int mLastNavBarMode;
    private PhoneStatusBar mBar;
    private final ContentObserver mNavigationSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateNavigationbarViews();
        }
    };
    /* @} */

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getContext().getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);

        getIcons(res);

        mBarTransitions = new NavigationBarTransitions(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
        /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
        if(PhoneStatusBar.mSupportDynamicBar) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("navigationbar_config"),
                    true,
                    mNavigationSettingsObserver);
        }
        /* @} */
    }

    /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(PhoneStatusBar.mSupportDynamicBar) {
            mContext.getContentResolver().unregisterContentObserver(mNavigationSettingsObserver);
        }
    }
    /* @} */

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setBar(PhoneStatusBar phoneStatusBar) {
        /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
        if(PhoneStatusBar.mSupportDynamicBar) {
            mBar = phoneStatusBar;
        }
        /* @} */
        mTaskSwitchHelper.setBar(phoneStatusBar);
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mTaskSwitchHelper.onInterceptTouchEvent(event);
    }

    public void abortCurrentGesture() {
        getHomeButton().abortCurrentGesture();
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewById(R.id.recent_apps);
    }

    public View getMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getBackButton() {
        return mCurrentView.findViewById(R.id.back);
    }

    public KeyButtonView getHomeButton() {
        return (KeyButtonView) mCurrentView.findViewById(R.id.home);
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewById(R.id.ime_switcher);
    }

    /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
    public View getHideBarButton() {
        return mCurrentView.findViewById(R.id.hide_bar_btn);
    }

    public KeyButtonView getNotiBarButton() {
        return (KeyButtonView)mCurrentView.findViewById(R.id.pull_notification_bar);
    }
    /* @} */

    private void getIcons(Resources res) {
        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_land);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
        mBackAltLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime_land);
        mRecentIcon = res.getDrawable(R.drawable.ic_sysbar_recent);
        mRecentLandIcon = res.getDrawable(R.drawable.ic_sysbar_recent_land);
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(getContext().getResources());

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        ((ImageView)getBackButton()).setImageDrawable(backAlt
                ? (mVertical ? mBackAltLandIcon : mBackAltIcon)
                : (mVertical ? mBackLandIcon : mBackIcon));

        ((ImageView)getRecentsButton()).setImageDrawable(mVertical ? mRecentLandIcon : mRecentIcon);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0);
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);
        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);


        setDisabledFlags(mDisabledFlags, true);
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
            }
        }
        if (inLockTask() && disableRecent && !disableHome) {
            // Don't hide recents when in lock task, it is used for exiting.
            // Unless home is hidden, then in DPM locked mode and no exit available.
            disableRecent = false;
        }

        getBackButton()   .setVisibility(disableBack       ? View.INVISIBLE : View.VISIBLE);
        getHomeButton()   .setVisibility(disableHome       ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);
    }

    private boolean inLockTask() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        setLayoutTransitionsEnabled(!wakeAndUnlocking);
    }

    private void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        LayoutTransition lt = navButtons.getLayoutTransition();
        if (lt != null) {
            if (enabled) {
                lt.enableTransitionType(LayoutTransition.APPEARING);
                lt.enableTransitionType(LayoutTransition.DISAPPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            } else {
                lt.disableTransitionType(LayoutTransition.APPEARING);
                lt.disableTransitionType(LayoutTransition.DISAPPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            }
        }
    }

    private void setUseFadingAnimations(boolean useFadingAnimations) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean old = lp.windowAnimations != 0;
            if (!old && useFadingAnimations) {
                lp.windowAnimations = R.style.Animation_NavigationBarFadeIn;
            } else if (old && !useFadingAnimations) {
                lp.windowAnimations = 0;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        getMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] =
        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];

        mCurrentView = mRotatedViews[Surface.ROTATION_0];

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
        if(PhoneStatusBar.mSupportDynamicBar) {
            getHideBarButton().setOnClickListener(mHideBarClickListener);
            getNotiBarButton().setOnClickListener(mNotiBarClickListener);
            mLastNavBarMode = Settings.System.getInt(mContext.getContentResolver(), "navigationbar_config", 0);
            Log.d(TAG, "NavigationVarView#onFinishInflate, mLastNavBarMode: " + mLastNavBarMode);
            if ((mLastNavBarMode & 0x10) != 0) {
                getHideBarButton().setVisibility(View.VISIBLE);
            } else {
                getHideBarButton().setVisibility(View.INVISIBLE);
            }

            if ((mLastNavBarMode & 0x0F) == 2 || (mLastNavBarMode & 0x0F) == 3) {
                getNotiBarButton().setVisibility(View.VISIBLE);
            } else {
                getNotiBarButton().setVisibility(View.GONE);
            }
        }
        /* @} */

        updateRTLOrder();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }

        mCurrentView = mRotatedViews[rot];
        /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
        Log.d(TAG, "NavigationBarView#reorient, rot: "+rot, new Exception());
        if(PhoneStatusBar.mSupportDynamicBar) {
            int mode = Settings.System.getInt(mContext.getContentResolver(), "navigationbar_config", 0);

            if ((mode & 0x10) != 0) {
                getHideBarButton().setVisibility(View.VISIBLE);
            } else {
                getHideBarButton().setVisibility(View.INVISIBLE);
            }

            if ((mode & 0x0F) == 2 || (mode & 0x0F) == 3) {
                getNotiBarButton().setVisibility(View.VISIBLE);
            } else {
                getNotiBarButton().setVisibility(View.GONE);
            }
            // TODO: here maybe enhanced later, because only one can be implement... this logic is ok?
            if(rot == 0 || rot ==2) {
                swapChildrenOrderIfVertical1(mRotatedViews[0].findViewById(R.id.nav_buttons), 0x0F & mode);
            } else {
                swapChildrenOrderIfVertical2(mRotatedViews[1].findViewById(R.id.nav_buttons), 0x0F & mode);
            }
        }
        /* @} */
        mCurrentView.setVisibility(View.VISIBLE);
        setLayoutTransitionsEnabled(mLayoutTransitionsEnabled);

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
        if(PhoneStatusBar.mSupportDynamicBar) {
            getHideBarButton().setOnClickListener(mHideBarClickListener);
            getNotiBarButton().setOnClickListener(mNotiBarClickListener);
        }
        /* @} */

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);

        // force the low profile & disabled states into compliance
        mBarTransitions.init();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {

            // We swap all children of the 90 and 270 degree layouts, since they are vertical
            View rotation90 = mRotatedViews[Surface.ROTATION_90];
            swapChildrenOrderIfVertical(rotation90.findViewById(R.id.nav_buttons));
            adjustExtraKeyGravity(rotation90, isLayoutRtl);

            View rotation270 = mRotatedViews[Surface.ROTATION_270];
            if (rotation90 != rotation270) {
                swapChildrenOrderIfVertical(rotation270.findViewById(R.id.nav_buttons));
                adjustExtraKeyGravity(rotation270, isLayoutRtl);
            }
            mIsLayoutRtl = isLayoutRtl;
        }
    }

    private void adjustExtraKeyGravity(View navBar, boolean isLayoutRtl) {
        View menu = navBar.findViewById(R.id.menu);
        View imeSwitcher = navBar.findViewById(R.id.ime_switcher);
        if (menu != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            menu.setLayoutParams(lp);
        }
        if (imeSwitcher != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) imeSwitcher.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            imeSwitcher.setLayoutParams(lp);
        }
    }

    /**
     * Swaps the children order of a LinearLayout if it's orientation is Vertical
     *
     * @param group The LinearLayout to swap the children from.
     */
    private void swapChildrenOrderIfVertical(View group) {
        Log.d(TAG, "NavigationBarView#swapChildrenOrderIfVertical is called", new Exception());

        if (group instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) group;
            if (linearLayout.getOrientation() == VERTICAL) {
                int childCount = linearLayout.getChildCount();
                ArrayList<View> childList = new ArrayList<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    childList.add(linearLayout.getChildAt(i));
                }
                linearLayout.removeAllViews();
                for (int i = childCount - 1; i >= 0; i--) {
                    linearLayout.addView(childList.get(i));
                }
            }
        }
    }

    /* SPRD: Bug 535100 new feature of dynamic navigationbar @{ */
    private void swapChildrenOrderIfVertical1(View group, int mode) {
        if (group instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) group;
            int childCount = linearLayout.getChildCount();
            ArrayList<View> childList = new ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                childList.add(linearLayout.getChildAt(i));
            }
            linearLayout.removeAllViews();

            for (int i = 0; i < childCount; i++) {
                if (mode == 0 || mode == 2) {
                    if (i == 1) {
                        linearLayout.addView(getViewWithId(childList, R.id.back));
                    } else if (i == 3) {
                        linearLayout.addView(getViewWithId(childList, R.id.recent_apps));
                    } else {
                        linearLayout.addView(childList.get(i));
                    }
                } else {
                    if (i == 1) {
                        linearLayout.addView(getViewWithId(childList, R.id.recent_apps));
                    } else if (i == 3) {
                        linearLayout.addView(getViewWithId(childList, R.id.back));
                    } else {
                        linearLayout.addView(childList.get(i));
                    }
                }
            }
        }
    }

    private void swapChildrenOrderIfVertical2(View group, int mode) {
        if (group instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) group;
            int childCount = linearLayout.getChildCount();
            ArrayList<View> childList = new ArrayList<>(childCount);
            for (int i = 0; i < childCount; i++) {
                childList.add(linearLayout.getChildAt(i));
            }

            linearLayout.removeAllViews();
            for (int i = 0; i < childCount; i++) {
                if (mode == 0 || mode == 2) {
                    if (i == 4) {
                        linearLayout.addView(getViewWithId(childList, R.id.back));
                    } else if (i == 2) {
                        linearLayout.addView(getViewWithId(childList, R.id.recent_apps));
                    } else {
                        linearLayout.addView(childList.get(i));
                    }
                } else {
                    if (i == 4) {
                        linearLayout.addView(getViewWithId(childList, R.id.recent_apps));
                    } else if (i == 2) {
                        linearLayout.addView(getViewWithId(childList, R.id.back));
                    } else {
                        linearLayout.addView(childList.get(i));
                    }
                }
            }
        }
    }

    private View getViewWithId(ArrayList<View> list, int id) {
        int size = list.size();
        if (size <= 0) {
            return null;
        }

        for (int i = 0; i < size; i++) {
            if(list.get(i).getId() == id) return list.get(i);
        }
        return null;
    }

    public void updateNavigationbarViews() {
        int mode = Settings.System.getInt(mContext.getContentResolver(), "navigationbar_config", 0);
        if (!isAttachedToWindow()) return;

        boolean style = (mLastNavBarMode&0x0F) != (mode&0x0F);

        boolean hide = (mLastNavBarMode&0xF0) != (mode&0xF0);
        
        Log.d(TAG, "updateNavigationbarViews, mode: "+ mode + "; mLastNavBarMode: " + mLastNavBarMode 
                + "; style: " + style + "; hide: "+hide);
        if(hide) {
            if ((mode & 0x10) != 0) {
                getHideBarButton().setVisibility(View.VISIBLE);
            } else {
                getHideBarButton().setVisibility(View.INVISIBLE);
            }
        }

        if(style) {
            if ((mode & 0x0F) == 2 || (mode & 0x0F) == 3) {
                getNotiBarButton().setVisibility(View.VISIBLE);
            } else {
                getNotiBarButton().setVisibility(View.GONE);
            }
        }
        
        if(style) {
            swapChildrenOrderIfVertical1(mRotatedViews[0].findViewById(R.id.nav_buttons), 0x0F&mode);
            swapChildrenOrderIfVertical2(mRotatedViews[1].findViewById(R.id.nav_buttons), 0x0F&mode);
        }
        
        mLastNavBarMode = mode;
    }
    /* @} */

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
                    );
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }
}
