/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.example.android.sunshine.sync;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineDateUtils;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class SunshineSyncIntentService extends IntentService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener  {

    private static final String WEAR_PATH = "/wear";
    private static final String HIGH_TEMP_KEY = "high";
    private static final String LOW_TEMP_KEY = "low";
    private static final String ICON_ASSET_KEY = "key";
    GoogleApiClient mGoogleApiClient;
    Context context;
    int weatherId;
    double high;
    double low;

    public static final int INDEX_WEATHER_ID = 0;
    public static final int INDEX_MAX_TEMP = 1;
    public static final int INDEX_MIN_TEMP = 2;

    public static final String[] WEATHER_NOTIFICATION_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    public SunshineSyncIntentService() {
        super("SunshineSyncIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        Uri todaysWeatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));
        /*
         * The MAIN_FORECAST_PROJECTION array passed in as the second parameter is defined in our WeatherContract
         * class and is used to limit the columns returned in our cursor.
         */
        Uri forecastQueryUri = WeatherContract.WeatherEntry.CONTENT_URI;
                /* Sort order: Ascending by date */
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
                /*
                 * A SELECTION in SQL declares which rows you'd like to return. In our case, we
                 * want all weather data from today onwards that is stored in our weather table.
                 * We created a handy method to do that in our WeatherEntry class.
                 */
        String selection = WeatherContract.WeatherEntry.getSqlSelectForTodayOnwards();
        Cursor todayWeatherCursor = context.getContentResolver().query(
                forecastQueryUri,
                WEATHER_NOTIFICATION_PROJECTION,
                selection,
                null,
                sortOrder);
        if (todayWeatherCursor.moveToFirst()) {
            Log.d("GoogleClient","cursor move to first");
            weatherId = todayWeatherCursor.getInt(INDEX_WEATHER_ID);
            high = todayWeatherCursor.getDouble(INDEX_MAX_TEMP);
            low = todayWeatherCursor.getDouble(INDEX_MIN_TEMP);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("GoogleClient","onHandleIntent");
        SunshineSyncTask.syncWeather(this);
    }

    private void updateWear(Context context, double high, double low, int weatherId) {
        Log.d("GoogleClient","called send data");
        Bitmap icon = BitmapFactory.decodeResource(context.getResources(),
                SunshineWeatherUtils.getSmallArtResourceIdForWeatherCondition(weatherId));
        Asset asset = createAssetFromBitmap(icon);
        PutDataMapRequest dataMap = PutDataMapRequest.create(WEAR_PATH);

        dataMap.getDataMap().putString(HIGH_TEMP_KEY, SunshineWeatherUtils.formatTemperature(this, high));
        dataMap.getDataMap().putString(LOW_TEMP_KEY, SunshineWeatherUtils.formatTemperature(this, low));
        dataMap.getDataMap().putAsset(ICON_ASSET_KEY, asset);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("GoogleClient", "Connected");
        updateWear(context, high, low, weatherId);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}