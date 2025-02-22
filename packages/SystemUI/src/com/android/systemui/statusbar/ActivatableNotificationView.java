/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import com.android.systemui.R;

/**
 * Base class for both {@link ExpandableNotificationRow} and {@link NotificationOverflowContainer}
 * to implement dimming/activating on Keyguard for the double-tap gesture
 */
public abstract class ActivatableNotificationView extends ExpandableOutlineView {

    private static final long DOUBLETAP_TIMEOUT_MS = 1200;
    private static final int BACKGROUND_ANIMATION_LENGTH_MS = 220;
    private static final int ACTIVATE_ANIMATION_LENGTH = 220;
    private static final int DARK_ANIMATION_LENGTH = 170;

    /**
     * The amount of width, which is kept in the end when performing a disappear animation (also
     * the amount from which the horizontal appearing begins)
     */
    private static final float HORIZONTAL_COLLAPSED_REST_PARTIAL = 0.05f;

    /**
     * At which point from [0,1] does the horizontal collapse animation end (or start when
     * expanding)? 1.0 meaning that it ends immediately and 0.0 that it is continuously animated.
     */
    private static final float HORIZONTAL_ANIMATION_END = 0.2f;

    /**
     * At which point from [0,1] does the alpha animation end (or start when
     * expanding)? 1.0 meaning that it ends immediately and 0.0 that it is continuously animated.
     */
    private static final float ALPHA_ANIMATION_END = 0.0f;

    /**
     * At which point from [0,1] does the horizontal collapse animation start (or start when
     * expanding)? 1.0 meaning that it starts immediately and 0.0 that it is animated at all.
     */
    private static final float HORIZONTAL_ANIMATION_START = 1.0f;

    /**
     * At which point from [0,1] does the vertical collapse animation start (or end when
     * expanding) 1.0 meaning that it starts immediately and 0.0 that it is animated at all.
     */
    private static final float VERTICAL_ANIMATION_START = 1.0f;

    /**
     * Scale for the background to animate from when exiting dark mode.
     */
    private static final float DARK_EXIT_SCALE_START = 0.93f;

    private static final Interpolator ACTIVATE_INVERSE_INTERPOLATOR
            = new PathInterpolator(0.6f, 0, 0.5f, 1);
    private static final Interpolator ACTIVATE_INVERSE_ALPHA_INTERPOLATOR
            = new PathInterpolator(0, 0, 0.5f, 1);
    private final int mTintedRippleColor;
    private final int mLowPriorityRippleColor;
    protected final int mNormalRippleColor;

    private boolean mDimmed;
    private boolean mDark;

    private int mBgTint = 0;

    /**
     * Flag to indicate that the notification has been touched once and the second touch will
     * click it.
     */
    private boolean mActivated;

    private float mDownX;
    private float mDownY;
    private final float mTouchSlop;

    private OnActivatedListener mOnActivatedListener;

    private final Interpolator mLinearOutSlowInInterpolator;
    protected final Interpolator mFastOutSlowInInterpolator;
    private final Interpolator mSlowOutFastInInterpolator;
    private final Interpolator mSlowOutLinearInInterpolator;
    private final Interpolator mLinearInterpolator;
    private Interpolator mCurrentAppearInterpolator;
    private Interpolator mCurrentAlphaInterpolator;

    private NotificationBackgroundView mBackgroundNormal;
    private NotificationBackgroundView mBackgroundDimmed;
    private ObjectAnimator mBackgroundAnimator;
    private RectF mAppearAnimationRect = new RectF();
    private float mAnimationTranslationY;
    private boolean mDrawingAppearAnimation;
    private ValueAnimator mAppearAnimator;
    private float mAppearAnimationFraction = -1.0f;
    private float mAppearAnimationTranslation;
    private boolean mShowingLegacyBackground;
    private final int mLegacyColor;
    private final int mNormalColor;
    private final int mLowPriorityColor;
    private boolean mIsBelowSpeedBump;

