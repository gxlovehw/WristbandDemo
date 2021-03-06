package com.realsil.android.wristbanddemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.realsil.android.wristbanddemo.backgroundscan.BackgroundScanAutoConnected;
import com.realsil.android.wristbanddemo.bmob.BmobControlManager;
import com.realsil.android.wristbanddemo.constant.ConstantParam;
import com.realsil.android.wristbanddemo.utility.DeviceListAdapter;
import com.realsil.android.wristbanddemo.utility.ExtendedBluetoothDevice;
import com.realsil.android.wristbanddemo.utility.GlobalGatt;
import com.realsil.android.wristbanddemo.utility.GlobalGreenDAO;
import com.realsil.android.wristbanddemo.utility.HighLightView;
import com.realsil.android.wristbanddemo.utility.RefreshableLinearLayoutView;
import com.realsil.android.wristbanddemo.utility.RefreshableScanView;
import com.realsil.android.wristbanddemo.utility.SPWristbandConfigInfo;
import com.realsil.android.wristbanddemo.utility.SpecScanRecord;
import com.realsil.android.wristbanddemo.utility.StringByteTrans;
import com.realsil.android.wristbanddemo.utility.ValuePickerFragment;
import com.realsil.android.wristbanddemo.utility.WristbandManager;
import com.realsil.android.wristbanddemo.utility.WristbandManagerCallback;
import com.realsil.android.wristbanddemo.view.SwipeBackActivity;
import com.realsil.android.wristbanddemo.view.SwipeMenu;
import com.realsil.android.wristbanddemo.view.SwipeMenuCreator;
import com.realsil.android.wristbanddemo.view.SwipeMenuItem;
import com.realsil.android.wristbanddemo.view.SwipeMenuListView;

import java.util.Set;
import java.util.UUID;

public class WristbandSettingScanDeviceActivity extends SwipeBackActivity implements DeviceListAdapter.DeviceListCallback{
    // Log
    private final static String TAG = "WristbandSettingScanDeviceActivity";
    private final static boolean D = true;

    private static final int REQUEST_ENABLE_BT = 1;

    private WristbandManager mWristbandManager;
    private GlobalGreenDAO mGlobalGreenDAO;
    GlobalGatt mGlobalGatt;

    private TextView mtvScanInfo;

    private ImageView mivScanIcon;
    private ImageView mivScanBack;

    private RelativeLayout mrlScanInfo;

    private SwipeMenuListView mList;

    RefreshableScanView refreshableView;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mScanning;

    private ProgressBar mprgBar;

    private ProgressDialog mProgressDialog = null;

    // Device Scan adapter
    private DeviceListAdapter mAdapter;

    private BluetoothDevice mBluetoothDevice;

    private Toast mToast;

