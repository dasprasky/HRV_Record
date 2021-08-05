package com.wbd101.hrvrecord;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;
import java.util.Locale;

public class scanResultAdapter extends RecyclerView.Adapter<scanResultAdapter.scanViewHolder> {
    private final LinkedList<ScanResult> scanResultsList;
    private final LayoutInflater mInflator;
    private final Context context;

    public scanResultAdapter(Context context, LinkedList<ScanResult> scanResultsList) {
        mInflator = LayoutInflater.from(context);
        this.scanResultsList = scanResultsList;
        this.context= context;
    }

    @NonNull
    @Override
    public scanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View mItemView = mInflator.inflate(R.layout.device_identity, parent, false);
        return new scanViewHolder(mItemView, this);
    }

    @Override
    public void onBindViewHolder(@NonNull scanViewHolder holder, int position) {
        BluetoothDevice device = scanResultsList.get(position).getDevice();
        holder.deviceName.setText(device.getName() == null ? "Unknown": scanResultsList.get(position).getDevice().getName());
//        holder.deviceRssi.setText(String.valueOf(scanResultsList.get(position).getRssi()));
        holder.deviceAddress.setText(device.getAddress());
    }

    @Override
    public int getItemCount() {
        return scanResultsList.size();
    }

    class scanViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        public final TextView deviceName;
        public final TextView deviceAddress;
//        public final TextView deviceRssi;
        final scanResultAdapter mAdapter;

        public scanViewHolder(View itemView, scanResultAdapter adapter){
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name_1);
            deviceAddress = itemView.findViewById(R.id.address_value_1);
//            deviceRssi = itemView.findViewById(R.id.device_rssi);
            this.mAdapter = adapter;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int mPosition = getLayoutPosition();
            ScanResult element = scanResultsList.get(mPosition);

            Intent intent = new Intent(context, BleOperations.class);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, element.getDevice());
            context.startActivity(intent);
        }
    }

}