    public ActivatableNotificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mFastOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mSlowOutFastInInterpolator = new PathInterpolator(0.8f, 0.0f, 0.6f, 1.0f);
        mLinearOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in);
        mSlowOutLinearInInterpolator = new PathInterpolator(0.8f, 0.0f, 1.0f, 1.0f);
        mLinearInterpolator = new LinearInterpolator();
        setClipChildren(false);
        setClipToPadding(false);
        mLegacyColor = context.getColor(R.color.notification_legacy_background_color);
        mNormalColor = context.getColor(R.color.notification_material_background_color);
        mLowPriorityColor = context.getColor(
                R.color.notification_material_background_low_priority_color);
        mTintedRippleColor = context.getColor(
                R.color.notification_ripple_tinted_color);
        mLowPriorityRippleColor = context.getColor(
                R.color.notification_ripple_color_low_priority);
        mNormalRippleColor = context.getColor(
                R.color.notification_ripple_untinted_color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundNormal = (NotificationBackgroundView) findViewById(R.id.backgroundNormal);
        mBackgroundDimmed = (NotificationBackgroundView) findViewById(R.id.backgroundDimmed);
        mBackgroundNormal.setCustomBackground(R.drawable.notification_material_bg_btn);
        mBackgroundDimmed.setCustomBackground(R.drawable.notification_material_bg_dim_btn);
        updateBackground();
        updateBackgroundTint();
    }

    private final Runnable mTapTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            makeInactive(true /* animate */);
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDimmed) {
            return handleTouchEventDimmed(event);
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        if (!mDimmed){
            mBackgroundNormal.drawableHotspotChanged(x, y);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mDimmed) {
            mBackgroundDimmed.setState(getDrawableState());
        } else {
            mBackgroundNormal.setState(getDrawableState());
        }
    }

    private boolean handleTouchEventDimmed(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                if (mDownY > getActualHeight()) {
                    return false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isWithinTouchSlop(event)) {
                    makeInactive(true /* animate */);
                    return false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isWithinTouchSlop(event)) {
                    if (!mActivated) {
                        makeActive();
                        postDelayed(mTapTimeoutRunnable, DOUBLETAP_TIMEOUT_MS);
                    } else {
                        boolean performed = performClick();
                        if (performed) {
                            removeCallbacks(mTapTimeoutRunnable);
                        }
                    }
                } else {
                    makeInactive(true /* animate */);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                makeInactive(true /* animate */);
                break;
            default:
                break;
        }
        return true;
    }

    private void makeActive() {
        startActivateAnimation(false /* reverse */);
        mActivated = true;
        if (mOnActivatedListener != null) {
            mOnActivatedListener.onActivated(this);
        }
    }

    private void startActivateAnimation(boolean reverse) {
        if (!isAttachedToWindow()) {
            return;
        }
        int widthHalf = mBackgroundNormal.getWidth()/2;
        int heightHalf = mBackgroundNormal.getActualHeight()/2;
        float radius = (float) Math.sqrt(widthHalf*widthHalf + heightHalf*heightHalf);
        Animator animator;
        if (reverse) {
            animator = ViewAnimationUtils.createCircularReveal(mBackgroundNormal,
                    widthHalf, heightHalf, radius, 0);
        } else {
            animator = ViewAnimationUtils.createCircularReveal(mBackgroundNormal,
                    widthHalf, heightHalf, 0, radius);
        }
        mBackgroundNormal.setVisibility(View.VISIBLE);
        Interpolator interpolator;
        Interpolator alphaInterpolator;
        if (!reverse) {
            interpolator = mLinearOutSlowInInterpolator;
            alphaInterpolator = mLinearOutSlowInInterpolator;
        } else {
            interpolator = ACTIVATE_INVERSE_INTERPOLATOR;
            alphaInterpolator = ACTIVATE_INVERSE_ALPHA_INTERPOLATOR;
        }
        animator.setInterpolator(interpolator);
        animator.setDuration(ACTIVATE_ANIMATION_LENGTH);
        if (reverse) {
            mBackgroundNormal.setAlpha(1f);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mDimmed) {
                        mBackgroundNormal.setVisibility(View.INVISIBLE);
                    }
                }
            });
            animator.start();
        } else {
            mBackgroundNormal.setAlpha(0.4f);
            animator.start();
        }
        mBackgroundNormal.animate()
                .alpha(reverse ? 0f : 1f)
                .setInterpolator(alphaInterpolator)
                .setDuration(ACTIVATE_ANIMATION_LENGTH);
    }

    /**
     * Cancels the hotspot and makes the notification inactive.
     */
    public void makeInactive(boolean animate) {
        if (mActivated) {
            if (mDimmed) {
                if (animate) {
                    startActivateAnimation(true /* reverse */);
                } else {
                    mBackgroundNormal.setVisibility(View.INVISIBLE);
                }
            }
            mActivated = false;
        }
        if (mOnActivatedListener != null) {
            mOnActivatedListener.onActivationReset(this);
        }
        removeCallbacks(mTapTimeoutRunnable);
    }

    private boolean isWithinTouchSlop(MotionEvent event) {
        return Math.abs(event.getX() - mDownX) < mTouchSlop
                && Math.abs(event.getY() - mDownY) < mTouchSlop;
    }

    public void setDimmed(boolean dimmed, boolean fade) {
        if (mDimmed != dimmed) {
            mDimmed = dimmed;
            if (fade) {
                fadeDimmedBackground();
            } else {
                updateBackground();
            }
        }
    }

    public void setDark(boolean dark, boolean fade, long delay) {
        super.setDark(dark, fade, delay);
        if (mDark == dark) {
            return;
        }
        mDark = dark;
        if (!dark && fade) {
            if (mActivated) {
                mBackgroundDimmed.setVisibility(View.VISIBLE);
                mBackgroundNormal.setVisibility(View.VISIBLE);
            } else if (mDimmed) {
                mBackgroundDimmed.setVisibility(View.VISIBLE);
                mBackgroundNormal.setVisibility(View.INVISIBLE);
            } else {
                mBackgroundDimmed.setVisibility(View.INVISIBLE);
                mBackgroundNormal.setVisibility(View.VISIBLE);
            }
            fadeInFromDark(delay);
        } else {
            updateBackground();
        }
     }

    public void setShowingLegacyBackground(boolean showing) {
        mShowingLegacyBackground = showing;
        updateBackgroundTint();
    }

    @Override
    public void setBelowSpeedBump(boolean below) {
        super.setBelowSpeedBump(below);
        if (below != mIsBelowSpeedBump) {
            mIsBelowSpeedBump = below;
            updateBackgroundTint();
        }
    }

    /**
     * Sets the tint color of the background
     */
    public void setTintColor(int color) {
        mBgTint = color;
        updateBackgroundTint();
    }

    private void updateBackgroundTint() {
        int color = getBgColor();
        int rippleColor = getRippleColor();
        if (color == mNormalColor) {
            // We don't need to tint a normal notification
            color = 0;
        }
        mBackgroundDimmed.setTint(color);
        mBackgroundNormal.setTint(color);
        mBackgroundDimmed.setRippleColor(rippleColor);
        mBackgroundNormal.setRippleColor(rippleColor);
    }

    /**
     * Fades in the background when exiting dark mode.
     */
    private void fadeInFromDark(long delay) {
        final View background = mDimmed ? mBackgroundDimmed : mBackgroundNormal;
        background.setAlpha(0f);
        background.setPivotX(mBackgroundDimmed.getWidth() / 2f);
        background.setPivotY(getActualHeight() / 2f);
        background.setScaleX(DARK_EXIT_SCALE_START);
        background.setScaleY(DARK_EXIT_SCALE_START);
        background.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(DARK_ANIMATION_LENGTH)
                .setStartDelay(delay)
                .setInterpolator(mLinearOutSlowInInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        // Jump state if we are cancelled
                        background.setScaleX(1f);
                        background.setScaleY(1f);
                        background.setAlpha(1f);
                    }
                })
                .start();
    }

    /**
     * Fades the background when the dimmed state changes.
     */
    private void fadeDimmedBackground() {
        mBackgroundDimmed.animate().cancel();
        mBackgroundNormal.animate().cancel();
        if (mDimmed) {
            mBackgroundDimmed.setVisibility(View.VISIBLE);
        } else {
            mBackgroundNormal.setVisibility(View.VISIBLE);
        }
        float startAlpha = mDimmed ? 1f : 0;
        float endAlpha = mDimmed ? 0 : 1f;
        int duration = BACKGROUND_ANIMATION_LENGTH_MS;
        // Check whether there is already a background animation running.
        if (mBackgroundAnimator != null) {
            startAlpha = (Float) mBackgroundAnimator.getAnimatedValue();
            duration = (int) mBackgroundAnimator.getCurrentPlayTime();
            mBackgroundAnimator.removeAllListeners();
            mBackgroundAnimator.cancel();
            if (duration <= 0) {
                updateBackground();
                return;
            }
        }
        mBackgroundNormal.setAlpha(startAlpha);
        mBackgroundAnimator =
                ObjectAnimator.ofFloat(mBackgroundNormal, View.ALPHA, startAlpha, endAlpha);
        mBackgroundAnimator.setInterpolator(mFastOutSlowInInterpolator);
        mBackgroundAnimator.setDuration(duration);
        mBackgroundAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDimmed) {
                    mBackgroundNormal.setVisibility(View.INVISIBLE);
                } else {
                    mBackgroundDimmed.setVisibility(View.INVISIBLE);
                }
                mBackgroundAnimator = null;
            }
        });
        mBackgroundAnimator.start();
    }

    private void updateBackground() {
        cancelFadeAnimations();
        if (mDark) {
            mBackgroundDimmed.setVisibility(View.INVISIBLE);
            mBackgroundNormal.setVisibility(View.INVISIBLE);
        } else if (mDimmed) {
            mBackgroundDimmed.setVisibility(View.VISIBLE);
            mBackgroundNormal.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundDimmed.setVisibility(View.INVISIBLE);
            mBackgroundNormal.setVisibility(View.VISIBLE);
            mBackgroundNormal.setAlpha(1f);
            removeCallbacks(mTapTimeoutRunnable);
        }
    }

    private void cancelFadeAnimations() {
        if (mBackgroundAnimator != null) {
            mBackgroundAnimator.cancel();
        }
        mBackgroundDimmed.animate().cancel();
        mBackgroundNormal.animate().cancel();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setPivotX(getWidth() / 2);
    }

    @Override
    public void setActualHeight(int actualHeight, boolean notifyListeners) {
        super.setActualHeight(actualHeight, notifyListeners);
        setPivotY(actualHeight / 2);
        mBackgroundNormal.setActualHeight(actualHeight);
        mBackgroundDimmed.setActualHeight(actualHeight);
    }

    @Override
    public void setClipTopAmount(int clipTopAmount) {
        super.setClipTopAmount(clipTopAmount);
        mBackgroundNormal.setClipTopAmount(clipTopAmount);
        mBackgroundDimmed.setClipTopAmount(clipTopAmount);
    }

    @Override
    public void performRemoveAnimation(long duration, float translationDirection,
            Runnable onFinishedRunnable) {
        enableAppearDrawing(true);
        if (mDrawingAppearAnimation) {
            startAppearAnimation(false /* isAppearing */, translationDirection,
                    0, duration, onFinishedRunnable);
        } else if (onFinishedRunnable != null) {
            onFinishedRunnable.run();
        }
    }

    @Override
    public void performAddAnimation(long delay, long duration) {
        enableAppearDrawing(true);
        if (mDrawingAppearAnimation) {
            startAppearAnimation(true /* isAppearing */, -1.0f, delay, duration, null);
        }
    }

    private void startAppearAnimation(boolean isAppearing, float translationDirection, long delay,
            long duration, final Runnable onFinishedRunnable) {
        cancelAppearAnimation();
        mAnimationTranslationY = translationDirection * getActualHeight();
        if (mAppearAnimationFraction == -1.0f) {
            // not initialized yet, we start anew
            if (isAppearing) {
                mAppearAnimationFraction = 0.0f;
                mAppearAnimationTranslation = mAnimationTranslationY;
            } else {
                mAppearAnimationFraction = 1.0f;
                mAppearAnimationTranslation = 0;
            }
        }

        float targetValue;
        if (isAppearing) {
            mCurrentAppearInterpolator = mSlowOutFastInInterpolator;
            mCurrentAlphaInterpolator = mLinearOutSlowInInterpolator;
            targetValue = 1.0f;
        } else {
            mCurrentAppearInterpolator = mFastOutSlowInInterpolator;
            mCurrentAlphaInterpolator = mSlowOutLinearInInterpolator;
            targetValue = 0.0f;
        }
        mAppearAnimator = ValueAnimator.ofFloat(mAppearAnimationFraction,
                targetValue);
        mAppearAnimator.setInterpolator(mLinearInterpolator);
        mAppearAnimator.setDuration(
                (long) (duration * Math.abs(mAppearAnimationFraction - targetValue)));
        mAppearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAppearAnimationFraction = (float) animation.getAnimatedValue();
                updateAppearAnimationAlpha();
                updateAppearRect();
                invalidate();
            }
        });
        if (delay > 0) {
            // we need to apply the initial state already to avoid drawn frames in the wrong state
            updateAppearAnimationAlpha();
            updateAppearRect();
            mAppearAnimator.setStartDelay(delay);
        }
        mAppearAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mWasCancelled;

            @Override
            public void onAnimationEnd(Animator animation) {
                if (onFinishedRunnable != null) {
                    onFinishedRunnable.run();
                }
                if (!mWasCancelled) {
                    mAppearAnimationFraction = -1;
                    setOutlineRect(null);
                    enableAppearDrawing(false);
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
                mWasCancelled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mWasCancelled = true;
            }
        });
        mAppearAnimator.start();
    }

    private void cancelAppearAnimation() {
        if (mAppearAnimator != null) {
            mAppearAnimator.cancel();
        }
    }

    public void cancelAppearDrawing() {
        cancelAppearAnimation();
        enableAppearDrawing(false);
    }

    private void updateAppearRect() {
        float inverseFraction = (1.0f - mAppearAnimationFraction);
        float translationFraction = mCurrentAppearInterpolator.getInterpolation(inverseFraction);
        float translateYTotalAmount = translationFraction * mAnimationTranslationY;
        mAppearAnimationTranslation = translateYTotalAmount;

        // handle width animation
        float widthFraction = (inverseFraction - (1.0f - HORIZONTAL_ANIMATION_START))
                / (HORIZONTAL_ANIMATION_START - HORIZONTAL_ANIMATION_END);
        widthFraction = Math.min(1.0f, Math.max(0.0f, widthFraction));
        widthFraction = mCurrentAppearInterpolator.getInterpolation(widthFraction);
        float left = (getWidth() * (0.5f - HORIZONTAL_COLLAPSED_REST_PARTIAL / 2.0f) *
                widthFraction);
        float right = getWidth() - left;

        // handle top animation
        float heightFraction = (inverseFraction - (1.0f - VERTICAL_ANIMATION_START)) /
                VERTICAL_ANIMATION_START;
        heightFraction = Math.max(0.0f, heightFraction);
        heightFraction = mCurrentAppearInterpolator.getInterpolation(heightFraction);

        float top;
        float bottom;
        final int actualHeight = getActualHeight();
        if (mAnimationTranslationY > 0.0f) {
            bottom = actualHeight - heightFraction * mAnimationTranslationY * 0.1f
                    - translateYTotalAmount;
            top = bottom * heightFraction;
        } else {
            top = heightFraction * (actualHeight + mAnimationTranslationY) * 0.1f -
                    translateYTotalAmount;
            bottom = actualHeight * (1 - heightFraction) + top * heightFraction;
        }
        mAppearAnimationRect.set(left, top, right, bottom);
        setOutlineRect(left, top + mAppearAnimationTranslation, right,
                bottom + mAppearAnimationTranslation);
    }

    private void updateAppearAnimationAlpha() {
        float contentAlphaProgress = mAppearAnimationFraction;
        contentAlphaProgress = contentAlphaProgress / (1.0f - ALPHA_ANIMATION_END);
        contentAlphaProgress = Math.min(1.0f, contentAlphaProgress);
        contentAlphaProgress = mCurrentAlphaInterpolator.getInterpolation(contentAlphaProgress);
        setContentAlpha(contentAlphaProgress);
    }

    private void setContentAlpha(float contentAlpha) {
        View contentView = getContentView();
        if (contentView.hasOverlappingRendering()) {
            int layerType = contentAlpha == 0.0f || contentAlpha == 1.0f ? LAYER_TYPE_NONE
                    : LAYER_TYPE_HARDWARE;
            int currentLayerType = contentView.getLayerType();
            if (currentLayerType != layerType) {
                contentView.setLayerType(layerType, null);
            }
        }
        contentView.setAlpha(contentAlpha);
    }

    protected abstract View getContentView();

    private int getBgColor() {
        if (mBgTint != 0) {
            return mBgTint;
        } else if (mShowingLegacyBackground) {
            return mLegacyColor;
        } else if (mIsBelowSpeedBump) {
            return mLowPriorityColor;
        } else {
            return mNormalColor;
        }
    }

    protected int getRippleColor() {
        if (mBgTint != 0) {
            return mTintedRippleColor;
        } else if (mShowingLegacyBackground) {
            return mTintedRippleColor;
        } else if (mIsBelowSpeedBump) {
            return mLowPriorityRippleColor;
        } else {
            return mNormalRippleColor;
        }
    }

    /**
     * When we draw the appear animation, we render the view in a bitmap and render this bitmap
     * as a shader of a rect. This call creates the Bitmap and switches the drawing mode,
     * such that the normal drawing of the views does not happen anymore.
     *
     * @param enable Should it be enabled.
     */
    private void enableAppearDrawing(boolean enable) {
        if (enable != mDrawingAppearAnimation) {
            mDrawingAppearAnimation = enable;
            if (!enable) {
                setContentAlpha(1.0f);
            }
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mDrawingAppearAnimation) {
            canvas.save();
            canvas.translate(0, mAppearAnimationTranslation);
        }
        super.dispatchDraw(canvas);
        if (mDrawingAppearAnimation) {
            canvas.restore();
        }
    }

    public void setOnActivatedListener(OnActivatedListener onActivatedListener) {
        mOnActivatedListener = onActivatedListener;
    }

    public void reset() {
        setTintColor(0);
        setShowingLegacyBackground(false);
        setBelowSpeedBump(false);
    }

    public interface OnActivatedListener {
        void onActivated(ActivatableNotificationView view);
        void onActivationReset(ActivatableNotificationView view);
    }
}
