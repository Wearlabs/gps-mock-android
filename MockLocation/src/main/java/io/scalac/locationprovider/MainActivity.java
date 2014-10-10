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

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Interface to the SendMockLocationService that sends mock locations into Location Services.
 * <p/>
 * This Activity collects parameters from the UI, sends them to the Service, and receives back
 * status messages from the Service.
 * <p/>
 * The following parameters are sent:
 * <ul>
 * <li><b>Type of test:</b> one-time cycle through the mock locations, or continuous sending</li>
 * <li><b>Pause interval:</b> Amount of time (in seconds) to wait before starting mock location
 * sending. This pause allows the tester to switch to the app under test before sending begins.
 * </li>
 * <li><b>Send interval:</b> Amount of time (in seconds) before sending a new location.
 * This time is unrelated to the update interval requested by the app under test. For example, the
 * app under test can request updates every second, and the tester can request a mock location
 * send every five seconds. In this case, the app under test will receive the same location 5
 * times before a new location becomes available.
 * </li>
 */
public class MainActivity extends FragmentActivity {

    // Handle to connection status reporting field in UI
    public TextView mConnectionStatus;

    // Handle to app status reporting field in UI
    public TextView mAppStatus;

    // Handle to input field for the interval to wait before starting mock location testing
    private EditText mPauseInterval;

    // Handle to input field for the interval to wait before sending a new mock location
    private EditText mSendInterval;

    private EditText mMockId;

    // Intent to send to SendMockLocationService. Contains the type of test to run
    private Intent mRequestIntent;

    /*
     * Handle to an indeterminate ProgressBar widget in the UI. Indicates that mock location
     * testing is underway.
     */
    private ProgressBar mActivityIndicator;

    /*
     * Initialize global variables and set up inner components
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Connect to the main UI
        setContentView(R.layout.activity_main);

        /*
         * Get handles to UI elements
         */
        // Connection status reporting field
        mConnectionStatus = (TextView) findViewById(R.id.connection_status);

        // App status reporting field
        mAppStatus = (TextView) findViewById(R.id.app_status);

        // Pause interval entry field
        mPauseInterval = (EditText) findViewById(R.id.pause_value);

        // Send interval entry field
        mSendInterval = (EditText) findViewById(R.id.send_interval_value);

        mMockId = (EditText) findViewById(R.id.mock_id_value);

        // Activity indicator that appears while a test run is underway
        mActivityIndicator = (ProgressBar) findViewById(R.id.testing_activity_indicator);

        // Instantiate a broadcast receiver for Intents coming from the Service
        ServiceMessageReceiver mMessageReceiver = new ServiceMessageReceiver();

        /*
         * Filter incoming Intents from the Service. Receive only Intents with a particular action
         * value.
         */
        IntentFilter filter = new IntentFilter(LocationUtils.ACTION_SERVICE_MESSAGE);

