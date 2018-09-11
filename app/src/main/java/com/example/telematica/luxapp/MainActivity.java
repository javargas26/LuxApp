package com.example.telematica.luxapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static android.content.ContentValues.TAG;
import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MainActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks,LocationListener, OnMapReadyCallback{

    Button btnSensor, btnGPS, btnSend, btnClear;
    TextView sensorView, tvValueLatitud, tvValueLongitude;
    String url= "https://streetlightdata.000webhostapp.com/write_data.php";
    Handler bluetoothIn;

    final int handlerState = 0;             //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();
    private ConnectedThread mConnectedThread;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address
    private static String address = null;

    LocationManager locationManager;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    // Creating Volley RequestQueue.
    RequestQueue requestQueue;

    // Create string variable to hold the EditText Value.
    String ValueHolder, LatHolder, LonHolder, StateHolder ;
    Float StateValue;

    // Creating Progress dialog.
    ProgressDialog progressDialog;


    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Marker mLocationMarker;

    final int MY_PERMISSIONS_REQUEST_LOCATION = 1;


    @SuppressLint("HandlerLeak")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


//        MapFragment mapFragment = (MapFragment) getFragmentManager()
//               .findFragmentById(R.id.map);
//        mapFragment.getMapAsync(this);






        //Link the buttons and textViews to respective views
        btnSensor = (Button) findViewById(R.id.buttonSensor);
        btnGPS = (Button) findViewById(R.id.buttonGPS);
        btnSend = (Button) findViewById(R.id.buttonSend);
        sensorView = (TextView) findViewById(R.id.sensorView0);
        btnClear=findViewById(R.id.buttonClear);
        tvValueLatitud=(TextView)findViewById(R.id.tvValueLatitude);
        tvValueLongitude=(TextView)findViewById(R.id.tvValueLongitude);

        requestQueue = Volley.newRequestQueue(MainActivity.this);

        progressDialog = new ProgressDialog(MainActivity.this);






        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {                                     //if message is what we want
                    String readMessage = (String) msg.obj;                                                                // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);                                      //keep appending to string until ~
                    int endOfLineIndex = recDataString.indexOf("~");                    // determine the end-of-line
                    if (endOfLineIndex > 0) {                                           // make sure there data before ~
                        if (recDataString.charAt(0) == '#')                             //if it starts with # we know it is what we are looking for
                        {
                            String sensor0 = recDataString.substring(1, 6);             //get sensor value from string between indices 1-5
                            sensorView.setText( sensor0 );    //update the textviews with sensor values

                        }
                        recDataString.delete(0, recDataString.length());                    //clear all string data
                    }
                }
            }

        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();

        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
        }

        // GPS
        btnGPS.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {


                getLocation();
                Toast.makeText(getBaseContext(), "Accediendo a las coordenadas", Toast.LENGTH_SHORT).show();
            }
        });
        //Sensor
        btnSensor.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mConnectedThread.write("1");    // Envia accion
                Toast.makeText(getBaseContext(), "Pidiendo dato sensor", Toast.LENGTH_SHORT).show();
            }
        });
        //Borrar
        btnClear.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sensorView.setText(null);
                tvValueLatitud.setText(null);
                tvValueLongitude.setText(null);
                Toast.makeText(getBaseContext(), "Limpiando campos", Toast.LENGTH_SHORT).show();
            }

        });

        //Send
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Showing progress dialog at user registration time.
                progressDialog.setMessage("Please Wait, We are Inserting Your Data on Server");
                progressDialog.show();

                // Calling method to get value from EditText.
                GetValueFromEditText();

                // Creating string request with post method.
                StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String ServerResponse) {

                                // Hiding the progress dialog after all task complete.
                                progressDialog.dismiss();

                                // Showing response message coming from server.
                                Toast.makeText(MainActivity.this, ServerResponse, Toast.LENGTH_LONG).show();
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError volleyError) {

                                // Hiding the progress dialog after all task complete.
                                progressDialog.dismiss();

                                // Showing error message if something goes wrong.
                                Toast.makeText(MainActivity.this, volleyError.toString(), Toast.LENGTH_LONG).show();
                            }
                        }) {
                    @Override
                    protected Map<String, String> getParams() {

                        // Creating Map String Params.
                        Map<String, String> params = new HashMap<String, String>();

                        // Adding All values to Params.
                        params.put("value", ValueHolder);
                        params.put("latitud", LatHolder);
                        params.put("longitud", LonHolder);
                        params.put("Estado",StateHolder);

                        return params;
                    }

                };

                // Creating RequestQueue.
                RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);

                // Adding the StringRequest object into requestQueue.
                requestQueue.add(stringRequest);

            }



        });
    }

    // Creating method to get value from EditText.
    public void GetValueFromEditText(){

        ValueHolder = sensorView.getText().toString().trim();
        LatHolder = tvValueLatitud.getText().toString().trim();
        LonHolder = tvValueLongitude.getText().toString().trim();
//        StateValue = Float.parseFloat(sensorView.getText().toString());
//        if (StateValue<15)
//        {
//            StateHolder="Bajo".trim();
//        }
//        if (StateValue>=15 || StateValue<50)
//        {
//            StateHolder="Medio".trim();
//        }
//        if (StateValue>50)
//        {
//            StateHolder="Aceptable".trim();
//        }

        float num = Float.parseFloat(sensorView.getText().toString());
        if (num <= 10) {
            StateHolder = "Bajo".toString().trim();
        } else if (num > 10 && num <= 30) {
            StateHolder = "Medio".toString().trim();
        } else if (num > 30) {
            StateHolder = "Aceptable".toString().trim();
        }

    }

    void getLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, (android.location.LocationListener) this);
        }
        catch(SecurityException e) {
            e.printStackTrace();
        }
    }





