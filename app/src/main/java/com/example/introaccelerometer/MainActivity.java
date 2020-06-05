package com.example.introaccelerometer;

import android.Manifest;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.Math;
import java.util.List;
import java.util.Locale;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import android.app.AlertDialog;
import android.content.Context;
import android.os.CountDownTimer;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements SensorEventListener{
    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    TextToSpeech textToSpeech;
    public static final  String SHARED_PREFS = "sharedPrefs";
    public  static final String USER = "User";
    public  static final String CONTACT = "Contact";
    public  static final String NUMBER = "Number";
    private String userName;
    private String contactName;
    private String number;
    private String message;
    private Chronometer clock;
    private Sensor mySensor;
    private SensorManager SM;
    private LineGraphSeries<DataPoint> series, limit;
    private static double currentX;
    private ThreadPoolExecutor liveChartExecutor;
    private LinkedBlockingQueue<Double> accelerationQueue = new LinkedBlockingQueue<>(10);
    Context context = this;
    public boolean dialog;
    private AlertDialog alert111;
    private boolean responded = false;
    //add enum for states
    private enum State{
        normal, falling, crash, still, help
    }
    private final double MIN_THRESHOLD = 0.6;
    private final double MAX_THRESHOLD = 1.1;
    private final double COLLISION_THRESHOLD = 3;
    //min time to fall
    private final int FALLEN = 200;
    //min time still to show dialog
    private final int HELP_ME = 2500;
    private final int TIME_OUT = 30000;

    private float x;
    private float y;
    private float z;
    private float g;
    private String emergency = "Fall Detected. Do you need help? We will contact your emergency person if you do not respond ";
    private State phone = State.normal;
    CountDownTimer timeout;

    private TextView xView, yView, zView, gView, milliView, stateView,textViewUser, textViewContact, textViewNumber;
    public EditText editTextUser, editTextContact, editTextNumber;

    //Set up sensors and text views
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    //Contact details text view
        Button editBtn = (Button)findViewById(R.id.editBtn);
        Button state = (Button)findViewById(R.id.buttonState);
        textViewUser = findViewById(R.id.textViewUser);
        textViewContact = findViewById(R.id.textViewContact);
        textViewNumber = findViewById(R.id.textViewNumber);

        state.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phone = State.help;
            }
        });
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditDialog();
            }
        });


    //Text to speech implementation
        textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                //if there is no error then set the language
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.ENGLISH);
                   textToSpeech.setSpeechRate((float)0.80);
                }
            }
        });



        //Create sensor manager
        SM = (SensorManager)getSystemService(SENSOR_SERVICE);

        //Accelerometer graph set up
        final GraphView graph = (GraphView) findViewById(R.id.graph);
        series = new LineGraphSeries<>();

        series.setColor(Color.rgb(57,192,186));
        graph.addSeries(series);
        graph.setBackgroundColor(Color.TRANSPARENT);
        graph.setTitleColor(Color.WHITE);
        graph.getViewport().setScalable(true);
        graph.getGridLabelRenderer().setGridColor(Color.DKGRAY);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        // activate horizontal scrolling
        graph.getViewport().setScrollable(true);
        // activate horizontal and vertical zooming and scrolling
        graph.getViewport().setScalableY(true);
        // activate vertical scrolling
        graph.getViewport().setScrollableY(true);
        // To set a fixed manual viewport use this:
        // set manual X bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0.5);
        graph.getViewport().setMaxX(6.5);
        // set manual Y bounds
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(3);

        currentX = 0;
        // Start chart thread
        liveChartExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        if (liveChartExecutor != null)
            liveChartExecutor.execute(new AccelerationChart(new AccelerationChartHandler()));



        //Accelerometer Sensor
        mySensor = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //Register Sensor Listener
        SM.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_NORMAL);

        //Assign TextViews for phone sate and timer
        stateView = findViewById(R.id.stateView);
        clock = findViewById(R.id.clock);
        clock.setFormat("Time since freefall: %s");

        //Load saved emergency contact details data
        loadData();
        updateViews();
    }



    public boolean checkPermission(String permission){
        int check = ContextCompat.checkSelfPermission(this, permission);
        return  (check == PackageManager.PERMISSION_GRANTED);
    }

    //Edit contact details
    public void EditDialog(){
        AlertDialog.Builder builderD = new AlertDialog.Builder(context);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.layout_dialog, null);
        builderD.setView(view)
                .setTitle("Edit Details")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                //Get text from edit text in dialog into display textview
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        textViewUser.setText(editTextUser.getText().toString());
                        textViewContact.setText(editTextContact.getText().toString());
                        textViewNumber.setText(editTextNumber.getText().toString());
                        saveData();
                    }
                });
        //Set up editTexts
        editTextUser = view.findViewById(R.id.editTextUser);
        editTextContact = view.findViewById(R.id.editTextContact);
        editTextNumber = view.findViewById(R.id.editTextNumber);
        //When edit button pressed, preset edittext into current details
        editTextUser.setText(textViewUser.getText().toString());
        editTextContact.setText(textViewContact.getText().toString());
        editTextNumber.setText(textViewNumber.getText().toString());
        builderD.create();
        builderD.show();
    }


    //Saving emergency contact details even when app is closed using shared preferences
    public void saveData(){
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(USER, textViewUser.getText().toString());
        editor.putString(CONTACT, textViewContact.getText().toString());
        editor.putString(NUMBER, textViewNumber.getText().toString());

        editor.apply();
        Toast.makeText(this, "Details Saved", Toast.LENGTH_SHORT).show();
    }
    //Loading shared preferences
    public void loadData(){
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        userName = sharedPreferences.getString(USER, "");
        contactName = sharedPreferences.getString(CONTACT, "");
        number = sharedPreferences.getString(NUMBER, "");
    }
    //Update from loaded values
    public void updateViews() {
        textViewUser.setText(userName);
        textViewContact.setText(contactName);
        textViewNumber.setText(number);
    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
        //get values
        double rootSum;
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];
        rootSum = Math.sqrt((x*x) + (y*y) + (z*z));
        g = (float)(rootSum/9.8);

        //won't need

        stateView.setText("State: " + phone.toString());
        //check state
        states();
    }
    private boolean appInForeground() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
        if (runningAppProcesses == null) {
            return false;
        }

        for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
            if (runningAppProcess.processName.equals(context.getPackageName()) &&
                    runningAppProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    public void states()
    {
        //need help, pop up dialog and wait [add in wait]
        if (phone == State.help && !dialog) {
            //Open app and alert dialog even when app is not opened
            if (!appInForeground()) {
                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                startActivity(intent);
            }

            final AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            //When fall is detected, display alert dialog and speech to text emergency message
            builder1.setTitle("Fall Detected!");
            builder1.setMessage(emergency);
            if (!textToSpeech.isSpeaking()) {
                textToSpeech.speak(emergency, TextToSpeech.QUEUE_FLUSH, null);
            }

            builder1.setCancelable(false);
            //If needs help prompt emergency contact message
            builder1.setPositiveButton(
                    "Yes, get help immediately",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            responded = true;
                            EmergencyContact();
                            dialog.cancel();
                        }
                    });
           //Apology message on false alarms
            builder1.setNeutralButton(
                    "I fell but I'm okay",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //alert111.cancel();
                            textToSpeech.speak("You should make sure you're okay.", TextToSpeech.QUEUE_FLUSH, null);
                            responded = true;
                            dialog.cancel();
                            Initialise();
                        }
                    });
            //Safety reassurance message on minor falls
            builder1.setNegativeButton(
                    "No, I haven't fallen",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            textToSpeech.speak("I'm sorry. False alarm!", TextToSpeech.QUEUE_FLUSH, null);
                            responded = true;
                            dialog.cancel();
                            Initialise();
                        }
                    });
            alert111 = builder1.create();
            alert111.show();

            //Set dialog text sizes
            Button no = alert111.getButton((DialogInterface.BUTTON_NEGATIVE));
            no.setTextSize(15);
            Button ok = alert111.getButton((DialogInterface.BUTTON_NEUTRAL));
            ok.setTextSize(15);
            Button yes = alert111.getButton((DialogInterface.BUTTON_POSITIVE));
            yes.setTextSize(15);
            TextView fallView = alert111.findViewById(android.R.id.message);
            fallView.setTextSize(20);

            if (timeout != null){
                timeout.cancel();
            }
            timeout = new CountDownTimer(TIME_OUT, 1000) {

                public void onTick(long millisUntilFinished) {
                    alert111.setMessage(emergency + "within " + millisUntilFinished / 1000 + " seconds.");
                }
                //If times out without response then prompt emergency contact message
                public void onFinish() {
                    alert111.dismiss();
                    if (responded == false)
                    {
                        EmergencyContact();
                        responded = false;
                    }

                }
            }.start();
            dialog = true;

        }
        //freefall
        else if (g < MIN_THRESHOLD)
        {
            switch(phone) {
                case normal:
                    //phone started falling so start clock
                    clock.setBase(SystemClock.elapsedRealtime());
                    clock.start();
                    phone = State.falling;
                    break;
            }
        }
        //Collision
        else if (g >= COLLISION_THRESHOLD && phone == State.falling) {
            long elapsedMillis = SystemClock.elapsedRealtime() - clock.getBase();
            //if fallen for long enough, classed as collision, continue
            if (elapsedMillis > FALLEN)
                phone = State.crash;
            else
            {
                //not a fall
                Initialise();
                return;
            }
        }
        //still
        else if (g >= MIN_THRESHOLD && g <= MAX_THRESHOLD)
        {
            switch(phone) {
                case crash:
                    //start new timer
                    clock.setBase(SystemClock.elapsedRealtime());
                    phone = State.still;
                    break;
                case still:
                    // get help
                    long elapsedMillis = SystemClock.elapsedRealtime() - clock.getBase();
                    if (elapsedMillis > HELP_ME)
                    {
                        phone = State.help;
                    }
                    break;
                case falling:
                    //low impact, probably a drop
                    Initialise();
            }
        }
    }

    //reset
    public void Initialise()
    {
        clock.stop();
        clock.setBase(SystemClock.elapsedRealtime());
        phone = State.normal;
        dialog = false;
    }

    //Gathers location of fall and display emergency contact message to specified contact
    public void EmergencyContact()
    {
        //Set up location access and get permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            //TODO: Consider calling
            // public void requestPermissions(@NonNull String[] permissions, int requestCode)
            // here to request the missing permissions, and then overriding
            // public void onRequesetPermissionResult(int requestCode, String[] permissions,
            //                                            int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, new Listener());
        // Have another for GPS provider just in case.
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, new Listener());
        // Try to request the location immediately
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (location != null) {
            handleLatLng(location.getLatitude(), location.getLongitude());
        }
        //Set emergency message when location or wifi is turned off
        if( !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)  || !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
            message = userName + " has fallen and requires assistance";
            Toast.makeText(getApplicationContext(),
                    "Allow Location permission and turn on High Accuracy setting to send address to contact.",
                    Toast.LENGTH_LONG).show();
        }
        //Emergency message with location
        else{
            message = userName + " has fallen and requires assistance at\n" +getAddress(location.getLatitude(),location.getLongitude());
            Toast.makeText(getApplicationContext(),
                    "Trying to obtain GPS coordinates. Make sure you have location services on. On High Accuracy",
                    Toast.LENGTH_SHORT).show();
        }
        //Display emergency contact alert dialog
        textToSpeech.speak("Contacting help..", TextToSpeech.QUEUE_FLUSH, null);		        textToSpeech.speak("Contacting help..", TextToSpeech.QUEUE_FLUSH, null);
        contactName = textViewContact.getText().toString();
        contactName = textViewContact.getText().toString();
        userName = textViewUser.getText().toString();

        AlertDialog.Builder builder3 = new AlertDialog.Builder(context)
                .setTitle("Sending Message to " + contactName)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(
                        "Thank you",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                Initialise();
                            }
                        });
        AlertDialog contact = builder3.create();
        contact.show();
        Button yes = contact.getButton((DialogInterface.BUTTON_POSITIVE));
        yes.setTextSize(15);
        TextView fallView = contact.findViewById(android.R.id.message);
        fallView.setTextSize(20);



    }
    //Alternative display emergency contact message to specified contact when location permission denied
    public void EmergencyNoLocation(){

        textToSpeech.speak("Contacting help..", TextToSpeech.QUEUE_FLUSH, null);		        textToSpeech.speak("Contacting help..", TextToSpeech.QUEUE_FLUSH, null);
        contactName = textViewContact.getText().toString();
        contactName = textViewContact.getText().toString();
        userName = textViewUser.getText().toString();
        message = userName + " has fallen and requires assistance";
        Toast.makeText(getApplicationContext(),
                "Allow Location permission and turn on High Accuracy setting to send address to contact.",
                Toast.LENGTH_LONG).show();
        AlertDialog.Builder builder3 = new AlertDialog.Builder(context)
                .setTitle("Sending Message to " + contactName)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(
                        "Thank you",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                Initialise();
                            }
                        });
        AlertDialog contact = builder3.create();
        contact.show();
        Button yes = contact.getButton((DialogInterface.BUTTON_POSITIVE));
        yes.setTextSize(15);
        TextView fallView = contact.findViewById(android.R.id.message);
        fallView.setTextSize(20);


    }

    public void handleLatLng(double latitude, double longitude){
        Log.v("TAG", "(" + latitude + "," + longitude + ")");

    }

    //Reverse geocoding to obtain current address
    private String getAddress(double latitude, double longitude) {
        String address = "";
        String city = "";
        Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude,longitude, 1);
            address = addresses.get(0).getAddressLine(0);
            city = addresses.get(0).getLocality();
            Log.d("TAG", "Complete Address: " + addresses.toString());
            Log.d("TAG", "Address: " + address);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return address;
    }

    public class Listener extends AppCompatActivity implements LocationListener {
        public void onLocationChanged(Location location) {
            double  lon = location.getLongitude();
            double  lat = location.getLatitude();
            handleLatLng(lat, lon);
        }
        public void onProviderDisabled(String provider){}
        public void onProviderEnabled(String provider){}
        public void onStatusChanged(String provider, int status, Bundle extras){}
    }

    //Location permission request
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this,
                            "Location Access Granted",
                            Toast.LENGTH_LONG).show();
                    EmergencyContact();


                } else {
                    Toast.makeText(MainActivity.this,
                            "Location Access Denied",
                            Toast.LENGTH_LONG).show();
                    EmergencyNoLocation();
                }
              return;
            }
        }
    }
    //Calculations for accelerometor graph display
    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        double x = values[0];
        double y = values[1];
        double z = values[2];

        double accelerationSquareRoot = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        double acceleration = Math.sqrt(accelerationSquareRoot);

        accelerationQueue.offer(acceleration);
    }
    //Updates accelerometer graph
    private class AccelerationChartHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Double accelerationY = 0.0D;
            if (!msg.getData().getString("ACCELERATION_VALUE").equals(null) && !msg.getData().getString("ACCELERATION_VALUE").equals("null")) {
                accelerationY = (Double.parseDouble(msg.getData().getString("ACCELERATION_VALUE")));
            }
                series.appendData(new DataPoint(currentX, accelerationY), true, 10);
                    currentX = currentX + 1;
        }
    }
    //Live implementation of graph
    private class AccelerationChart implements Runnable {
        private boolean drawChart = true;
        private Handler handler;

        public AccelerationChart(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            while (drawChart) {
                Double accelerationY;
                try {
                    Thread.sleep(300); // Speed up the X axis
                    accelerationY = accelerationQueue.poll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (accelerationY == null)
                    continue;

                Message msgObj = handler.obtainMessage();
                Bundle b = new Bundle();
                b.putString("ACCELERATION_VALUE", String.valueOf(accelerationY));
                msgObj.setData(b);
                handler.sendMessage(msgObj);
            }
        }
    }
@Override
public void onAccuracyChanged(Sensor sensor, int accuracy) {
    //not in use
}
}
