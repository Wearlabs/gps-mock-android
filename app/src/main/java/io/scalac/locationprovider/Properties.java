package io.scalac.locationprovider;

import java.io.IOException;
import java.io.InputStream;

/**
 * Save FoW environment related data
 */
public class Properties {
    final static String TAG = "Properties";

    private static java.util.Properties props = new java.util.Properties();

    static {
        InputStream inputStream = Properties.class.getClassLoader().getResourceAsStream("res/raw/app.properties");
        try {
            props.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getString(String propName, String defaultValue) {
        return props.getProperty(propName, defaultValue);
    }
}