//    @Override
//    public void onMapReady(GoogleMap map) {
//        map.addMarker(new MarkerOptions()
//                .position(new LatLng(0, 0))
//                .title("Marker"));
//    }

    @Override
    public void onLocationChanged(Location location) {
        tvValueLatitud.setText(String.valueOf(location.getLatitude()));
        tvValueLongitude.setText(String.valueOf(location.getLongitude()));
        mLastLocation = location;
        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        // To help preserve the device’s battery life, you’ll typically want to use
        // removeLocationUpdates to suspend location updates when your app is no longer
        // visible onscreen//
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, (com.google.android.gms.location.LocationListener) this);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(MainActivity.this, "Please Enable GPS and Internet", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) {

    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    @Override
    public void onResume() {
        super.onResume();

        //Get MAC address from DeviceListActivity via intent
        Intent intent = getIntent();

        //Get the MAC address from the DeviceListActivty via EXTRA
        address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //create device and set the MAC address

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "La creacción del Socket fallo", Toast.LENGTH_LONG).show();
        }
        // Establish the Bluetooth socket connection.
        try
        {
            btSocket.connect();
        } catch (IOException e) {
            try
            {
                btSocket.close();
            } catch (IOException e2)
            {
                //insert code to deal with this
            }
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("x");
    }

    @Override
    public void onPause()
    {
        super.onPause();
        try
        {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Specify what kind of map you want to display. In this example I’m sticking with the
        // classic, “Normal” map

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        mMap.setMyLocationEnabled(true);    }

    protected synchronized void buildGoogleApiClient() {
        // Use the GoogleApiClient.Builder class to create an instance of the
        // Google Play Services API client//
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks((GoogleApiClient.ConnectionCallbacks) this)
                .addApi(LocationServices.API)
                .build();

        // Connect to Google Play Services, by calling the connect() method//
        mGoogleApiClient.connect();
    }

    @Override
    // If the connect request is completed successfully, the onConnected(Bundle) method
    // will be invoked and any queued items will be executed//
    public void onConnected(Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Retrieve the user’s last known location//
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                    mLocationRequest, (com.google.android.gms.location.LocationListener) this);
        }
    }

//    @Override
    // If the connect request is completed successfully, the onConnected(Bundle) method
    // will be invoked and any queued items will be executed//
//    public void onConnected(Bundle bundle) {
//        mLocationRequest = new LocationRequest();
//        mLocationRequest.setInterval(2000);
//        if (ContextCompat.checkSelfPermission(this,
//                android.Manifest.permission.ACCESS_COARSE_LOCATION)
//                == PackageManager.PERMISSION_GRANTED) {
//            // Retrieve the user’s last known location//
//            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
//                    mLocationRequest, (com.google.android.gms.location.LocationListener) this);
//        }
//    }

    @Override
    public void onConnectionSuspended(int i) {
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION:
            {

                // If the request is cancelled, the result array will be empty (0)//
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // If the user has granted your permission request, then your app can now perform all its
                    // location-related tasks, including displaying the user’s location on the map//
                    if (ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    // If the user has denied your permission request, then at this point you may want to
                    // disable any functionality that depends on this permission//
                }
                return;
            }
        }
    }







    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "El dispositivo no soporta bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }


    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);         //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "La Conexión fallo", Toast.LENGTH_LONG).show();
                finish();

            }
        }
    }
}