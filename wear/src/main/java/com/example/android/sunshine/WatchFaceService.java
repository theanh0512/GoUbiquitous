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

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchFaceService.Engine> mWeakReference;

        public EngineHandler(WatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.OnConnectionFailedListener,
            GoogleApiClient.ConnectionCallbacks,
            DataApi.DataListener{


        final String LOG_TAG = Engine.class.getSimpleName();
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mHighPaint;
        Paint mLowPaint;
        Paint mIconPaint;
        Paint mDatePaint;
        Paint mTextPaintDate;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        final String NOT_AVAILABLE = "na";

        float mXOffset;
        float mYOffset;
        float mYOffsetDate;
        float mXOffsetDate;
        float mXOffsetIcon;
        float mYOffsetIcon;
        float mXOffsetTemp;
        float mYOffsetTemp;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        GoogleApiClient mGoogleApiClient;

        final String WEAR_PATH = "/wear";
        final String HIGH_TEMP_KEY = "high";
        final String LOW_TEMP_KEY = "low";
        private static final String ICON_ASSET_KEY = "key";

        String mHighTemp;
        String mLowTemp;
        Bitmap mIcon;
        String mDate;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            Resources resources = WatchFaceService.this.getResources();
//            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
//
//            mBackgroundPaint = new Paint();
//            mBackgroundPaint.setColor(resources.getColor(R.color.background));
//
//            mTextPaint = new Paint();
//            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
//
//            mHighPaint = new Paint();
//            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));
//
//            mLowPaint = new Paint();
//            mLowPaint = createTextPaint(resources.getColor(R.color.digital_text));
//
//            mDatePaint = new Paint();
//            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));
//
//            mIconPaint = new Paint();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetDate = resources.getDimension(R.dimen.digital_y_offset_date);
            mXOffsetDate = resources.getDimension(R.dimen.digital_x_offset_date);
            mYOffsetIcon = resources.getDimension(R.dimen.digital_y_offset_icon);
            mYOffsetTemp= resources.getDimension(R.dimen.digital_y_offset_temp);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintDate = new Paint();
            mTextPaintDate = createTextPaint(resources.getColor(R.color.digital_text_date));

            mTime = new Time();

            mHighTemp = NOT_AVAILABLE;
            mLowTemp = NOT_AVAILABLE;
            mIcon = null;
            mDate = getFormattedDate(System.currentTimeMillis());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.i(LOG_TAG, "ON_VISIBILITY_CHANGED");

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }



        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mXOffsetIcon = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_icon_round : R.dimen.digital_x_offset_icon);
            mXOffsetTemp = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_temp_round : R.dimen.digital_x_offset_temp);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float textSizeDate = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round_date : R.dimen.digital_text_size_date);

            mTextPaint.setTextSize(textSize);
            mTextPaintDate.setTextSize(textSizeDate);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.i(LOG_TAG, "ON_AMBIENT_MODE_CHANGED");
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mTextPaintDate.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = WatchFaceService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.


            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = String.format(Locale.getDefault(), "%d:%02d", mTime.hour, mTime.minute);

            Log.i(LOG_TAG, "SECONDS REMOVED");

            float centerX = bounds.centerX();
            float centerY = bounds.centerY();



            canvas.drawText(text, centerX - mTextPaint.measureText(text)/2, centerY, mTextPaint);

            canvas.drawText(mDate, centerX - mDatePaint.measureText(mDate)/2, centerY + 40, mDatePaint);
            Log.i(LOG_TAG, "DATE ADDED");

            if (!mHighTemp.equals(NOT_AVAILABLE) && !mLowTemp.equals(NOT_AVAILABLE)){
                canvas.drawText(mHighTemp, centerX - mHighPaint.measureText(mHighTemp) - 8, centerY + 80, mHighPaint);
                canvas.drawText(mLowTemp, centerX + 8, centerY + 80, mLowPaint);
            }

            if (mIcon!=null && !isInAmbientMode()) {
                canvas.drawBitmap(mIcon, centerX - mIcon.getWidth()/2, centerY - 100, mIconPaint);
            }

        }


        public String getFormattedDate(long millis){
            SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM FF", Locale.getDefault());
            return formatter.format(new Date(millis));

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.i(LOG_TAG, "ON_CONNECTED");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i(LOG_TAG, "ON_CONNECTION_SUSPENDED");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.i(LOG_TAG, "ON_DATA_CHANGED");
            Log.i(LOG_TAG, "UPDATED");
            for (DataEvent event: dataEventBuffer){
                if (event.getType() == DataEvent.TYPE_CHANGED){
                    DataItem dataItem = event.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(WEAR_PATH) == 0){
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mHighTemp = dataMap.getString(HIGH_TEMP_KEY);
                        mLowTemp = dataMap.getString(LOW_TEMP_KEY);
                        loadBitmapFromAsset(dataMap.getAsset(ICON_ASSET_KEY));

                        Log.i(LOG_TAG, "HIGH TEMP: " + mHighTemp);
                        Log.i(LOG_TAG, "LOW TEMP: " + mLowTemp);
                    }
                }
            }
        }

        public void loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("NULL ASSET");
            }

            Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).setResultCallback(
                    new ResultCallback<DataApi.GetFdForAssetResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                            InputStream is = getFdForAssetResult.getInputStream();
                            if (is != null){
                                Bitmap icon = BitmapFactory.decodeStream(is);
                                mIcon = Bitmap.createScaledBitmap(icon, 40, 40, false);
                            }
                        }
                    });

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.i(LOG_TAG, "ON_CONNECTION_FAILED");
        }
    }
}