        /*
         * Restrict detection of Intents. Only Intents from other components in this app are
         * detected.
         */
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);

        // Instantiate the Intent that starts SendMockLocationService
        mRequestIntent = new Intent(this, SendMockLocationService.class);
    }

    /**
     * Respond when Run Once is clicked. Start a one-time mock location test run.
     *
     * @param v The View that was clicked
     */
    public void onStartOnceButtonClick(View v) {

        // Verify the input values and put them into global variables
        if (getInputValues()) {
            // Notify SendMockLocationService to loop once through the mock locations
            mRequestIntent.setAction(LocationUtils.ACTION_START_ONCE);

            // Set the app status field in the UI
            mAppStatus.setText(R.string.testing_started);

            // Turn on the activity indicator, to show that testing is running
            mActivityIndicator.setVisibility(View.VISIBLE);

            // Start SendMockLocationService
            startService(mRequestIntent);
        }
    }

    /**
     * Respond when Run Continuously is clicked. Start a continuous mock location test run.
     * Mock locations are sent indefinitely, until the tester clicks Stop Continuous Run.
     *
     * @param v The View that was clicked
     */
    public void onStartContinuousButtonClick(View v) {

        // Verify the input values and put them into global variables
        if (getInputValues()) {
            // Notify SendMockLocationService to loop indefinitely through the mock locations
            mRequestIntent.setAction(LocationUtils.ACTION_START_CONTINUOUS);

            // Set the app status field in the UI
            mAppStatus.setText(R.string.testing_started);

            // Turn on the activity indicator, to show that testing is running
            mActivityIndicator.setVisibility(View.VISIBLE);

            // Start SendMockLocationService
            startService(mRequestIntent);
        }
    }

    /**
     * Respond when Stop Test is clicked. Stop the current mock location test run. If the user
     * requested a one-time run with a short pause interval and fast send interval, this
     * request may have no effect, because the Service will have already stopped.
     *
     * @param v The View that was clicked
     */
    public void onStopButtonClick(View v) {

        // Stop SendMockLocationService
        mRequestIntent.setAction(LocationUtils.ACTION_STOP_TEST);

        // If SendMockLocationService is running
        if (null != startService(mRequestIntent)) {

            // Update app status to show that a request was sent to stop the Service
            mAppStatus.setText(R.string.stop_service);

        } else {

            // Update app status to show that the Service isn't running
            mAppStatus.setText(R.string.no_service);
        }

        // Update connection status to show that the connection was destroyed
        mConnectionStatus.setText(R.string.disconnected);

        // Turn off the activity indicator
        mActivityIndicator.setVisibility(View.GONE);
    }

    /**
     * Verify the pause interval and send interval from the UI. If they're correct, store
     * them in the Intent that's used to start SendMockLocationService
     *
     * @return true if all the input values are correct; otherwise false
     */
    public boolean getInputValues() {

        // Get the values from the UI
        String pauseIntervalText = mPauseInterval.getText().toString();
        String sendIntervalText = mSendInterval.getText().toString();
        String mockIdText = mMockId.getText().toString();

        if (TextUtils.isEmpty(pauseIntervalText)) {

            // Report that the pause interval is empty
            mAppStatus.setText(R.string.pause_interval_empty);
            return false;
        } else if (Integer.valueOf(pauseIntervalText) <= 0) {

            // Report that the pause interval is not a positive number
            mAppStatus.setText(R.string.pause_interval_not_positive);
            return false;
        }

        if (TextUtils.isEmpty(sendIntervalText)) {

            mAppStatus.setText(R.string.send_entry_empty);
            return false;
        } else if (Integer.valueOf(sendIntervalText) <= 0) {

            // Report that the send interval is not a positive number
            mAppStatus.setText(R.string.send_interval_not_positive);
            return false;
        }

        if (TextUtils.isEmpty(mockIdText)) {
            mAppStatus.setText(R.string.mock_id_empty);
            return false;
        }

        int pauseValue = Integer.valueOf(pauseIntervalText);
        int sendValue = Integer.valueOf(sendIntervalText);

        mRequestIntent.putExtra(LocationUtils.EXTRA_PAUSE_VALUE, pauseValue);
        mRequestIntent.putExtra(LocationUtils.EXTRA_SEND_INTERVAL, sendValue);
        mRequestIntent.putExtra(LocationUtils.EXTRA_MOCK_ID, mockIdText);

        return true;
    }

    /**
     * Broadcast receiver triggered by broadcast Intents within this app that match the
     * receiver's filter (see onCreate())
     */
    private class ServiceMessageReceiver extends BroadcastReceiver {

        /*
         * Invoked when a broadcast Intent from SendMockLocationService arrives
         *
         * context is the Context of the app
         * intent is the Intent object that triggered the receiver
         */
        @Override
        public void onReceive(Context context, Intent intent) {

            // Get the message code from the incoming Intent
            int code1 = intent.getIntExtra(LocationUtils.KEY_EXTRA_CODE1, 0);
            int code2 = intent.getIntExtra(LocationUtils.KEY_EXTRA_CODE2, 0);

            // Choose the action, based on the message code
            switch (code1) {
                /*
                 * SendMockLocationService reported that the location client is connected. Update
                 * the app status reporting field in the UI.
                 */
                case LocationUtils.CODE_CONNECTED:
                    mConnectionStatus.setText(R.string.connected);
                    break;

                /*
                 * SendMockLocationService reported that the location client disconnected. This
                 * happens if Location Services drops the connection. Update the app status and the
                 * connection status reporting fields in the UI.
                 */
                case LocationUtils.CODE_DISCONNECTED:
                    mConnectionStatus.setText(R.string.disconnected);
                    mAppStatus.setText(R.string.notification_content_test_stop);
                    break;

                /*
                 * SendMockLocationService reported that an attempt to connect to Location
                 * Services failed. Testing can't continue. The Service has already stopped itself.
                 * Update the connection status reporting field and include the error code.
                 * Also update the app status field
                 */
                case LocationUtils.CODE_CONNECTION_FAILED:
                    mActivityIndicator.setVisibility(View.GONE);
                    mConnectionStatus.setText(
                            context.getString(R.string.connection_failure, code2));
                    mAppStatus.setText(R.string.location_test_finish);
                    break;

                /*
                 * SendMockLocationService reported that the tester requested a test, but a test
                 * is already underway. Update the app status reporting field.
                 */
                case LocationUtils.CODE_IN_TEST:
                    mAppStatus.setText(R.string.not_continuous_test);
                    break;

                /*
                 * SendMockLocationService reported that the test run finished. Turn off the
                 * progress indicator, update the app status reporting field and the connection
                 * status reporting field. Since this message can only occur if
                 * SendMockLocationService disconnected the client, the connection status is
                 * "disconnected".
                 */
                case LocationUtils.CODE_TEST_FINISHED:
                    mActivityIndicator.setVisibility(View.GONE);
                    mAppStatus.setText(context.getText(R.string.location_test_finish));
                    mConnectionStatus.setText(R.string.disconnected);
                    break;

                /*
                 * SendMockLocationService reported that the tester interrupted the test.
                 * Turn off the activity indicator and update the app status reporting field.
                 */
                case LocationUtils.CODE_TEST_STOPPED:
                    mActivityIndicator.setVisibility(View.GONE);
                    mAppStatus.setText(R.string.test_interrupted);
                    break;

                /*
                 * An unknown broadcast Intent was received. Log an error.
                 */
                default:
                    Log.e(LocationUtils.APPTAG, getString(R.string.invalid_broadcast_code));
                    break;
            }
        }
    }
}
