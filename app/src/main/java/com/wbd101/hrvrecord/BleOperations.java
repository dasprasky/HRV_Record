package com.wbd101.hrvrecord;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BleOperations extends AppCompatActivity {
    private BluetoothGatt gatt;
    private static final int GATT_MAX_MTU_SIZE = 517;
    private int rri_progress = 0;
    private ArrayList<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
    private LineGraphSeries<DataPoint> hrGraphSeries = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> respGraphSeries = new LineGraphSeries<>();
    private static final UUID HEART_RATE_MEASURE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID TX_CHARACTERISTIC = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private GraphView hr_graph, resp_graph;
    private long startTime;
    private boolean record = false, executed = false;
    private String hr_csv, resp_csv, hrv_csv, tx_csv;
    private File hr_file, resp_file, hrv_file, tx_file;
    private final int max_window = 160;
    private int age = 23;
    private int seconds = 0;
    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_operations);
        Intent intent = getIntent();

        // Register for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        registerReceiver(mReceiver, filter);

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String device_name = device.getName();
        Bundle extras = intent.getExtras();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    gatt = device.connectGatt(getApplicationContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE); // handle devices with same MAC for both classic BT and BLE

                } else {
                    gatt = device.connectGatt(getApplicationContext(), false, gattCallback);
                }
            }
        });

        hr_graph = (GraphView)findViewById(R.id.hr_graph);
        hr_graph.setBackgroundColor(Color.TRANSPARENT);
        hr_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        hr_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        hr_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        hr_graph.getViewport().setYAxisBoundsManual(true);
        hr_graph.getViewport().setMinX(0.0);
        hr_graph.getViewport().setMaxX(100.0);
        hr_graph.getViewport().setXAxisBoundsManual(true);
        hrGraphSeries.setThickness(4);
        hrGraphSeries.setColor(Color.parseColor("#A6CEE3"));
        hrGraphSeries.setDataPointsRadius((float)2.0);
        hr_graph.addSeries(hrGraphSeries);

        //resp
        resp_graph = (GraphView)findViewById(R.id.resp_graph);
        resp_graph.setBackgroundColor(Color.TRANSPARENT);
        resp_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        resp_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        resp_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        resp_graph.getViewport().setYAxisBoundsManual(true);
        resp_graph.getViewport().setMinX(0.0);
        resp_graph.getViewport().setMaxX(100.0);
        resp_graph.getViewport().setXAxisBoundsManual(true);
        respGraphSeries.setThickness(4);
        respGraphSeries.setColor(Color.parseColor("#A6CEE3"));
        respGraphSeries.setDataPointsRadius((float)2.0);
        resp_graph.addSeries(respGraphSeries);

        //native methods
        AndroidHRVAPI.init_hrv_analysis();
        AndroidRespirationAPI.init_respiration_analysis();

        TextView hr = (TextView)findViewById(R.id.NBO_hr_value);
        hr.setText(extras.getString("currHR"));
        TextView rr = (TextView)findViewById(R.id.NBO_resp_value);
        rr.setText(extras.getString("currRR"));

        // when disconnect is pressed
        Button disconnect_button = (Button)findViewById(R.id.NBO_disconnect_button);
        disconnect_button.setOnClickListener(v -> {
            gatt.disconnect();
            finish(); //finishes current activity and falls back to MainActivity
        });

        // when record is pressed
        Button record_button = (Button)findViewById(R.id.record_button);
        record_button.setOnClickListener(v -> {
            File root = getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath());//Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            record = !record;
            if(record){
                record_button.setText("Stop Recording");
                try {
                    Date dNow = new Date();
                    SimpleDateFormat timeStamp =
                            new SimpleDateFormat("HH_mm_ss'_'dd_MM_yyyy");
                    String folder = root + "/wbdmx";
                    makeFolder(folder);
                    folder = root + "/wbdmx/Heart Rate";
                    makeFolder(folder);
                    folder = root + "/wbdmx/HRV";
                    makeFolder(folder);
                    folder = root + "/wbdmx/Respiration";
                    makeFolder(folder);
                    folder = root + "/wbdmx/Tx";
                    makeFolder(folder);

                    String hr_filename = "/wbdmx/Heart Rate/" + device_name+"_"+"hr_" + timeStamp.format(dNow.getTime()) + ".csv";
                    hr_csv = (root + hr_filename);
                    hr_file = new File(hr_csv);
                    List<String> processed_line = new ArrayList<String>();
                    processed_line.add("System Timestamp");
                    processed_line.add("Milliseconds Elapsed");
                    processed_line.add("Heart Rate");
                    processed_line.add("R-R Interval (RRI)");;
                    String[] final_line = new String[processed_line.size()];
                    processed_line.toArray(final_line);
                    csv_writer(final_line, hr_csv);

                    String respiratory_filename = "/wbdmx/Respiration/" + device_name+"_"+ "resp_" + timeStamp.format(dNow.getTime()) + ".csv";
                    resp_csv = (root + respiratory_filename);
                    resp_file = new File(resp_csv);
                    processed_line = new ArrayList<String>();
                    processed_line.add("System Timestamp");
                    processed_line.add("Milliseconds Elapsed");
                    processed_line.add("Respiratory Rate (RR)");
                    final_line = new String[processed_line.size()];
                    processed_line.toArray(final_line);
                    csv_writer(final_line, resp_csv);

                    String hrv_filename = "/wbdmx/HRV/" + device_name+"_"+ "hrv_" + timeStamp.format(dNow.getTime()) + ".csv";
                    hrv_csv = (root + hrv_filename);
                    hrv_file = new File(hrv_csv);
                    processed_line = new ArrayList<String>();
                    processed_line.add("System Timestamp");
                    processed_line.add("Milliseconds Elapsed");
                    processed_line.add("Timestamp");
                    processed_line.add("Total RR count");
                    processed_line.add("Window RR count");
                    processed_line.add("Valid RR count");
                    processed_line.add("Stress index");
                    processed_line.add("pNN50");
                    processed_line.add("rMSSD");
                    processed_line.add("HRV Score");
                    processed_line.add("DFA slope 1");
                    processed_line.add("DFA slope 2");
                    processed_line.add("SDNN");
                    processed_line.add("Respiratory rate");
                    processed_line.add("VLF");
                    processed_line.add("LF");
                    processed_line.add("HF");
                    processed_line.add("LFnu");
                    processed_line.add("HFnu");
                    processed_line.add("LF/HF");
                    processed_line.add("TP");
                    processed_line.add("Conf. Lvl");
                    processed_line.add("Percentage Stress");
                    final_line = new String[processed_line.size()];
                    processed_line.toArray(final_line);
                    csv_writer(final_line, hrv_csv);

                    String tx_filename = "/wbdmx/Tx/" + device_name+"_"+ "tx_" + timeStamp.format(dNow.getTime()) + ".csv";
                    tx_csv = (root + tx_filename);
                    tx_file = new File(tx_csv);
                    processed_line = new ArrayList<String>();
                    processed_line.add("System Timestamp");
                    processed_line.add("Milliseconds Elapsed");
                    processed_line.add("G_x");
                    processed_line.add("G_y");
                    processed_line.add("G_z");
                    final_line = new String[processed_line.size()];
                    processed_line.toArray(final_line);
                    csv_writer(final_line, tx_csv);

                    Toast.makeText(getApplicationContext(),"New Files Created", Toast.LENGTH_SHORT).show();


//                    Log.w("onInfo", "new files created");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                startTime = System.currentTimeMillis();
                runTimer();
            }
            else{
                Toast.makeText(getApplicationContext(), "Files Saved", Toast.LENGTH_SHORT).show();
                record_button.setText("Record");
                seconds = 0;
                handler.removeCallbacks(runnable);
                final TextView statusView = findViewById(R.id.status_info);
                statusView.setText("You can record another session.");
                statusView.setVisibility(View.VISIBLE);
//                final TextView timeView = findViewById(R.id.timer);
//                timeView.setVisibility(View.INVISIBLE);
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Age");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String text = input.getText().toString();
                if(age>15 && age<100){
                    age = Integer.parseInt(text);
                    Toast.makeText(getApplicationContext(),"Age set to "+ text, Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(getApplicationContext(),"Age must be between 15 and 100. Age set to default - 23", Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(getApplicationContext(),"Age set to default - 23", Toast.LENGTH_LONG).show();
                dialog.cancel();
            }
        });
        builder.show();
    }
    private void makeFolder(String name){
        File directory = new File(name);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }
    private void runTimer() {
        final TextView statusView = findViewById(R.id.status_info);
        statusView.setVisibility(View.INVISIBLE);
        final TextView timeView = findViewById(R.id.timer);
        timeView.setVisibility(View.VISIBLE);
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                int secs = seconds % 60;

                String time = String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
                timeView.setText(time);
                seconds++;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(runnable);
    }
    // Create a BroadcastReceiver for bluetooth actions
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(getApplicationContext(), "Connected to "+ device.getAddress(), Toast.LENGTH_SHORT).show();
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                Toast.makeText(getApplicationContext(), "Device Disconnected", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(status == gatt.GATT_SUCCESS){
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully connected to device "+gatt.getDevice().getAddress());
                    new Handler(Looper.getMainLooper()).post(new Runnable(){
                        @Override
                        public void run(){
                            boolean ans = gatt.discoverServices();
                            Log.d("onConnectionStateChange", "Discover services started: "+ ans);
                            gatt.requestMtu(GATT_MAX_MTU_SIZE);
                        }
                    });
                }
                else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully disconnected form device "+ gatt.getDevice().getAddress());
                    gatt.close();
                }
                else{
                    Log.w("BluetoothGattCallback", "Error "+status+" encountered for "+gatt.getDevice().getAddress()+ "\nDisconnecting...");
                    gatt.close();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            List<BluetoothGattService> services = gatt.getServices();
            Log.w("BluetoothGattCallback", "Discovered "+ services.size()+" services for "+gatt.getDevice().getAddress());

            for(int i = 0; i< services.size(); i++){
                List<BluetoothGattCharacteristic> characteristic = services.get(i).getCharacteristics();
                Log.w("BluetoothGattCallback", "Discovered Characteristic of size "+ characteristic.size()+" for "+ services.get(i).getUuid());
                for(int j = 0; j<characteristic.size(); j++){
                    characteristics.add(characteristic.get(j));
                    Log.w("BluetoothGattCallback", "Discovered Characteristic"+ characteristic.get(j).getUuid()+" for "+gatt.getDevice().getAddress());
                }
            }
            Log.w("BluetoothGattCallback", "Total Characteristics = "+ characteristics.size());
            characteristicsOperations(characteristics);

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("BluetoothGattCallback", "Read characteristic success for "+ characteristic.getUuid().toString() +" value: "+ new String(characteristic.getValue(), StandardCharsets.UTF_8));
            }
            else if(status == BluetoothGatt.GATT_READ_NOT_PERMITTED){
                Log.i("BluetoothGattCallback", "Read not permitted for  "+ characteristic.getUuid().toString());
            }
            else{
                Log.i("BluetoothGattCallback", "Characteristic read failed for  "+ characteristic.getUuid().toString());
                gatt.disconnect();
                finish();
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] value = characteristic.getValue();
            broadcastUpdate(characteristic, value);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            boolean event = status ==BluetoothGatt.GATT_SUCCESS;
            Log.w("onMtuChanged", "ATT MTU changed to "+mtu+" "+ event);
        }
    };
    private void characteristicsOperations(List<BluetoothGattCharacteristic> characteristics){
        if(characteristics.isEmpty()){
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?");
            return;
        }
        Iterator<BluetoothGattCharacteristic> it = characteristics.iterator();
        while(it.hasNext()) {
            BluetoothGattCharacteristic character = it.next();

            if(character.getUuid().equals(HEART_RATE_MEASURE)){
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setNotifications(character, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, true);
                    }
                }, 2000);

            }
            if(character.getUuid().equals(TX_CHARACTERISTIC)){
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setNotifications(character, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, true);
                    }
                }, 500);

            }
        }

    }
    public void setNotifications(BluetoothGattCharacteristic characteristic, byte[] payload, boolean enable){
        String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
        UUID cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID);

        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if(descriptor == null){
            Log.e("setNotification", "Could not get CCC descriptor for characteristic "+ characteristic.getUuid());
        }
        if(!gatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)){
            Log.e("setNotification", "setCharacteristicNotification failed");
        }
        descriptor.setValue(payload);
        boolean result = gatt.writeDescriptor(descriptor);
        if(!result){
            Log.e("setNotification", "writeDescriptor failed for descriptor");

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),"Descriptor failed! Device may not be connected. Try again!",Toast.LENGTH_LONG).show();
                }
            });
            gatt.disconnect();
            finish();
        }
    }
    private int x_hr = 0, hr_max =0, hr_min = 100;
    private int resp_max =0, resp_min = 0, x_resp=0;
    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic, byte[] value) {
        if (characteristic.getUuid().equals(HEART_RATE_MEASURE)) {
            String value_str = HelperFunctions.bytesToHex(value);
            String[] line = value_str.split(" ");
            int heart_rate = Integer.parseInt(line[1], 16);
//            Log.w("broadcastUpdate", "Heart Rate: " + heart_rate);
            if (heart_rate != 255) {
                TextView hr = (TextView) findViewById(R.id.NBO_hr_value);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hr.setText(Integer.toString(heart_rate));
                        if(heart_rate != 0 && heart_rate>hr_max){
                            hr_max = heart_rate;
                            hr_graph.getViewport().setMinY(30.0);
                            hr_graph.getViewport().setMaxY(hr_max);
                        }
                        if(heart_rate != 0 && heart_rate<hr_min){
                            hr_min = heart_rate;
                            hr_graph.getViewport().setMinY(hr_min);
                        }
                        DataPoint datapoint = new DataPoint(x_hr++, heart_rate);
                        hrGraphSeries.appendData(datapoint, true, 100);
                    }
                });
            }
            List<Short> rri_values= new ArrayList<Short>();
            if(line.length>2) {
                rri_progress++;
                String[] rris = Arrays.copyOfRange(line, 2, line.length);
                for(int i = 0 ; i < rris.length-1; i=i+2){
                    short rr_prev = (short)Integer.parseInt(rris[i+1] + rris[i], 16);
                    rri_values.add(rr_prev);
                }
                for(int i = 0; i< rri_values.size(); i++){
                    //native
                    AndroidHRVAPI.hrv_analysis((int) (System.currentTimeMillis() / 1000L), (short) heart_rate, rri_values.get(i));
                    AndroidRespirationAPI.analyze_respiration((float)rri_values.get(i));
                    hrv_analysis_results();
                }
            }
            if (record) {
                SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss.SSS");
                Date d = new Date();
                List<String> processed_line = new ArrayList<String>();
                processed_line.add(format.format(d.getTime()));
                processed_line.add(Long.toString(System.currentTimeMillis() - startTime));
                processed_line.add(Integer.toString(heart_rate));
                for (float rri : rri_values) {
                    processed_line.add(Float.toString(rri));
                }
                String[] final_line = new String[processed_line.size()];
                processed_line.toArray(final_line);
                csv_writer(final_line, hr_csv);
            }
        }
        else if(characteristic.getUuid().equals(TX_CHARACTERISTIC) && record){
            SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss.SSS");
            Date d = new Date();
            byte[] char_value = characteristic.getValue();
            boolean check_sum = HelperFunctions.checksum(char_value);
//            Log.i("broadcastUpdate/checksm", Boolean.toString(check_sum));
            List<String> processed_line = new ArrayList<String>();
            if(!check_sum) processed_line.add("***Checksum mismatch**");
            String value_str =  HelperFunctions.bytestoformat(char_value);

            processed_line.add(format.format(d.getTime()));
            processed_line.add(Long.toString(System.currentTimeMillis() - startTime));
            List data = Arrays.asList(value_str.split(" "));
            processed_line.addAll(data.subList(Math.max(data.size() - 3, 0), data.size())); //getting the last 3 items
            String[] final_line = new String[processed_line.size()];
            processed_line.toArray(final_line);
            csv_writer(final_line, tx_csv);
//            Log.i("broadcastUpdate/tx_char",  value_str);
        }
    }

    private void hrv_analysis_results() {
        if(rri_progress > 30) {     //because rr takes aroud 30 seconds to produce results
            TextView rr = (TextView)findViewById(R.id.NBO_resp_value);
            respiration_result_t respiraResult = AndroidRespirationAPI.get_respiration_result();
            int resp_rate = respiraResult.getRespiratory_rate();
//            Log.w("onCreate-HRV_Results", "Respiratory rate: " + resp_rate);
            runOnUiThread(new Runnable(){
                @Override
                public void run(){
                    rr.setText(String.format(Locale.US,"%d", respiraResult.getRespiratory_rate()));
                    if(resp_rate>resp_max){
                        resp_max = resp_rate;
                        resp_graph.getViewport().setMaxY(resp_max);
                    }
                    if(resp_rate<resp_min){
                        resp_min = resp_rate;
                        resp_graph.getViewport().setMinY(resp_min);
                    }
                    DataPoint datapoint = new DataPoint(x_resp++, resp_rate);
                    respGraphSeries.appendData(datapoint, true, 100);
                }
            });
            if (record) {
                SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss.SSS");
                Date d = new Date();
                List<String> processed_line = new ArrayList<String>();
                processed_line.add(format.format(d.getTime()));
                processed_line.add(Long.toString(System.currentTimeMillis() - startTime));
                processed_line.add(Integer.toString(resp_rate));;

                String[] final_line = new String[processed_line.size()];
                processed_line.toArray(final_line);
                csv_writer(final_line, resp_csv);
            }
        }
        if(rri_progress>=max_window && !executed){
            Button record_button = (Button)findViewById(R.id.record_button);
            new Handler(Looper.getMainLooper()).post(new Runnable(){
                @Override
                public void run(){
                    record_button.setEnabled(true);
                }
            });
            TextView status_info = (TextView)findViewById(R.id.status_info);
            new Handler(Looper.getMainLooper()).post(new Runnable(){
                @Override
                public void run(){
                    status_info.setText("You can start recording now.");
                }
            });

            executed = true;
        }
        if (rri_progress >= max_window)  {
            hrv_result_t currResult = AndroidHRVAPI.HRV_Get_Analysis_Result();
            int timestamp = currResult.getResult_timestamp();
            long total_rr_count = currResult.getTotal_rr_cnt();
            int rr_window_count = currResult.getWindow_rr_cnt();
            int valid_rr_count = currResult.getValid_rr_cnt();
            int stress_index = currResult.getStress_index();
            int pNN50 = currResult.getPNN50();
            Float rmssd = currResult.getRMSSD();
            float score = currResult.getHRV_Score();
            float dfa_slope1 = currResult.getDfa_slope1();
            float dfa_slope2 = currResult.getDfa_slope2();
            float SDNN = currResult.getSDNN180();
            int respiratory_rate = currResult.getRespiratory_rate();
            float vlf = currResult.getVlf();
            Float lf = currResult.getLf();
            Float hf = currResult.getHf();
            float lf_nu = currResult.getLf_nu();
            float hf_nu = currResult.getHf_nu();
            float lf_to_hf = currResult.getLf_to_hf();
            float tp = currResult.getTotal_power();
            float conf_lvl = currResult.getResult_conf_level();
//            Log.w("onCreate-HRV_Results", "Timestamp: " + timestamp);
//            Log.w("onCreate-HRV_Results", "Total RR count: " + total_rr_count);
//            Log.w("onCreate-HRV_Results", "Window RR count: " + rr_window_count);
//            Log.w("onCreate-HRV_Results", "Valid RR count: " + valid_rr_count);
//            Log.w("onCreate-HRV_Results", "Stress index: " + stress_index);
//            Log.w("onCreate-HRV_Results", "pNN50: " + pNN50);
//            Log.w("onCreate-HRV_Results", "rMSSD: " + rmssd);
//            Log.w("onCreate-HRV_Results", "HRV score: " + score);
//            Log.w("onCreate-HRV_Results", "DFA slope 1: " + dfa_slope1);
//            Log.w("onCreate-HRV_Results", "DFA slope 2: " + dfa_slope2);
//            Log.w("onCreate-HRV_Results", "SDNN: " + SDNN);
//            Log.w("onCreate-HRV_Results", "Respiratory rate: " + respiratory_rate);
//            Log.w("onCreate-HRV_Results", "VLF: " + vlf);
//            Log.w("onCreate-HRV_Results", "LF: " + lf);
//            Log.w("onCreate-HRV_Results", "HF: " + hf);
//            Log.w("onCreate-HRV_Results", "LFnu: " + lf_nu);
//            Log.w("onCreate-HRV_Results", "HFnu: " + hf_nu);
//            Log.w("onCreate-HRV_Results", "LF/HF: " + lf_to_hf);
//            Log.w("onCreate-HRV_Results", "TP: " + tp);
//            Log.w("onCreate-HRV_Results", "Conf Lvl: " + conf_lvl);

            int percentage_stress = AndroidHRVStressAPI.get_percentage_stress(currResult.getRMSSD(), age);
            percentage_stress = (100-percentage_stress);
//            Log.w("onCreate-HRV_Results", "Stress Lvl: " + percentage_stress);


            if (record) {
                SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss.SSS");
                Date d = new Date();
                List<String> processed_line = new ArrayList<String>();
                processed_line.add(format.format(d.getTime()));
                processed_line.add(Long.toString(System.currentTimeMillis() - startTime));
                processed_line.add(Integer.toString(timestamp));
                processed_line.add(Long.toString(total_rr_count));
                processed_line.add(Integer.toString(rr_window_count));
                processed_line.add(Integer.toString(valid_rr_count));
                processed_line.add(Integer.toString(stress_index));
                processed_line.add(Integer.toString(pNN50));
                processed_line.add(Float.toString(rmssd));
                processed_line.add(Float.toString(score));
                processed_line.add(Float.toString(dfa_slope1));
                processed_line.add(Float.toString(dfa_slope2));
                processed_line.add(Float.toString(SDNN));
                processed_line.add(Integer.toString(respiratory_rate));
                processed_line.add(Float.toString(vlf));
                processed_line.add(Float.toString(lf));
                processed_line.add(Float.toString(hf));
                processed_line.add(Float.toString(lf_nu));
                processed_line.add(Float.toString(hf_nu));
                processed_line.add(Float.toString(lf_to_hf));
                processed_line.add(Float.toString(tp));
                processed_line.add(Float.toString(conf_lvl));
                processed_line.add(Integer.toString(percentage_stress));
                String[] final_line = new String[processed_line.size()];
                processed_line.toArray(final_line);
                csv_writer(final_line, hrv_csv);
            }

        }
    }
    public void csv_writer(String[] line, String csv) {
        try {
            CSVWriter writer = new CSVWriter(new FileWriter(csv, true));
            writer.writeNext(line, false);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /** static constructor */
    static {
        System.loadLibrary("AndroidHRVAPI");
        System.loadLibrary("AndroidRespirationAPI");
        System.loadLibrary("AndroidHRVStressAPI");
    }
}
