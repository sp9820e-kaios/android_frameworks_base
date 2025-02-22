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
 * limitations under the License.
 */

package com.android.systemui.recents.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.recents.Constants;
import com.android.systemui.recents.RecentsConfiguration;
import com.android.systemui.recents.misc.SystemServicesProxy;
import com.android.systemui.recents.misc.Utilities;
import com.android.systemui.recents.model.RecentsTaskLoader;
import com.android.systemui.recents.model.Task;
import com.android.systemui.statusbar.phone.PhoneStatusBar;


/* The task bar view */
public class TaskViewHeader extends FrameLayout {

    RecentsConfiguration mConfig;
    private SystemServicesProxy mSsp;

    // Header views
    ImageView mMoveTaskButton;
    ImageView mDismissButton;
    ImageView mApplicationIcon;
    TextView mActivityDescription;

    // Header drawables
    boolean mCurrentPrimaryColorIsDark;
    int mCurrentPrimaryColor;
    int mBackgroundColor;
    Drawable mLightDismissDrawable;
    Drawable mDarkDismissDrawable;
    RippleDrawable mBackground;
    GradientDrawable mBackgroundColorDrawable;
    AnimatorSet mFocusAnimator;
    String mDismissContentDescription;

    // Static highlight that we draw at the top of each view
    static Paint sHighlightPaint;

