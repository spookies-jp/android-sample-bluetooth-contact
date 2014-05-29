package jp.co.spookies.android.bluetoothcontact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import a_vcard.android.syncml.pim.PropertyNode;
import a_vcard.android.syncml.pim.VDataBuilder;
import a_vcard.android.syncml.pim.VNode;
import a_vcard.android.syncml.pim.vcard.VCardException;
import a_vcard.android.syncml.pim.vcard.VCardParser;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothContactActivity extends Activity {
	private static final int DURATION = 300;
	private static final int REQUEST_PICK_CONTACT = 0;
	private static final int REQUEST_ENABLE_BLUETOOTH = 1;
	private static final int REQUEST_SELECT_DEVICE = 2;
	private static final int REQUEST_DISCOVERABLE = 3;
	private static final String SERVICE = "BluetoothContact";
	private static final UUID PROFILE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final String PREFERENCE_VCARD = "vcard";
	private static final String PROP_NAME = "propName";
	private static final String PROP_VALUE = "propValue";
	private BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
	private BluetoothDevice device;
	private Thread serverThread;
	private Thread clientThread;
	private VNode receivedVNode;
	private Handler handler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.main);
		initTab();
		if (bluetooth == null) {
			this.finish();
		}
		VNode contact = getVNode();
		if (contact != null) {
			showVCard(R.id.contact_list_config, contact);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!bluetooth.isEnabled()) {
			Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		if (requestCode == REQUEST_PICK_CONTACT && resultCode == RESULT_OK) {
			Cursor c = managedQuery(intent.getData(), null, null, null, null);
			c.moveToFirst();
			String key = c.getString(c.getColumnIndex(Contacts.LOOKUP_KEY));
			Uri uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, key);
			try {
				InputStream stream = getContentResolver().openInputStream(uri);
				byte[] bytes = new byte[stream.available()];
				stream.read(bytes);
				setVCard(new String(bytes));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
			if (resultCode != RESULT_OK) {
				this.finish();
			}
		} else if (requestCode == REQUEST_SELECT_DEVICE) {
			if (resultCode == RESULT_OK) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				setDevice(device);
			}
		} else if (requestCode == REQUEST_DISCOVERABLE) {
			if (resultCode == DURATION) {
				startServerThread();
			}
		}
	}

	public void onSelectReceiverButtonClicked(View view) {
		Intent intent = new Intent(this, DeviceList.class);
		startActivityForResult(intent, REQUEST_SELECT_DEVICE);
	}

	public void onSelectContactButtonClicked(View view) {
		Intent intent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
		startActivityForResult(intent, REQUEST_PICK_CONTACT);
	}

	public void onSendButtonClicked(View view) {
		if (device != null) {
			startClientThread();
		}
	}

	public void onReceiveButtonClicked(View view) {
		Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DURATION);
		startActivityForResult(intent, REQUEST_DISCOVERABLE);
	}

	public void onRegisterButtonClicked(View view) {
		if (receivedVNode != null) {
			insertContact(receivedVNode);
			receivedVNode = null;
			showVCard(R.id.contact_list_receive, null);
			Toast.makeText(this, R.string.registered, Toast.LENGTH_LONG).show();
		}
	}

	private void initTab() {
		TabHost tabs = (TabHost) findViewById(android.R.id.tabhost);
		tabs.setup();
		TabSpec tab1 = tabs.newTabSpec("tab1");
		tab1.setIndicator(getString(R.string.tab1),
				getResources().getDrawable(R.drawable.icon_send));
		tab1.setContent(R.id.tab1);
		tabs.addTab(tab1);
		TabSpec tab2 = tabs.newTabSpec("tab2");
		tab2.setIndicator(getString(R.string.tab2),
				getResources().getDrawable(R.drawable.icon_receive));
		tab2.setContent(R.id.tab2);
		tabs.addTab(tab2);
		TabSpec tab3 = tabs.newTabSpec("tab3");
		tab3.setIndicator(getString(R.string.tab3),
				getResources().getDrawable(R.drawable.icon_config));
		tab3.setContent(R.id.tab3);
		tabs.addTab(tab3);
	}

	private void setDevice(BluetoothDevice device) {
		this.device = device;
		TextView deviceName = (TextView) findViewById(R.id.receiver);
		if (device == null) {
			deviceName.setText(R.string.not_selected);
		} else {
			deviceName.setText(this.device.getName());
		}
	}

	private String getVCard() {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(this);
		return pref.getString(PREFERENCE_VCARD, "");
	}

	private void setVCard(String vCard) {
		Editor pref = PreferenceManager.getDefaultSharedPreferences(this)
				.edit();
		pref.putString(PREFERENCE_VCARD, vCard);
		pref.commit();
		VNode contact = getVNode();
		if (contact != null) {
			showVCard(R.id.contact_list_config, contact);
		}
	}

	private VNode getVNode() {
		String vCardString = getVCard();
		if (vCardString.equals("")) {
			return null;
		}
		return buildContacts(vCardString).get(0);
	}

	private void showVCard(int listViewId, VNode contact) {
		ListView listView = (ListView) findViewById(listViewId);
		if (contact == null) {
			listView.setAdapter(null);
			return;
		}
		List<Map<String, String>> data = new ArrayList<Map<String, String>>();
		for (PropertyNode property : contact.propList) {
			try {
				PropertyType type = PropertyType.valueOf(property.propName);
				if (type != null) {
					Map<String, String> content = new HashMap<String, String>();
					content.put(PROP_NAME, type.getJpName());
					content.put(PROP_VALUE, property.propValue);
					data.add(content);
				}
			} catch (IllegalArgumentException e) {
			}
		}
		ListAdapter adapter = new SimpleAdapter(this, data,
				R.layout.property_list_item, new String[] { PROP_NAME,
						PROP_VALUE }, new int[] { R.id.property_name,
						R.id.property_value });
		listView.setAdapter(adapter);
	}

	private void startServerThread() {
		if (serverThread != null) {
			return;
		}
		final ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setMessage(getString(R.string.receiving));
		progressDialog.show();
		serverThread = new Thread() {
			@Override
			public void run() {
				try {
					BluetoothServerSocket serverSocket = bluetooth
							.listenUsingRfcommWithServiceRecord(SERVICE,
									PROFILE);
					BluetoothSocket socket = serverSocket.accept();
					serverSocket.close();
					InputStream stream = socket.getInputStream();
					byte[] bytes = new byte[1000];
					int length = stream.read(bytes);
					byte[] str = new byte[length];
					for (int i = 0; i < length; i++) {
						str[i] = bytes[i];
					}
					String vcard = new String(str);
					receivedVNode = buildContacts(vcard).get(0);
					handler.post(new Runnable() {
						@Override
						public void run() {
							showVCard(R.id.contact_list_receive, receivedVNode);
							progressDialog.dismiss();
							Toast.makeText(BluetoothContactActivity.this,
									R.string.received, Toast.LENGTH_LONG)
									.show();
						}
					});
				} catch (IOException e) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(BluetoothContactActivity.this,
									R.string.failed, Toast.LENGTH_LONG).show();
							progressDialog.cancel();
						}
					});
				}
				serverThread = null;
			}
		};
		serverThread.start();
	}

	private void startClientThread() {
		if (clientThread != null) {
			return;
		}
		final ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setMessage(getString(R.string.sending));
		progressDialog.show();
		clientThread = new Thread() {
			@Override
			public void run() {
				try {
					if (bluetooth.isDiscovering()) {
						bluetooth.cancelDiscovery();
					}
					BluetoothSocket socket = device
							.createRfcommSocketToServiceRecord(PROFILE);
					socket.connect();
					OutputStream stream = socket.getOutputStream();
					byte[] buffer = getVCard().getBytes();
					stream.write(buffer);
					handler.post(new Runnable() {
						@Override
						public void run() {
							progressDialog.dismiss();
							setDevice(null);
							Toast.makeText(BluetoothContactActivity.this,
									R.string.sent, Toast.LENGTH_LONG).show();
						}
					});
				} catch (Exception e) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							progressDialog.cancel();
							Toast.makeText(BluetoothContactActivity.this,
									R.string.failed, Toast.LENGTH_LONG).show();
						}
					});
				}
				clientThread = null;
			}
		};
		clientThread.start();
	}

	private List<VNode> buildContacts(String vCard) {
		VCardParser parser = new VCardParser();
		VDataBuilder builder = new VDataBuilder();
		try {
			parser.parse(vCard, "utf-8", builder);
		} catch (VCardException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return builder.vNodeList;
	}

	private void insertContact(VNode contact) {
		String name = "";
		List<String> tels = new ArrayList<String>();
		List<String> emails = new ArrayList<String>();
		for (PropertyNode property : contact.propList) {
			try {
				PropertyType type = PropertyType.valueOf(property.propName);
				if (type == PropertyType.FN) {
					name = property.propValue;
				} else if (type == PropertyType.EMAIL) {
					emails.add(property.propValue);
				} else if (type == PropertyType.TEL) {
					tels.add(property.propValue);
				}
			} catch (IllegalArgumentException e) {
			}
		}
		ContentValues values = new ContentValues();
		values.put(RawContacts.ACCOUNT_TYPE, (String) null);
		values.put(RawContacts.ACCOUNT_NAME, (String) null);
		Uri uri = getContentResolver().insert(RawContacts.CONTENT_URI, values);
		values.clear();
		values.put(ContactsContract.Data.RAW_CONTACT_ID,
				ContentUris.parseId(uri));
		values.put(ContactsContract.Data.MIMETYPE,
				StructuredName.CONTENT_ITEM_TYPE);
		values.put(StructuredName.DISPLAY_NAME, name);
		getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
		for (String email : emails) {
			values.clear();
			values.put(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
			values.put(Email.DATA1, email);
			getContentResolver().insert(
					Uri.withAppendedPath(uri, Data.CONTENT_DIRECTORY), values);
		}
		for (String tel : tels) {
			values.clear();
			values.put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
			values.put(Phone.NUMBER, tel);
			getContentResolver().insert(
					Uri.withAppendedPath(uri, Data.CONTENT_DIRECTORY), values);
		}
	}
}

enum PropertyType {
	FN("名前"), EMAIL("メール"), TEL("電話番号");
	private PropertyType(String jpName) {
		this.jpName = jpName;
	}

	private String jpName;

	public String getJpName() {
		return jpName;
	}
}