    private HighLightView mHighLightView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wristband_scan_devices);
        // get wristband instance
        mGlobalGreenDAO = GlobalGreenDAO.getInstance();
        mWristbandManager = WristbandManager.getInstance();
        mGlobalGatt = GlobalGatt.getInstance();
        mBluetoothAdapter = mGlobalGatt.getBluetoothAdapter();
        if(mBluetoothAdapter == null) {
            if(D) Log.e(TAG, "May be something error!");
            if(!mGlobalGatt.initialize()) {
                BluetoothManager bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
                if (bluetoothManager == null) {
                    Log.e(TAG, "Unable to initialize BluetoothManager.");
                } else {
                    mBluetoothAdapter = bluetoothManager.getAdapter();
                    if (mBluetoothAdapter == null) {
                        Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
                    }
                }
            }
        }

        mHighLightView = new HighLightView(this);

        // Check whether support BLE
        if (!ensureBLEExists()) {
            finish();
        }

        // set UI
        setUI();

        initialStringFormat();

        if(D) Log.d("BatteryService", "onCreate");
        initialUI();
        /*
        // Broadcast to receive Hid connect message
        mBondStateReceiver = new BondStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        this.registerReceiver(mBondStateReceiver, filter);
        */

        // Show Guide window
        showGuide(mrlScanInfo, R.string.pull_to_scan);
    }

    @Override
    public void removeBond(int position) {
        // stop le scan
        BackgroundScanAutoConnected.getInstance().stopAutoConnect();

        new AlertDialog.Builder(WristbandSettingScanDeviceActivity.this, AlertDialog.THEME_HOLO_LIGHT)
                .setTitle(R.string.settings_remove_bond)
                .setMessage(R.string.settings_remove_bond_tip)
                .setPositiveButton(R.string.sure, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (D) Log.d(TAG, "remove bond");
                                if (mWristbandManager.isConnect()) {
                                    mWristbandManager.SendRemoveBondCommand();
                                    // remote will disconnect
                                    //mWristbandManager.close();
                                }
                                SPWristbandConfigInfo.setBondedDevice(WristbandSettingScanDeviceActivity.this, null);
                                /*
                                mGlobalGreenDAO.deleteAllSportData();
                                mGlobalGreenDAO.deleteAllSleepData();
                                SPWristbandConfigInfo.deleteAll(WristbandSettingScanDeviceActivity.this);
                                */
                                if(mAdapter != null) {
                                    if(D) Log.d(TAG, "clearDevices");
                                    mAdapter.clearDevices();
                                }
                                initialUI();
                                if(D) Log.d("BatteryService", "removeBond");
                                BackgroundScanAutoConnected.getInstance().forceLeScan();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean ensureBLEExists() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showToast(R.string.bluetooth_not_support_ble);
            return false;
        }
        return true;
    }

    private void initialUI() {
        if(D) Log.d("BatteryService", "initialUI");
        if(!mWristbandManager.isConnect()) {
            if(SPWristbandConfigInfo.getBondedDevice(WristbandSettingScanDeviceActivity.this) != null) {
                mtvScanInfo.setText(getResources().getString(R.string.disconnect_reconnect_it_with_toast));
            } else {
                mtvScanInfo.setText(getResources().getString(R.string.pull_to_scan));
            }
            mivScanIcon.setImageResource(R.mipmap.connect_failure);
        } else {
            if(BmobControlManager.getInstance().checkAPKWorkType()) {
                mtvScanInfo.setText(getResources().getString(R.string.connected_with_toast));
            } else {
                mtvScanInfo.setText(getResources().getString(R.string.connected_with_toast_local));
            }
            mivScanIcon.setImageResource(R.mipmap.connect_succeed);
        }
        // add the bonded devices
        addConnectedAndBondDevices();
    }

    AdapterView.OnItemClickListener mListItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // TODO Auto-generated method stub
            String curBondedDeviceName = SPWristbandConfigInfo.getBondedDevice(WristbandSettingScanDeviceActivity.this);

            if(mAdapter.getConnectState(position)) {
                if(!BmobControlManager.checkAPKWorkType()) {
                    if (D) Log.w(TAG, "Select a connected device, do nothing.");
                } else {
                    if (curBondedDeviceName != null) {
                        if (mAdapter.getDevice(position).getAddress().equals(curBondedDeviceName)) {
                            Intent intent = new Intent(WristbandSettingScanDeviceActivity.this, WristbandDeviceInfoActivity.class);
                            WristbandSettingScanDeviceActivity.this.startActivity(intent);
                        }
                    }
                }
                return;
            }
            if(curBondedDeviceName != null) {
                if(mAdapter.getDevice(position).getAddress().equals(curBondedDeviceName)) {
                    if(D) Log.d(TAG, "Reconnect");
                } else {
                    if (D) Log.w(TAG, "Is bonded, if you want bond another one, please unpair first.");
                    showToast(R.string.bonded_unpair_first);
                    return;
                }
            }

            final BluetoothDevice device = mAdapter.getDevice(position) ;
            mBluetoothDevice = device;

            if (device == null) return;

            if(D) Log.i(TAG, "select a device, the name is " + device.getName() + ", addr is " + device.getAddress());
            
            // Here only support one connect
            if(mWristbandManager.getBluetoothAddress() != null
                    && !mWristbandManager.getBluetoothAddress().equals(device.getAddress())) {
                mWristbandManager.close();
            }
            //点击设备进行连接
            BackgroundScanAutoConnected.getInstance().connectWristbandDevice(device);
        }
    };


    private String mFormatConnectDevice;

    private void initialStringFormat() {
        mFormatConnectDevice = getResources().getString(R.string.connect_with_device_name);
    }

    // add connect device to adapter
    private void addConnectedAndBondDevices() {
        Log.d("BatteryService","addConnectedAndBondDevices");
        if(mBluetoothAdapter == null) {
            if(D) Log.d(TAG, "addConnectedAndBondDevices: mBluetoothAdapter == NULL");
            return;
        }
        // Connect devices not bond
        if(mWristbandManager.isConnect()) {
            if(D) Log.d(TAG, "addConnectedAndBondDevices: mWristbandManager.getBluetoothAddress(): " + mWristbandManager.getBluetoothAddress());
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mWristbandManager.getBluetoothAddress());
            // get the battery first.
            if(mWristbandManager.isReady()
                    /*&& !mWristbandManager.isInSendCommand()*/) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if(mWristbandManager.isReady()
                                /*&& !mWristbandManager.isInSendCommand()*/) {
                            mWristbandManager.readBatteryLevel();
                        } else {
                            if(D) Log.w(TAG, "Not login or is in sending command, maybe something wrong!");
                        }
                    }
                }).start();
            }
            String useName;
            String name = SPWristbandConfigInfo.getInfoKeyValue(WristbandSettingScanDeviceActivity.this, device.getAddress());
            mWristbandManager.registerCallback(mWristbandManagerCallback);
            if(name != null) {
                useName = name;
            } else {
                useName = device.getName();
            }
            if(D) Log.d(TAG, "name: " + name + ", userName: " + useName);
            mAdapter.addBondedDevice(new ExtendedBluetoothDevice(device, useName,
                    ExtendedBluetoothDevice.NO_RSSI, ExtendedBluetoothDevice.DEVICE_IS_BONDED, true));
        } else {
            Log.d("saomiao","一开始就执行到这里");
            String addr = SPWristbandConfigInfo.getBondedDevice(WristbandSettingScanDeviceActivity.this);
            if(addr != null) {
                Log.d("saomiao","一开始就执行到这里");
                if(D) Log.d(TAG, "addConnectedAndBondDevices: addr: " + addr);
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(addr);
                String useName;
                String name = SPWristbandConfigInfo.getInfoKeyValue(WristbandSettingScanDeviceActivity.this, device.getAddress());
                if(name != null) {
                    useName = name;
                } else {
                    useName = device.getName();
                }
                if(D) Log.d(TAG, "name: " + name + ", userName: " + useName);
                if(device != null) {
                    mAdapter.addBondedDevice(new ExtendedBluetoothDevice(device, useName,
                            ExtendedBluetoothDevice.NO_RSSI, ExtendedBluetoothDevice.DEVICE_IS_BONDED, false));
                }
            }
        }

    }


    /**
     * if scanned device already in the list then update it otherwise add as a new device
     */
    private void addScannedDevice(final BluetoothDevice device, final int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String lastBondDeviceName = SPWristbandConfigInfo.getInfoKeyValue(WristbandSettingScanDeviceActivity.this
                        , device.getAddress());
                String deviceName = device.getName();
                if(lastBondDeviceName != null) {
                    if(D) Log.i(TAG, "Is the saved device, use the last name. deviceName: " + deviceName + ", lastBondDeviceName: " + lastBondDeviceName);
                    deviceName = lastBondDeviceName;
                }
                mAdapter.addOrUpdateDevice(new ExtendedBluetoothDevice(device, deviceName, rssi, ExtendedBluetoothDevice.DEVICE_NOT_BONDED, false));
            }
        });
    }

    private void setUI() {
        mtvScanInfo = (TextView) findViewById(R.id.tvScanInfo);

        mivScanIcon = (ImageView) findViewById(R.id.ivScanIcon);
        mrlScanInfo = (RelativeLayout) findViewById(R.id.rlScanInfo);
        mivScanBack = (ImageView) findViewById(R.id.ivScanBack);
        mivScanBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mWristbandManager.SendDataRequest();
                finish();
            }
        });

        refreshableView = (RefreshableScanView) findViewById(R.id.refreshable_view);
        refreshableView.setOnRefreshListener(new RefreshableScanView.PullToRefreshListener() {
            @Override
            public void onRefresh() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mAdapter != null) {
                            mAdapter.clearDevices();
                        }
                        initialUI();
                        if(D) Log.d("BatteryService", "setUI");
                        BackgroundScanAutoConnected.getInstance().forceLeScan();
                    }
                });
                //refreshableView.finishRefreshing();
            }
        }, 0);

        // Initializes list view adapter.
        mList = (SwipeMenuListView) findViewById(R.id.lvWristbandDevice);
        mList.setMenuCreator(mSwipMenuCreator);
        mList.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public void onMenuItemClick(int position, SwipeMenu menu, int index) {
                if (D) Log.e(TAG, "setOnMenuItemClickListener, index: " + index);
                switch (index) {
                    case 0:
                        // remove bond
                        removeBond(position);
                        break;
                }
            }
        });
        mList.setOnSwipeListener(new SwipeMenuListView.OnSwipeListener() {

            @Override
            public void onSwipeStart(int position) {
                // swipe start
                //if(D) Log.e(TAG, "onSwipeStart");
                allowDrag(false);
            }

            @Override
            public void onSwipeEnd(int position) {
                // swipe end
                //if(D) Log.e(TAG, "onSwipeEnd");
                allowDrag(true);
            }
        });
        mAdapter = new DeviceListAdapter(this, this);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(mListItemClickListener);

        mprgBar = (ProgressBar)findViewById(R.id.progress_bar);
    }

    BackgroundScanAutoConnected.BackgroundScanCallback mBackgroundScanCallback
            = new BackgroundScanAutoConnected.BackgroundScanCallback() {
        public void onWristbandDeviceFind(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    if (D) Log.d(TAG, "onLeScan() - Device name is: " + device.getName() +
                            " - address is: " + device.getAddress());
                    // add device to adapter
                    addScannedDevice(device, rssi);
                }
            });
        }

        public void onLeScanEnable(boolean enable) {
            if(D) Log.d(TAG, "onLeScanEnable, enable: " + enable);
            if(enable) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mprgBar.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshableView.finishRefreshing();
                        mprgBar.setVisibility(View.GONE);
                    }
                });
            }
        }

        public void onWristbandLoginStateChange(final boolean connected) {
            //if(D) Log.d("BatteryService","onWristbandLoginStateChange");
            //if(D) Log.d("bondremove","手环登录状态发生改变");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    initialUI();

                    if(connected == false) {
                        if (mAdapter != null) {
                            if (D) Log.d(TAG, "clearDevices");
                            mAdapter.clearDevices();
                        }

                        BackgroundScanAutoConnected.getInstance().forceLeScan();
                    }
                }
            });

            if(!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    };

    // Application Layer callback
    WristbandManagerCallback mWristbandManagerCallback = new WristbandManagerCallback() {
        @Override
        public void onNameRead(final String data) {
            if(D) Log.d(TAG, "onNameRead");
            if(mWristbandManager.isConnect()) {
                if(D) Log.d(TAG, "onNameRead, name: " + data);
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mWristbandManager.getBluetoothAddress());
                mAdapter.addBondedDevice(new ExtendedBluetoothDevice(device, data,
                        ExtendedBluetoothDevice.NO_RSSI, ExtendedBluetoothDevice.DEVICE_IS_BONDED, true));
                //mAdapter.notifyDataSetChanged();
                SPWristbandConfigInfo.setInfoKeyValue(WristbandSettingScanDeviceActivity.this, device.getAddress(), data);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        }

        @Override
        public void onBatteryRead(int value) {
            if(D) Log.d("BatteryService", "onBatteryRead, value: " + value);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });

            new Thread(new Runnable() {
                @Override
                public void run() {

                    // Check again
                    if (!mWristbandManager.isReady()
                               /* || mWristbandManager.isInSendCommand()*/) {
                        if(D) Log.w(TAG, "onNameRead, is in sending command!");
                        return;
                    }
                    mWristbandManager.getDeviceName();
                }
            }).start();
        }
    };

    private void showToast(final String message) {
        if(mToast == null) {
            mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(message);
        }
        mToast.show();
    }
    private void showToast(final int message) {
        if(mToast == null) {
            mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        } else {
            mToast.setText(message);
        }
        mToast.show();
    }

    private void showProgressBar(final int message) {
        mProgressDialog = ProgressDialog.show(WristbandSettingScanDeviceActivity.this
                , null
                , getResources().getString(message)
                , true);
        mProgressDialog.setCancelable(false);

        mProgressBarSuperHandler.postDelayed(mProgressBarSuperTask, 30 * 1000);
    }

    private void showProgressBar(final String message) {
        mProgressDialog = ProgressDialog.show(WristbandSettingScanDeviceActivity.this
                , null
                , message
                , true);
        mProgressDialog.setCancelable(false);

        mProgressBarSuperHandler.postDelayed(mProgressBarSuperTask, 30 * 1000);
    }

    private void cancelProgressBar() {
        if(mProgressDialog != null) {
            if(mProgressDialog.isShowing()) {
                mProgressDialog.cancel();
            }
        }

        mProgressBarSuperHandler.removeCallbacks(mProgressBarSuperTask);
    }

    // Alarm timer
    Handler mProgressBarSuperHandler = new Handler();
    Runnable mProgressBarSuperTask = new Runnable(){
        @Override
        public void run() {
            // TODO Auto-generated method stub
            if(D) Log.w(TAG, "Wait Progress Timeout");
            showToast(R.string.progress_bar_timeout);
            mWristbandManager.close();
            // stop timer
            cancelProgressBar();
        }
    };

    @Override
    protected void onStop() {
        if(D) Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onResume() {
        if(D) Log.d(TAG, "onResume()");
        super.onResume();

        BackgroundScanAutoConnected.getInstance().registerCallback(mBackgroundScanCallback);

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            if(!mWristbandManager.isConnect()) {
                BackgroundScanAutoConnected.getInstance().forceLeScan();
            }
        }
    }

    @Override
    protected void onPause() {
        if(D) Log.d(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if(D) Log.d(TAG, "onDestroy()");
        super.onDestroy();

        mAdapter.clearDevices();

        BackgroundScanAutoConnected.getInstance().stopAutoConnect();
        BackgroundScanAutoConnected.getInstance().unregisterCallback(mBackgroundScanCallback);
        mWristbandManager.unRegisterCallback(mWristbandManagerCallback);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    //BackgroundScanAutoConnected.getInstance().forceLeScan();
                    showToast(R.string.bluetooth_enabled);
                } else {
                    // User did not enable Bluetooth or an error occured
                    if(D) Log.e(TAG, "BT not enabled");
                    showToast(R.string.bluetooth_not_enabled);
                    finish();
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        // make sure finish
        finish();
    }

    private void showGuide(View v, int id) {
        String s = getResources().getString(id);
        showGuide(v, s);
    }
    private void showGuide(View v, String s) {
        if(!isFirstLoad()) {
            return;
        }
        final int defaultOrientation = getRequestedOrientation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        SPWristbandConfigInfo.setFirstAppStartFlag(this, false);
        mHighLightView.showTipForView(v, s, HighLightView.HIGH_LIGHT_VIEW_TYPE_RECT_SPEC, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRequestedOrientation(defaultOrientation);
                v.callOnClick();
            }
        });
    }
    private boolean isFirstLoad() {
        return SPWristbandConfigInfo.getFirstAppStartFlag(this);
    }


    SwipeMenuCreator mSwipMenuCreator = new SwipeMenuCreator() {

        @Override
        public void create(SwipeMenu menu) {
            // Create different menus depending on the view type
            switch (menu.getViewType()) {
                case DeviceListAdapter.TYPE_BONDED_ITEM:
                    createMenu1(menu);
                    break;
                default:
                    // do nothing
                    break;
            }
        }

        private void createMenu1(SwipeMenu menu) {
            SwipeMenuItem removeBondItem = new SwipeMenuItem(
                    getApplicationContext());
            // set item background
            removeBondItem.setBackground(new ColorDrawable(Color.rgb(0xF9,
                    0x3F, 0x25)));
            // set item width
            removeBondItem.setWidth(dp2px(90));
            // set item title
            removeBondItem.setTitle(getString(R.string.remove_bond));
            // set item title fontsize
            removeBondItem.setTitleSize(18);
            // set item title font color
            removeBondItem.setTitleColor(Color.WHITE);
            // add to menu
            menu.addMenuItem(removeBondItem);
        }
    };

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
