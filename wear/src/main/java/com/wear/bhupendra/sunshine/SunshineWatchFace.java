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
package com.wear.bhupendra.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
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

    private class Engine extends CanvasWatchFaceService.Engine  implements GoogleApiClient.ConnectionCallbacks , GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener{

        // Constants for receiving data at  Watch Face
        private static final String PATH_WEATHER = "/weather";
        private static final String HIGH_TEMP = "high_temp";
        private static final String LOW_TEMP = "low_temp";
        private static final String WEATHER_ID = "weather_id";


        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;

        boolean mAmbient;
        Calendar mCalendar;

        float mXOffset;
        float mYOffset;

        Paint mTextTempLowPaint;
        Paint mTextTempHighPaint;
        Paint mTextDatePaint;

        // Variables for Weather Conditions
        String suffix = "\u00B0";
        private String mHighTemp ="30"+suffix;
        private String mLowTemp ="27"+suffix;
        private int mWeatherId =213;

        // Offsets date, time , weather
        float mYOffsetWeather;
        float mXOffsetDate;
        float mYOffsetDate;

        private GoogleApiClient mGoogleApiClient;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            //Initialize paint
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTextDatePaint = createTextDateTempPaint(resources.getColor(R.color.primary_light));

            mTextTempLowPaint = createTextTempPaint(resources.getColor(R.color.primary_light));
            mTextTempHighPaint = createTextTempPaint(resources.getColor(R.color.digital_text));

            mCalendar = Calendar.getInstance();

            //Initialize offset dstances
            mYOffsetWeather = resources.getDimension(R.dimen.y_offset_weather);
          //  mXOffsetTime = mTextTimePaint.measureText("12:00") / 2;
            mXOffsetDate = mTextDatePaint.measureText("WED, JUN 13, 2016") / 2;
            mYOffsetDate =resources.getDimension(R.dimen.y_offset_date);
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
            paint.setTextSize(getResources().getDimension(R.dimen.digital_text_size));
            return paint;
        }

        private Paint createTextTempPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(getResources().getDimension(R.dimen.temp_text_size));
            return paint;
        }

        private Paint createTextDateTempPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(getResources().getDimension(R.dimen.date_text_size));
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                //connect GoogleApiClient
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                unregisterReceiver();
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
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
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
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
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
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
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

            float highTextSize = mTextTempHighPaint.measureText(mHighTemp);


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                            mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                            mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            //For drawing the date
            String dayName = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
            String monthName = mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
            int year = mCalendar.get(Calendar.YEAR);
            String dateText = String.format("%s, %s %d, %d", dayName.toUpperCase(), monthName.toUpperCase(), dayOfMonth, year);
            if (isInAmbientMode()) {
                mTextDatePaint.setColor(getResources().getColor(R.color.digital_text));
            } else {
                mTextDatePaint.setColor(getResources().getColor(R.color.primary_light));
            }
            canvas.drawText(dateText, bounds.centerX() - mXOffsetDate, mYOffsetDate, mTextDatePaint);


            //Draw image for weather representation
            mTextTempLowPaint.setColor(getResources().getColor(R.color.primary_light));
            float xOffset = bounds.centerX() - (highTextSize / 2);
            canvas.drawText(mHighTemp, xOffset, mYOffsetWeather, mTextTempHighPaint);
            canvas.drawText(mLowTemp, bounds.centerX() + (highTextSize / 2) + 20, mYOffsetWeather, mTextTempLowPaint);

            // Draw icon (if not in ambient mode)
            Drawable b = getResources().getDrawable(Utility.getIconResourceForWeatherCondition(mWeatherId));
            Bitmap icon = ((BitmapDrawable) b).getBitmap();
            Log.d("Size icon ",mTextTempHighPaint.getTextSize()+"");
            float scaledWidth = ((float)45.0/ icon.getHeight()) * icon.getWidth();
            Bitmap weatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTextTempHighPaint.getTextSize(), true);
            float iconXOffset = bounds.centerX() - ((highTextSize / 2) + weatherIcon.getWidth() + 30);
            canvas.drawBitmap(weatherIcon, iconXOffset, mYOffsetWeather - weatherIcon.getHeight(), null);
            //canvas.drawBitmap(weatherIcon);

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

        //Listeners for GoogleApiClient

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Log.d("GOOGLE_API_CLIENT", "Connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("GOOGLE_API_CLIENT", "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("GOOGLE_API_CLIENT", "Connection Failed: " + connectionResult.getErrorMessage());
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(PATH_WEATHER) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mHighTemp = dataMap.getString(HIGH_TEMP);
                        mLowTemp = dataMap.getString(LOW_TEMP);
                        mWeatherId = dataMap.getInt(WEATHER_ID);
                        Log.d("WATCH_DATA", "\nHigh: " + mHighTemp + "\nLow: " + mLowTemp + "\nID: " + mWeatherId);
                        invalidate();
                    }
                }
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
