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

package com.android.systemui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

public class ToggleSeekBar extends SeekBar {
    private String mAccessibilityLabel;

    public ToggleSeekBar(Context context) {
        super(context);
    }

    public ToggleSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /*SPRD bug 617670:Press left/right key when progress in min/max maybe cause view focus change wrong.{@*/
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_DOWN:
            break;
        case KeyEvent.KEYCODE_DPAD_UP:
            break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            /*SPRD bug 627046:Layout rtl maybe wrong*/
            if(isLayoutRtl()){
                if(getProgress() == getMax()){
                    Log.d(VIEW_LOG_TAG, "onKeyDown left1 Progress="+getProgress()+",max="+ getMax());
                    return true;
                }
            }else{
                if(getProgress() == 0){
                    Log.d(VIEW_LOG_TAG, "onKeyDown left2 Progress="+getProgress()+",max="+ getMax());
                    return true;
                }
            }
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            /*SPRD bug 627046:Layout rtl maybe wrong*/
            if(isLayoutRtl()){
                if(getProgress() == 0){
                    Log.d(VIEW_LOG_TAG, "onKeyDown right1 Progress="+getProgress()+",max="+ getMax());
                    return true;
                }
            }else{
                if(getProgress() == getMax()){
                    Log.d(VIEW_LOG_TAG, "onKeyDown right2 Progress="+getProgress()+",max="+ getMax());
                    return true;
                }
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }
    /*@}*/

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            setEnabled(true);
        }

        return super.onTouchEvent(event);
    }

    public void setAccessibilityLabel(String label) {
        mAccessibilityLabel = label;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (mAccessibilityLabel != null) {
            info.setText(mAccessibilityLabel);
        }
    }
}
