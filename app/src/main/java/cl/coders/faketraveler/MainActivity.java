package cl.coders.faketraveler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.NONE;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    static final String sharedPrefKey = "cl.coders.mockposition.sharedpreferences";
    static final int KEEP_GOING = 0;
    static private final int SCHEDULE_REQUEST_CODE = 101;
    static Button button0;
    static Button button1;
    static Button button2;
    static WebView webView;
    static EditText editTextLat;
    static EditText editTextLng;
    static Context context;
    static SharedPreferences sharedPref;
    static SharedPreferences.Editor editor;
    static Double lat;
    static Double lng;
    static Double llat;
    static Double llng;
    static int timeInterval;
    static int speedLimit;
    static int howManyTimes;
    static long endTime;
    static int currentVersion;
    private static MockLocationProvider mockNetwork;
    private static MockLocationProvider mockGps;

    WebAppInterface webAppInterface;

    public enum SourceChange {
        NONE, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP
    }

    static SourceChange srcChange = NONE;

    static int index = 0;
    static boolean active = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        webView = findViewById(R.id.webView0);
        webAppInterface = new WebAppInterface(this, this);
        sharedPref = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        button0 = (Button) findViewById(R.id.button0);
        button1 = (Button) findViewById(R.id.button1);
        button2 = (Button) findViewById(R.id.button2);
        editTextLat = findViewById(R.id.editText0);
        editTextLng = findViewById(R.id.editText1);

        button0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                applyLocation();
            }
        });

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent myIntent = new Intent(getBaseContext(), MoreActivity.class);
                startActivity(myIntent);
            }
        });

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(webAppInterface, "Android");
        webView.loadUrl("file:///android_asset/map.html");

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        checkSharedPrefs();
        loadPref(sharedPref);

        try {
            editTextLat.setText(String.format(Locale.getDefault(), "%f", lat));
            editTextLng.setText(String.format(Locale.getDefault(), "%f", lng));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        editTextLat.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLat.getText().toString().isEmpty() && !editTextLat.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lat = Double.parseDouble((editTextLat.getText().toString()));

                        if (lng == null)
                            return;

                        setLatLng(editTextLat.getText().toString(), lng.toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        editTextLng.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLng.getText().toString().isEmpty() && !editTextLng.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lng = Double.parseDouble((editTextLng.getText().toString()));

                        if (lat == null)
                            return;

                        setLatLng(lat.toString(), editTextLng.getText().toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        if (TickService.active && endTime > System.currentTimeMillis()) {
            changeButtonToStop();
        } else {
            endTime = 0;
            editor.putLong("endTime", 0);
            editor.commit();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            button2.setVisibility(View.VISIBLE);
            button2.setOnClickListener((v) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/xml", "application/vnd.google-earth.kml+xml"});
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Open File"), SCHEDULE_REQUEST_CODE);
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        toast(context.getResources().getString(R.string.ApplyMockBroadRec_Closed));
        stopMockingLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isFinishing()) {
            toast(context.getResources().getString(R.string.ApplyMockBroadRec_Closed));
            stopMockingLocation();
        }
    }

    /**
     * Check and reinitialize shared preferences in case of problem.
     */
    static void checkSharedPrefs() {
        int version = sharedPref.getInt("version", 0);
        String lat = sharedPref.getString("lat", "N/A");
        String lng = sharedPref.getString("lng", "N/A");
        String llat = sharedPref.getString("llat", "N/A");
        String llng = sharedPref.getString("llng", "N/A");
        String howManyTimes = sharedPref.getString("howManyTimes", "N/A");
        String timeInterval = sharedPref.getString("timeInterval", "N/A");
        String speedLimit = sharedPref.getString("speedLimit", "N/A");
        //Long endTime = sharedPref.getLong("endTime", 0);

        if (version != currentVersion) {
            editor.putInt("version", currentVersion);
            editor.commit();
        }

        try {
            Double.parseDouble(lat);
            Double.parseDouble(lng);
            Double.parseDouble(llat);
            Double.parseDouble(llng);
            Double.parseDouble(howManyTimes);
            Double.parseDouble(timeInterval);
            Double.parseDouble(speedLimit);
        } catch (NumberFormatException e) {
            editor.clear();
            editor.putString("lat", lat);
            editor.putString("lng", lng);
            editor.putString("llat", lat);
            editor.putString("llng", lng);
            editor.putInt("version", currentVersion);
            editor.putString("howManyTimes", "0");
            editor.putString("timeInterval", "1000");
            editor.putString("speedLimit", "40");
            editor.putLong("endTime", 0);
            editor.putString("list", "");
            editor.commit();
            e.printStackTrace();
        }

    }

    static void loadPref(SharedPreferences sharedPref) {
        try {
            howManyTimes = Integer.parseInt(sharedPref.getString("howManyTimes", "0"));
        } catch (NumberFormatException ignore) {}
        try {
            timeInterval = Integer.parseInt(sharedPref.getString("timeInterval", "1000"));
        } catch (NumberFormatException ignore) {}
        if (timeInterval < 100) {
            timeInterval = 100;
        }
        try {
            speedLimit = Integer.parseInt(sharedPref.getString("speedLimit", "40"));
        } catch (NumberFormatException ignore) {}
        if (speedLimit < 1) {
            speedLimit = 1;
        }

        try {
            lat = Double.parseDouble(sharedPref.getString("lat", ""));
            lng = Double.parseDouble(sharedPref.getString("lng", ""));
        } catch (NumberFormatException e) {
        }
        try {
            llat = Double.parseDouble(sharedPref.getString("llat", ""));
            llng = Double.parseDouble(sharedPref.getString("llng", ""));
        } catch (NumberFormatException e) {
        }
        if (llat == null) llat = lat;
        if (llng == null) llng = lng;

        endTime = sharedPref.getLong("endTime", 0);
    }
    /**
     * Apply a mocked location, and start an alarm to keep doing it if howManyTimes is > 1
     * This method is called when "Apply" button is pressed.
     */
    protected static void applyLocation() {
        if (latIsEmpty() || lngIsEmpty()) {
            toast(context.getResources().getString(R.string.MainActivity_NoLatLong));
            return;
        }

        lat = Double.parseDouble(editTextLat.getText().toString());
        lng = Double.parseDouble(editTextLng.getText().toString());

        toast(context.getResources().getString(R.string.MainActivity_MockApplied));

        endTime = System.currentTimeMillis() + (long) (howManyTimes - 1) * timeInterval;
        editor.putLong("endTime", endTime);
        editor.commit();

        changeButtonToStop();

        try {
            mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
        } catch (SecurityException e) {
            e.printStackTrace();
            MainActivity.toast(context.getResources().getString(R.string.ApplyMockBroadRec_MockNotApplied));
            stopMockingLocation();
            return;
        }

        active = true;
        exec(lat, lng,0 ,1 );

        if (!hasEnded() && !TickService.active) {
            toast(context.getResources().getString(R.string.MainActivity_MockLocRunning));
            TickService.startTick(context);
        } else {
            stopMockingLocation();
        }
    }

    /**
     * Set a mocked location.
     *
     * @param lat         latitude
     * @param lng         longitude
     * @param speed       speed
     * @param orientation azimuth
     */
    static void exec(double lat, double lng, float speed, float orientation) {
        if (!active) return;
        try {
            Log.d(TickService.TAG,String.format("%f,%f,%f,%f",lat,lng,speed,orientation));
            mockNetwork.pushLocation(lat, lng, speed, orientation);
            mockGps.pushLocation(lat, lng, speed, orientation);

            llat = lat;
            llng = lng;
            editor.putString("llat", llat.toString());
            editor.putString("llng", llng.toString());
            editor.commit();
        } catch (Exception e) {
            e.printStackTrace();
            toast(context.getResources().getString(R.string.MainActivity_MockNotApplied));
            changeButtonToApply();
        }
    }

    /**
     * Check if mocking location should be stopped
     *
     * @return true if it has ended
     */
    static boolean hasEnded() {
        if (howManyTimes == KEEP_GOING) {
            return false;
        } else {
            return System.currentTimeMillis() > endTime;
        }
    }

    /**
     * Shows a toast
     */
    static void toast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_LONG).show();
    }

    /**
     * Returns true editTextLat has no text
     */
    static boolean latIsEmpty() {
        return editTextLat.getText().toString().isEmpty();
    }

    /**
     * Returns true editTextLng has no text
     */
    static boolean lngIsEmpty() {
        return editTextLng.getText().toString().isEmpty();
    }

    /**
     * Stops mocking the location.
     */
    protected static void stopMockingLocation() {
        changeButtonToApply();
        editor.putLong("endTime", System.currentTimeMillis() - 1);
        editor.putString("list", "");
        editor.commit();
        index = 0;

        if (TickService.active) {
            TickService.stopTick(context);
            toast(context.getResources().getString(R.string.MainActivity_MockStopped));
        }

        if (mockNetwork != null)
            mockNetwork.shutdown();
        if (mockGps != null)
            mockGps.shutdown();
        active = false;
    }

    /**
     * Changes the button to Apply, and its behavior.
     */
    static void changeButtonToApply() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Apply));
        button0.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                applyLocation();
            }

        });
    }

    /**
     * Changes the button to Stop, and its behavior.
     */
    static void changeButtonToStop() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        button0.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                stopMockingLocation();
            }

        });
    }

    /**
     * Sets latitude and longitude
     *
     * @param mLat      latitude
     * @param mLng      longitude
     * @param srcChange CHANGE_FROM_EDITTEXT or CHANGE_FROM_MAP, indicates from where comes the change
     */
    static void setLatLng(String mLat, String mLng, SourceChange srcChange) {
        lat = Double.parseDouble(mLat);
        lng = Double.parseDouble(mLng);

        if (srcChange == CHANGE_FROM_EDITTEXT) {
            webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
        } else if (srcChange == CHANGE_FROM_MAP) {
            MainActivity.srcChange = CHANGE_FROM_MAP;
            editTextLat.setText(mLat);
            editTextLng.setText(mLng);
            MainActivity.srcChange = NONE;
        }

        editor.putString("lat", mLat);
        editor.putString("lng", mLng);
        editor.commit();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLat() {
        return editTextLat.getText().toString();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLng() {
        return editTextLng.getText().toString();
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCHEDULE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            stopMockingLocation();
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    boolean gotPos = false;
                    InputStream file = getContentResolver().openInputStream(uri);
                    XmlPullParser p = Xml.newPullParser();
                    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);
                    p.setInput(file,null);
                    p.nextTag();
                    p.require(XmlPullParser.START_TAG, null,"kml");
                    while (p.next() != XmlPullParser.END_TAG) {
                        if (p.getEventType() != XmlPullParser.START_TAG) {
                            continue;
                        }
                        if (!"Document".equals(p.getName())) {
                            skip(p);
                            continue;
                        }
                        p.require(XmlPullParser.START_TAG, null, "Document");
                        while (p.next() != XmlPullParser.END_TAG) {
                            if (p.getEventType() != XmlPullParser.START_TAG) {
                                continue;
                            }
                            if (!"Placemark".equals(p.getName())) {
                                skip(p);
                                continue;
                            }
                            p.require(XmlPullParser.START_TAG, null, "Placemark");
                            while (p.next() != XmlPullParser.END_TAG) {
                                if (p.getEventType() != XmlPullParser.START_TAG) {
                                    continue;
                                }
                                if (!"LineString".equals(p.getName())) {
                                    skip(p);
                                    continue;
                                }
                                p.require(XmlPullParser.START_TAG, null, "LineString");
                                while (p.next() != XmlPullParser.END_TAG) {
                                    if (p.getEventType() != XmlPullParser.START_TAG) {
                                        continue;
                                    }
                                    if (!"coordinates".equals(p.getName())) {
                                        skip(p);
                                        continue;
                                    }
                                    if (p.next() == XmlPullParser.TEXT) {
                                        String result = p.getText();
                                        String[] lines = result.split("\n");
                                        if (lines.length > 0) {
                                            StringBuilder sb = new StringBuilder();
                                            for (String line:lines) {
                                                String t = line.trim();
                                                int i = t.lastIndexOf(",");
                                                if (i>0) {
                                                    String v=t.substring(0, i);
                                                    sb.append(v).append("\n");
                                                    if (!gotPos) {
                                                        String[] xy = v.split(",");
                                                        lat = Double.parseDouble(xy[1]);
                                                        editTextLat.setText(xy[1]);
                                                        lng = Double.parseDouble(xy[0]);
                                                        editTextLng.setText(xy[0]);
                                                    }
                                                    gotPos = true;
                                                }
                                            }
                                            editor.putString("list", sb.toString());
                                        }
                                        p.nextTag();
                                    }
                                }
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (XmlPullParserException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}