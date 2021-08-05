package com.wbd101.hrvrecord;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Iterator;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner bleScanner=null;
    private ScanCallback scanCallback;
    private scanResultAdapter scanAdapter;
    private final LinkedList<ScanResult> scanResults = new LinkedList<>();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private boolean isScanning = false;

    private Button scan_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        scan_btn = (Button) findViewById(R.id.search_button);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        scanAdapter = new scanResultAdapter(this, scanResults);
        recyclerView.setAdapter(scanAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        scan_btn.performClick();
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSIONS);
        }

    }
    @Override
    protected void onPause() {
        if (bleScanner != null) {
            stopScan();
            bleScanner = null;
        }
        super.onPause();
    }
    @Override
    protected void onStop() {
        if (bleScanner != null) {
            stopScan();
            bleScanner = null;
        }
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        if (bleScanner != null) {
            stopScan();
            bleScanner = null;
        }
        super.onDestroy();

    }

    private void startScan(){
        bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        scanAdapter.notifyDataSetChanged();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if(device.getName()!=null && device.getName().contains("Maxim")) {
                    Iterator<ScanResult> it = scanResults.iterator();
                    boolean present = false;
                    while (it.hasNext()) {
                        ScanResult item = it.next();
                        if (item.getDevice().getAddress().equals(result.getDevice().getAddress())) {
                            present = true;
                            int position = scanResults.indexOf(item);
                            scanResults.set(position, result);
                            scanAdapter.notifyDataSetChanged();
                        }
                    }
                    if (!present) {
                        Log.w("ScanCallback", "Found unique BLE device! Name : " + result.getDevice().getName() + " address: " + result.getDevice().getAddress());
                        ScanRecord scanRecord = result.getScanRecord();
                        scanResults.add(result);
                        scanAdapter.notifyItemInserted(scanResults.size() - 1);
                    }
                }

            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e("ScanCallback", "onScanFailed: code " + errorCode);
            }
        };
        scanResults.clear();
        bleScanner.startScan(null, scanSettings, scanCallback);
        isScanning = true;

    }
    private void stopScan(){
        bleScanner.stopScan(scanCallback);
        isScanning = false;
        scan_btn.setText("SEARCH FOR DEVICES");
    }

    public void button_function(View v) {
        if(isScanning){
            stopScan();
        }
        else{
            startScan();
            scan_btn.setText("STOP SEARCH");
        }
    }
}