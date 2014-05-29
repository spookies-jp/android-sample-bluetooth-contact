package jp.co.spookies.android.bluetoothcontact;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DeviceList extends ListActivity {
	private DeviceAdapter adapter;
	private BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

	private BroadcastReceiver bluetoothHandler = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					if (device.getName() != null) {
						adapter.add(device);
					}
				}
			} else if (action.equals(BluetoothDevice.ACTION_NAME_CHANGED)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					adapter.add(device);
				}
			} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
				setProgressBarIndeterminateVisibility(true);
			} else if (action
					.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
				setProgressBarIndeterminateVisibility(false);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);
		adapter = new DeviceAdapter(this, R.layout.device_list_item);
		setListAdapter(adapter);
		for (BluetoothDevice device : bluetooth.getBondedDevices()) {
			adapter.add(device);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(bluetoothHandler, filter);
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(bluetoothHandler);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		BluetoothDevice device = adapter.getItem(position);
		Intent intent = new Intent();
		intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
		setResult(RESULT_OK, intent);
		finish();
	}

	public void onSearchButtonClicked(View view) {
		if (bluetooth.isDiscovering()) {
			bluetooth.cancelDiscovery();
		}
		bluetooth.startDiscovery();
	}
}

class DeviceAdapter extends ArrayAdapter<BluetoothDevice> {
	LayoutInflater inflater;

	public DeviceAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			view = inflater.inflate(android.R.layout.simple_list_item_2, null);
		}
		BluetoothDevice device = getItem(position);
		TextView deviceName = (TextView) view.findViewById(android.R.id.text1);
		deviceName.setText(device.getName());
		TextView deviceAddress = (TextView) view
				.findViewById(android.R.id.text2);
		deviceAddress.setText(device.getAddress());
		return view;
	}
}
