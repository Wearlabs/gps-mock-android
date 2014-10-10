/*
 * Copyright (C) 2013 The Android Open Source Project
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

package io.scalac.locationprovider;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * A Service that injects test Location objects into the Location Services back-end. All other
 * apps that are connected to Location Services will see the test location values instead of
 * real values, until the test is over.
 * <p/>
 * To use this service, define the mock location values you want to use in the class
 * MockLocationConstants.java, then call this Service with startService().
 */
public class SendMockLocationService extends Service implements
        ConnectionCallbacks, OnConnectionFailedListener {

    // Object that connects the app to Location Services
    LocationClient mLocationClient;
    // A background thread for the work tasks
    HandlerThread mWorkThread;
    // Indicates if the test run has started
    private boolean mTestStarted;
    /*
     * Stores an instance of the local broadcast manager. A local
     * broadcast manager ensures security, because broadcast intents are
     * limited to the current app.
     */
    private LocalBroadcastManager mLocalBroadcastManager;
    // Stores an instance of the object that dispatches work requests to the worker thread
    private Looper mUpdateLooper;
    // The Handler instance that does the actual work
    private UpdateHandler mUpdateHandler;
    // An array of test location data
    private TestLocation[] mLocationArray;
    // The time to wait before starting to inject the test locations
    private int mPauseInterval;
    // The time to wait between each test injection
    private int mInjectionInterval;
    private String mMockId;
    // The type of test requested, either ACTION_START_ONCE or ACTION_START_CONTINUOUS
    private String mTestRequest;

    /*
     * At startup, load the static mock location data from MockLocationConstants.java, then
     * create a HandlerThread to inject the locations and start it.
     */
    @Override
    public void onCreate() {
        /*
         * Load the mock location data from MockLocationConstants.java
         */
        mLocationArray = buildTestLocationArray(LocationUtils.WAYPOINTS_LAT,
                LocationUtils.WAYPOINTS_LNG, LocationUtils.WAYPOINTS_ACCURACY);

        /*
         * Prepare to send status updates back to the main activity.
         * Get a local broadcast manager instance; broadcast intents sent via this
         * manager are only available within the this app.
         */
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        /*
         * Create a new background thread with an associated Looper that processes Message objects
         * from a MessageQueue. The Looper allows test Activities to send repeated requests to
         * inject mock locations from this Service.
         */
        mWorkThread = new HandlerThread("UpdateThread", Process.THREAD_PRIORITY_BACKGROUND);

        /*
         * Start the thread. Nothing actually runs until the Looper for this thread dispatches a
         * Message to the Handler.
         */
        mWorkThread.start();

        // Get the Looper for the thread
        mUpdateLooper = mWorkThread.getLooper();

        /*
         * Create a Handler object and pass in the Looper for the thread.
         * The Looper can now dispatch Message objects to the Handler's handleMessage() method.
         */
        mUpdateHandler = new UpdateHandler(mUpdateLooper);

        // Indicate that testing has not yet started
        mTestStarted = false;
    }

    /**
     * Post a notification to the notification bar. The title of the notification is fixed, but
     * the content comes from the input argument contentText. The notification does not contain
     * a content Intent, because the only destination it could have would be the main activity.
     * It's better to use the Recents button to go to the existing main activity.
     *
     * @param contentText Text to use for the notification content (main line of expanded
     *                    notification).
     */
    private void postNotification(String contentText) {

        // An instance of NotificationManager is needed to issue a notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        /*
         * Instantiate a new notification builder, using the API version that's backwards
         * compatible to platform version 4.
         */
        NotificationCompat.Builder builder;

        // Get the notification title
        String contentTitle = this.getString(R.string.notification_title_test_start);

        // Add values to the builder
        builder = new NotificationCompat.Builder(this)
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(contentTitle)
                .setContentText(contentText);

        /*
         * Post the notification. All notifications from InjectMockLocationService have the same
         * ID, so posting a new notification overwrites the old one.
         */
        notificationManager.notify(0, builder.build());
    }

    /**
     * Remove all notifications from the notification bar.
     */
    private void removeNotification() {

        // An instance of NotificationManager is needed to remove notifications
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Remove all notifications
        notificationManager.cancelAll();
    }

    /*
     * Since onBind is a static method, any subclass of Service must override it.
     * However, since this Service is not designed to be a bound Service, it returns null.
     */
    @Override
    public IBinder onBind(Intent inputIntent) {
        return null;
    }

    /*
     * Respond to an Intent sent by startService. onCreate() is called before this method,
     * to take care of initialization.
     *
     * This method responds to requests from the main activity to start testing.
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        // Get the type of test to run
        mTestRequest = startIntent.getAction();

        /*
         * If the incoming Intent was a request to run a one-time or continuous test
         */
        if (
                (TextUtils.equals(mTestRequest, LocationUtils.ACTION_START_ONCE))
                        ||
                        (TextUtils.equals(mTestRequest, LocationUtils.ACTION_START_CONTINUOUS))
                ) {

            // Get the pause interval and injection interval
            mPauseInterval = startIntent.getIntExtra(LocationUtils.EXTRA_PAUSE_VALUE, 2);
            mInjectionInterval = startIntent.getIntExtra(LocationUtils.EXTRA_SEND_INTERVAL, 1);
            mMockId = startIntent.getStringExtra(LocationUtils.EXTRA_MOCK_ID);

            // Post a notification in the notification bar that a test is starting
            postNotification(getString(R.string.notification_content_test_start));

            // Create a location client
            mLocationClient = new LocationClient(this, this, this);

            // Start connecting to Location Services
            mLocationClient.connect();

        } else if (TextUtils.equals(mTestRequest, LocationUtils.ACTION_STOP_TEST)) {

            // Remove any existing notifications
            removeNotification();

            // Send a message back to the main activity that the test is stopping
            sendBroadcastMessage(LocationUtils.CODE_TEST_STOPPED, 0);

            // Stop this Service
            stopSelf();
        }

        /*
         * Tell the system to keep the Service alive, but to discard the Intent that
         * started the Service
         */
        return Service.START_STICKY;
    }

    /**
     * Build an array of test location data for later use.
     *
     * @param lat_array      An array of latitude values
     * @param lng_array      An array of longitude values
     * @param accuracy_array An array of accuracy values
     * @return An array of test location data
     */
    private TestLocation[] buildTestLocationArray(double[] lat_array, double[] lng_array,
                                                  float[] accuracy_array) {

        // Temporary array of location data
        TestLocation[] location_array = new TestLocation[lat_array.length];

        /*
         * Iterate through all the arrays of data. This loop assumes that the arrays
         * all have the same length.
         */
        for (int index = 0; index < lat_array.length; index++) {

            /*
             * For each location, create a new location storage object. Set the "provider"
             * value to a number that identifies the location. This allows the tester or the
             * app under test to identify a particular location
             */
            location_array[index] =
                    new TestLocation(Integer.toString(index),
                            lat_array[index],
                            lng_array[index],
                            accuracy_array[index]);
        }

        // Return the temporary array
        return location_array;
    }

    /*
     * Invoked by Location Services if a connection could not be established.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Send connection failure broadcast to main activity
        sendBroadcastMessage(LocationUtils.CODE_CONNECTION_FAILED, result.getErrorCode());

        // Shut down. Testing can't continue until the problem is fixed.
        stopSelf();
    }

    /**
     * Send a broadcast message back to the main Activity, indicating a change in status.
     *
     * @param code1 The main status code to return
     * @param code2 A subcode for the status code, or 0.
     */
    private void sendBroadcastMessage(int code1, int code2) {
        // Create a new Intent to send back to the main Activity
        Intent sendIntent = new Intent(LocationUtils.ACTION_SERVICE_MESSAGE);

        // Put the status codes into the Intent
        sendIntent.putExtra(LocationUtils.KEY_EXTRA_CODE1, code1);
        sendIntent.putExtra(LocationUtils.KEY_EXTRA_CODE2, code2);

        // Send the Intent
        mLocalBroadcastManager.sendBroadcast(sendIntent);

    }

    /*
     * When the client is connected, Location Services calls this method, which in turn
     * starts the testing cycle by sending a message to the Handler that injects the test locations.
     */
    @Override
    public void onConnected(Bundle arg0) {
        // Send message to main activity
        sendBroadcastMessage(LocationUtils.CODE_CONNECTED, 0);
        // Start injecting mock locations into Location Services
        // Get the HandlerThread's Looper and use it for our Handler
        mUpdateLooper = mWorkThread.getLooper();
        mUpdateHandler = new UpdateHandler(mUpdateLooper);

        // Get a message object from the global pool
        Message msg = mUpdateHandler.obtainMessage();

        msg.obj = new TestParam(mTestRequest, mPauseInterval, mInjectionInterval, mMockId);

        // Fire off the injection loop
        mUpdateHandler.sendMessage(msg);
    }

    /*
     * If the client becomes disconnected without a call to LocationClient.disconnect(), Location
     * Services calls this method. If the test didn't finish, send a message to the main Activity.
     */
    @Override
    public void onDisconnected() {
        // If testing didn't finish, send an error message
        if (mTestStarted) {
            sendBroadcastMessage(LocationUtils.CODE_DISCONNECTED, LocationUtils.CODE_TEST_STOPPED);
        }
    }

    private TestLocation[] downloadLocation(String mockId) {
        DataDownloader downloader = new DataDownloader();
        String jsonStr = downloader.downloadFromUrl(getResources().getString(R.string.location_url) + "?id=" + mockId);

        double[] lat = new double[1];
        double[] lng = new double[1];
        float[] acc = new float[1];

        try {
            JSONObject json = new JSONObject(jsonStr);
            lat[0] = json.getDouble("lat");
            lng[0] = json.getDouble("lng");
            acc[0] = (float) json.getDouble("acc");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return buildTestLocationArray(lat, lng, acc);
    }

    /**
     * Convenience class for passing test parameters from the Intent received in onStartCommand()
     * via a Message to the Handler. The object makes it possible to pass the parameters through the
     * predefined Message field Message.obj.
     */
    private class TestParam {

        public final String TestAction;
        public final int TestPause;
        public final int InjectionPause;
        public final String MockId;

        public TestParam(String action, int testPause, int injectionPause, String mockId) {

            TestAction = action;
            TestPause = testPause;
            InjectionPause = injectionPause;
            MockId = mockId;
        }
    }

    /**
     * Define a class that manages the work of injecting test locations, using the Android
     * Handler API. A Handler facilitates running the work on a separate thread, so that the test
     * loop doesn't block the UI thread.
     * <p/>
     * A Handler is an object that can run code on a thread. Handler methods allow you to associate
     * the object with a Looper, which dispatches Message objects to the Handler code. In turn,
     * Message objects contain data and instructions for the Handler's code. A Handler is
     * created with a default thread and default Looper, but you can inject the Looper from another
     * thread if you want. This is often done to associate a Handler with a HandlerThread thread
     * that runs in the background.
     */

    public class UpdateHandler extends Handler {

        /**
         * Create a new Handler that uses the thread of the HandlerThread that contains the
         * provided Looper object.
         *
         * @param inputLooper The Looper object of a HandlerThread.
         */
        public UpdateHandler(Looper inputLooper) {
            // Instantiate the Handler with a Looper connected to a background thread
            super(inputLooper);

        }

        /*
         * Do the work. The Handler's Looper dispatches a Message to handleMessage(), which then
         * runs the code it contains on the thread associated with the Looper. The Message object
         * allows external callers to pass data to handleMessage().
         *
         * handleMessage() assumes that the location client already has a connection to Location
         * Services.
         */
        @Override
        public void handleMessage(Message msg) {

            boolean testOnce = false;
            // Create a new Location to inject into Location Services
            Location mockLocation = new Location(LocationUtils.LOCATION_PROVIDER);

            // Time values to put into the mock Location
            long elapsedTimeNanos;
            long currentTime;

            // Get the parameters from the Message
            TestParam params = (TestParam) msg.obj;
            String action = params.TestAction;
            int pauseInterval = params.TestPause;
            int injectionInterval = params.InjectionPause;
            String mockId = params.MockId;

            /*
             * Determine if this is a one-time run or a continuous run
             */
            if (TextUtils.equals(action, LocationUtils.ACTION_START_ONCE)) {
                testOnce = true;
            }

            // If a test run is not already in progress
            if (!mTestStarted) {

                // Flag that a test has started
                mTestStarted = true;

                // Start mock location mode in Location Services
                mLocationClient.setMockMode(true);

                // Remove the notification that testing is started
                removeNotification();

                // Add a notification that testing is in progress
                postNotification(getString(R.string.notification_content_test_running));

                /*
                 * Wait to allow the test to switch to the app under test, by putting the thread
                 * to sleep.
                 */
                try {
                    Thread.sleep((long) (pauseInterval * 1000));
                } catch (InterruptedException e) {
                    return;
                }

                // Get the device uptime and the current clock time
                elapsedTimeNanos = SystemClock.elapsedRealtimeNanos();
                currentTime = System.currentTimeMillis();

                /*
                 * Run the test loop, iterating through the array of test locations.
                 * Each test location is injected into Location Services, after which the
                 * thread is put to sleep for the requested interval.
                 *
                 * Uses a "do" loop so that one-time test and continuous test can share code.
                 */
                do {
                    mLocationArray = downloadLocation(mockId);

                    for (TestLocation aMLocationArray : mLocationArray) {
                        /*
                         * Set the time values for the test location. Both an elapsed system uptime
                         * and the current clock time in UTC timezone must be specified.
                         */
                        mockLocation.setElapsedRealtimeNanos(elapsedTimeNanos);
                        mockLocation.setTime(currentTime);

                        // Set the location accuracy, latitude, and longitude
                        mockLocation.setAccuracy(aMLocationArray.Accuracy);
                        mockLocation.setLatitude(aMLocationArray.Latitude);
                        mockLocation.setLongitude(aMLocationArray.Longitude);

                        // Inject the test location into Location Services
                        mLocationClient.setMockLocation(mockLocation);

                        // Wait for the requested update interval, by putting the thread to sleep
                        try {
                            Thread.sleep((long) (injectionInterval * 1000));
                        } catch (InterruptedException e) {
                            return;
                        }

                            /*
                             * Change the elapsed uptime and clock time by the amount of time
                             * requested.
                             */
                        elapsedTimeNanos += (long) injectionInterval *
                                LocationUtils.NANOSECONDS_PER_SECOND;
                        currentTime += injectionInterval *
                                LocationUtils.MILLISECONDS_PER_SECOND;
                    }

                /*
                 * Run the "do" while "testOnce" is false. For a one-time test, testOnce is true,
                 * so the "do" loop runs only once. For a continuous test, testOnce is false, so the
                 * "do" loop runs indefinitely.
                 */
                } while (!testOnce);

                /*
                 * Testing is finished.
                 */

                // Turn mock mode off
                mLocationClient.setMockMode(false);

                // Flag that testing has stopped
                mTestStarted = false;

                // Clear the testing notification
                removeNotification();

                // Disconnect from Location Services
                mLocationClient.disconnect();

                // Send a message back to the main activity
                sendBroadcastMessage(LocationUtils.CODE_TEST_FINISHED, 0);

                // Stop the service
                stopSelf();

                // If a test run is already in progress
            } else {
                /*
                 * The Service received a request to start testing, but a test was already in
                 * progress. Send a message back to the main Activity, and ignore the request.
                 */
                sendBroadcastMessage(LocationUtils.CODE_IN_TEST, 0);
            }
        }
    }

    private class DataDownloader {
        public String downloadFromUrl(String urlStr) {
            try {
                URL url = new URL(urlStr);
                URLConnection urlConnection = url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(50);
                int current;
                while ((current = bufferedInputStream.read()) != -1) {
                    byteArrayBuffer.append((byte) current);
                }

                return new String(byteArrayBuffer.toByteArray());
            } catch (IOException e) {
                Log.d("DataDownloader", "Error: " + e);
            }

            return "";
        }
    }

}
