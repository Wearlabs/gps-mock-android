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

/**
 * Object that stores Location settings that should change between test location
 * injections. Other Location settings are calculated for each injection, or don't
 * need to be specified.
 */
public class TestLocation {

    // Member fields
    public final double Latitude;
    public final double Longitude;
    public final float Accuracy;
    public final String Id;

    /**
     * Primary constructor. Create an object for a set of test location settings
     *
     * @param id        Identifies this location. Used as the test Location object's provider
     * @param latitude  The test location's latitude
     * @param longitude The test location's longitude
     * @param accuracy  The accuracy of the test location data
     */
    public TestLocation(String id, double latitude, double longitude, float accuracy) {

        Id = id;
        Latitude = latitude;
        Longitude = longitude;
        Accuracy = accuracy;
    }

    /**
     * Default constructor. Initialize everything to reasonable values.
     */
    public TestLocation() {

        Id = "test";
        Latitude = 37.4199338;
        Longitude = -122.0818539;
        Accuracy = 3.0f;
    }
}