    // Header dim, which is only used when task view hardware layers are not used
    Paint mDimLayerPaint = new Paint();
    PorterDuffColorFilter mDimColorFilter = new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_ATOP);

    boolean mLayersDisabled;

    public TaskViewHeader(Context context) {
        this(context, null);
    }

    public TaskViewHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskViewHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskViewHeader(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mConfig = RecentsConfiguration.getInstance();
        mSsp = RecentsTaskLoader.getInstance().getSystemServicesProxy();
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            }
        });

        // Load the dismiss resources
        mLightDismissDrawable = context.getDrawable(R.drawable.recents_dismiss_light);
        mDarkDismissDrawable = context.getDrawable(R.drawable.recents_dismiss_dark);
        mDismissContentDescription =
                context.getString(R.string.accessibility_recents_item_will_be_dismissed);

        // Configure the highlight paint
        if (sHighlightPaint == null) {
            sHighlightPaint = new Paint();
            sHighlightPaint.setStyle(Paint.Style.STROKE);
            sHighlightPaint.setStrokeWidth(mConfig.taskViewHighlightPx);
            sHighlightPaint.setColor(mConfig.taskBarViewHighlightColor);
            sHighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            sHighlightPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void onFinishInflate() {
        // Initialize the icon and description views
        mApplicationIcon = (ImageView) findViewById(R.id.application_icon);
        mActivityDescription = (TextView) findViewById(R.id.activity_description);
        mDismissButton = (ImageView) findViewById(R.id.dismiss_task);
        mMoveTaskButton = (ImageView) findViewById(R.id.move_task);

        // Hide the backgrounds if they are ripple drawables
        if (!Constants.DebugFlags.App.EnableTaskFiltering) {
            if (mApplicationIcon.getBackground() instanceof RippleDrawable) {
                mApplicationIcon.setBackground(null);
            }
        }

        mBackgroundColorDrawable = (GradientDrawable) getContext().getDrawable(R.drawable
                .recents_task_view_header_bg_color);
        // Copy the ripple drawable since we are going to be manipulating it
        mBackground = (RippleDrawable)
                getContext().getDrawable(R.drawable.recents_task_view_header_bg);
        mBackground = (RippleDrawable) mBackground.mutate().getConstantState().newDrawable();
        mBackground.setColor(ColorStateList.valueOf(0));
        mBackground.setDrawableByLayerId(mBackground.getId(0), mBackgroundColorDrawable);
        setBackground(mBackground);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the highlight at the top edge (but put the bottom edge just out of view)
        float offset = (float) Math.ceil(mConfig.taskViewHighlightPx / 2f);
        float radius = mConfig.taskViewRoundedCornerRadiusPx;
        int count = canvas.save(Canvas.CLIP_SAVE_FLAG);
        canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(-offset, 0f, (float) getMeasuredWidth() + offset,
                getMeasuredHeight() + radius, radius, radius, sHighlightPaint);
        canvas.restoreToCount(count);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Sets the dim alpha, only used when we are not using hardware layers.
     * (see RecentsConfiguration.useHardwareLayers)
     */
    void setDimAlpha(int alpha) {
        mDimColorFilter.setColor(Color.argb(alpha, 0, 0, 0));
        mDimLayerPaint.setColorFilter(mDimColorFilter);
        if (!mLayersDisabled) {
            setLayerType(LAYER_TYPE_HARDWARE, mDimLayerPaint);
        }
    }

    /** Returns the secondary color for a primary color. */
    int getSecondaryColor(int primaryColor, boolean useLightOverlayColor) {
        int overlayColor = useLightOverlayColor ? Color.WHITE : Color.BLACK;
        return Utilities.getColorWithOverlay(primaryColor, overlayColor, 0.8f);
    }

    /** Binds the bar view to the task */
    public void rebindToTask(Task t) {
        // If an activity icon is defined, then we use that as the primary icon to show in the bar,
        // otherwise, we fall back to the application icon
        if (t.activityIcon != null) {
            mApplicationIcon.setImageDrawable(t.activityIcon);
        } else if (t.applicationIcon != null) {
            mApplicationIcon.setImageDrawable(t.applicationIcon);
        }
        if (!mActivityDescription.getText().toString().equals(t.activityLabel)) {
            mActivityDescription.setText(t.activityLabel);
        }
        mActivityDescription.setContentDescription(t.contentDescription);

        // Try and apply the system ui tint
        int existingBgColor = (getBackground() instanceof ColorDrawable) ?
                ((ColorDrawable) getBackground()).getColor() : 0;
        if (existingBgColor != t.colorPrimary) {
            mBackgroundColorDrawable.setColor(t.colorPrimary);
            mBackgroundColor = t.colorPrimary;
        }
        mCurrentPrimaryColor = t.colorPrimary;
        mCurrentPrimaryColorIsDark = t.useLightOnPrimaryColor;
        mActivityDescription.setTextColor(t.useLightOnPrimaryColor ?
                mConfig.taskBarViewLightTextColor : mConfig.taskBarViewDarkTextColor);
        mDismissButton.setImageDrawable(t.useLightOnPrimaryColor ?
                mLightDismissDrawable : mDarkDismissDrawable);
        mDismissButton.setContentDescription(String.format(mDismissContentDescription,
                t.contentDescription));
        mMoveTaskButton.setVisibility((mConfig.multiStackEnabled) ? View.VISIBLE : View.INVISIBLE);
        if (mConfig.multiStackEnabled) {
            updateResizeTaskBarIcon(t);
        }
    }

    /** Updates the resize task bar button. */
    void updateResizeTaskBarIcon(Task t) {
        Rect display = mSsp.getWindowRect();
        Rect taskRect = mSsp.getTaskBounds(t.key.stackId);
        int resId = R.drawable.star;
        if (display.equals(taskRect) || taskRect.isEmpty()) {
            resId = R.drawable.vector_drawable_place_fullscreen;
        } else {
            boolean top = display.top == taskRect.top;
            boolean bottom = display.bottom == taskRect.bottom;
            boolean left = display.left == taskRect.left;
            boolean right = display.right == taskRect.right;
            if (top && bottom && left) {
                resId = R.drawable.vector_drawable_place_left;
            } else if (top && bottom && right) {
                resId = R.drawable.vector_drawable_place_right;
            } else if (top && left && right) {
                resId = R.drawable.vector_drawable_place_top;
            } else if (bottom && left && right) {
                resId = R.drawable.vector_drawable_place_bottom;
            } else if (top && right) {
                resId = R.drawable.vector_drawable_place_top_right;
            } else if (top && left) {
                resId = R.drawable.vector_drawable_place_top_left;
            } else if (bottom && right) {
                resId = R.drawable.vector_drawable_place_bottom_right;
            } else if (bottom && left) {
                resId = R.drawable.vector_drawable_place_bottom_left;
            }
        }
        mMoveTaskButton.setImageResource(resId);
    }

    /** Unbinds the bar view from the task */
    void unbindFromTask() {
        mApplicationIcon.setImageDrawable(null);
    }

    /** Animates this task bar dismiss button when launching a task. */
    void startLaunchTaskDismissAnimation() {
        if (mDismissButton.getVisibility() == View.VISIBLE) {
            mDismissButton.animate().cancel();
            mDismissButton.animate()
                    .alpha(0f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutSlowInInterpolator)
                    .setDuration(mConfig.taskViewExitToAppDuration)
                    .start();
        }
    }

    /** Animates this task bar if the user does not interact with the stack after a certain time. */
    void startNoUserInteractionAnimation() {
        /* SPRD: Bug 535096 new feature of lock recent apps @{ */
        if (mDismissButton.getVisibility() != View.VISIBLE) {
            if(PhoneStatusBar.mSupportLockApp) {
                TaskView tv = (TaskView)mDismissButton.getParent().getParent().getParent();
                if(tv.getTask().isLocked) {
                    return;
                }
            }
            /* @} */
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setAlpha(0f);
            mDismissButton.animate()
                    .alpha(1f)
                    .setStartDelay(0)
                    .setInterpolator(mConfig.fastOutLinearInInterpolator)
                    .setDuration(mConfig.taskViewEnterFromAppDuration)
                    .start();
        }
    }

    /** Mark this task view that the user does has not interacted with the stack after a certain time. */
    void setNoUserInteractionState() {
        if (mDismissButton.getVisibility() != View.VISIBLE) {
            mDismissButton.animate().cancel();
            /* SPRD: Bug 558006 fix the UI issue of dismiss button and lock icon overlap @{ */
            if(PhoneStatusBar.mSupportLockApp) {
                TaskView tv = (TaskView)mDismissButton.getParent().getParent().getParent();
                if(tv.getTask().isLocked) {
                    return;
                }
            }
            /* @} */
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setAlpha(1f);
        }
    }

    /** Resets the state tracking that the user has not interacted with the stack after a certain time. */
    void resetNoUserInteractionState() {
        mDismissButton.setVisibility(View.INVISIBLE);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {

        // Don't forward our state to the drawable - we do it manually in onTaskViewFocusChanged.
        // This is to prevent layer trashing when the view is pressed.
        return new int[] {};
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mLayersDisabled) {
            mLayersDisabled = false;
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    mLayersDisabled = false;
                    setLayerType(LAYER_TYPE_HARDWARE, mDimLayerPaint);
                }
            });
        }
    }

    public void disableLayersForOneFrame() {
        mLayersDisabled = true;

        // Disable layer for a frame so we can draw our first frame faster.
        setLayerType(LAYER_TYPE_NONE, null);
    }

    /** Notifies the associated TaskView has been focused. */
    void onTaskViewFocusChanged(boolean focused, boolean animateFocusedState) {
        // If we are not animating the visible state, just return
        if (!animateFocusedState) return;

        boolean isRunning = false;
        if (mFocusAnimator != null) {
            isRunning = mFocusAnimator.isRunning();
            Utilities.cancelAnimationWithoutCallbacks(mFocusAnimator);
        }

        if (focused) {
            int currentColor = mBackgroundColor;
            int secondaryColor = getSecondaryColor(mCurrentPrimaryColor, mCurrentPrimaryColorIsDark);
            int[][] states = new int[][] {
                    new int[] {},
                    new int[] { android.R.attr.state_enabled },
                    new int[] { android.R.attr.state_pressed }
            };
            int[] newStates = new int[]{
                    0,
                    android.R.attr.state_enabled,
                    android.R.attr.state_pressed
            };
            int[] colors = new int[] {
                    currentColor,
                    secondaryColor,
                    secondaryColor
            };
            mBackground.setColor(new ColorStateList(states, colors));
            mBackground.setState(newStates);
            // Pulse the background color
            int lightPrimaryColor = getSecondaryColor(mCurrentPrimaryColor, mCurrentPrimaryColorIsDark);
            ValueAnimator backgroundColor = ValueAnimator.ofObject(new ArgbEvaluator(),
                    currentColor, lightPrimaryColor);
            backgroundColor.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mBackground.setState(new int[]{});
                }
            });
            backgroundColor.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int color = (int) animation.getAnimatedValue();
                    mBackgroundColorDrawable.setColor(color);
                    mBackgroundColor = color;
                }
            });
            backgroundColor.setRepeatCount(ValueAnimator.INFINITE);
            backgroundColor.setRepeatMode(ValueAnimator.REVERSE);
            // Pulse the translation
            ObjectAnimator translation = ObjectAnimator.ofFloat(this, "translationZ", 15f);
            translation.setRepeatCount(ValueAnimator.INFINITE);
            translation.setRepeatMode(ValueAnimator.REVERSE);

            mFocusAnimator = new AnimatorSet();
            mFocusAnimator.playTogether(backgroundColor, translation);
            mFocusAnimator.setStartDelay(150);
            mFocusAnimator.setDuration(750);
            mFocusAnimator.start();
        } else {
            if (isRunning) {
                // Restore the background color
                int currentColor = mBackgroundColor;
                ValueAnimator backgroundColor = ValueAnimator.ofObject(new ArgbEvaluator(),
                        currentColor, mCurrentPrimaryColor);
                backgroundColor.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int color = (int) animation.getAnimatedValue();
                        mBackgroundColorDrawable.setColor(color);
                        mBackgroundColor = color;
                    }
                });
                // Restore the translation
                ObjectAnimator translation = ObjectAnimator.ofFloat(this, "translationZ", 0f);

                mFocusAnimator = new AnimatorSet();
                mFocusAnimator.playTogether(backgroundColor, translation);
                mFocusAnimator.setDuration(150);
                mFocusAnimator.start();
            } else {
                mBackground.setState(new int[] {});
                setTranslationZ(0f);
            }
        }
    }
}
