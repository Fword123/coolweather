package com.coolweather.android;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.adapter.BlueToothDeviceAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class edit extends AppCompatActivity implements View.OnClickListener {

    private BluetoothAdapter bTAdatper;
    private ListView listView;
    private BlueToothDeviceAdapter adapter;

    private TextView text_state;
    private TextView text_msg;

    private final int BUFFER_SIZE = 1024;
    private static final String NAME = "BT_DEMO";
    private static final UUID BT_UUID =
            UUID.fromString("02001101-0001-1000-8080-00805F9BA9BA");

    private ConnectThread connectThread;
    private ListenerThread listenerThread;
    private String weather_data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        //接收天气信息
        Intent intent=getIntent();
        weather_data=intent.getStringExtra("extra_data");

        initView();
        //获取本地蓝牙适配器对象
        bTAdatper = BluetoothAdapter.getDefaultAdapter();
        initReceiver();
        listenerThread = new ListenerThread();
        listenerThread.start();
    }
    private void initView() {
        findViewById(R.id.btn_openBT).setOnClickListener(this);
        findViewById(R.id.btn_search).setOnClickListener(this);
        findViewById(R.id.btn_send).setOnClickListener(this);
        text_state = (TextView) findViewById(R.id.text_state);
        text_msg = (TextView) findViewById(R.id.text_msg);
        listView = (ListView) findViewById(R.id.listView);

        adapter = new BlueToothDeviceAdapter//一开始adapter是空的
                (getApplicationContext(), R.layout.bluetooth_device_list_item);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (bTAdatper.isDiscovering()) {
                    bTAdatper.cancelDiscovery();//取消搜索
                }
                BluetoothDevice device = (BluetoothDevice) adapter.getItem(position);
                //连接设备
                connectDevice(device);
            }
        });
    }
    private void initReceiver() {
        //注册广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_openBT:
                openBlueTooth();
                break;
            case R.id.btn_search:
//                String[] list={};
//                ArrayAdapter adapter1=new ArrayAdapter<String>(getApplicationContext(),
//                        android.R.layout.simple_list_item_1, list);
//                listView.setAdapter(adapter1);
                adapter.clear();
                adapter.notifyDataSetChanged();
                searchDevices();
                break;
            case R.id.btn_send:

                if (connectThread != null) {
                    connectThread.sendMsg(weather_data);
                }
                break;
        }
    }
    /**
     * 开启蓝牙
     */
    private void openBlueTooth() {
        if (bTAdatper == null) {
            Toast.makeText(this, "当前设备不支持蓝牙功能", Toast.LENGTH_SHORT).show();
        }
        if (!bTAdatper.isEnabled()) {
           /* Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(i);*/
            bTAdatper.enable();//打开蓝牙
        }
        //开启被其它蓝牙设备发现的功能
        if (bTAdatper.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            //设置为一直开启
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivity(i);
        }
    }

    /**
     * 搜索蓝牙设备
     */
    private void searchDevices() {
        if (bTAdatper.isDiscovering()) {
            bTAdatper.cancelDiscovery();
        }
        getBoundedDevices();
        //开始查找远程蓝牙设备，返回值为boolean
        bTAdatper.startDiscovery();
    }

    /**
     * 获取已经配对过的设备
     */
    private void getBoundedDevices() {
        //获取已经配对过的设备
        Set<BluetoothDevice> pairedDevices = bTAdatper.getBondedDevices();
        //将其添加到设备列表中
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                adapter.add(device);
            }
        }
    }

    /**
     * 连接蓝牙设备
     */
    private void connectDevice(BluetoothDevice device) {
        text_state.setText(getResources().getString(R.string.connecting));
        try {
            //创建Socket
            /*
            createRfcommSocketToServiceRecord会使用您通过的UUID，
            并使用SDP来决定要使用哪个无线电通道进行连接。它还检查以确保服务器正在远程端点上侦听，
            具有相同的UUID。以这种方式，这是获得连接的最可靠的方式：
            它将始终使用正确的通道，如果打开连接成功，您知道另一端可以理解您的协议。
             */
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(BT_UUID);
            //启动连接线程
            connectThread = new ConnectThread(socket, true);
            connectThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //取消搜索
        if (bTAdatper != null && bTAdatper.isDiscovering()) {
            bTAdatper.cancelDiscovery();
        }
        //注销BroadcastReceiver，防止资源泄露
        unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //找到了为配对过的新设备，加入adapter
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra
                        (BluetoothDevice.EXTRA_DEVICE);
                //避免重复添加已经绑定过的设备
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    adapter.add(device);
                    adapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(edit.this,
                        "开始搜索", Toast.LENGTH_SHORT).show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(edit.this,
                        "搜索完毕", Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * 连接线程
     */
    private class ConnectThread extends Thread {

        private BluetoothSocket socket;
        private boolean activeConnect;
        InputStream inputStream;
        OutputStream outputStream;

        private ConnectThread(BluetoothSocket socket, boolean connect) {
            this.socket = socket;
            this.activeConnect = connect;
        }
        @Override
        public void run() {
            try {
                //如果是自动连接 则调用连接方法
                if (activeConnect) {
                    socket.connect();
                }
                text_state.post(new Runnable() {
                    @Override
                    public void run() {
                        text_state.setText(getResources().getString(R.string.connect_success));
                    }
                });
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                byte[] buffer = new byte[BUFFER_SIZE];//1024
                int bytes;
                while (true) {
                    //读取数据
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        final byte[] data = new byte[bytes];
                        System.arraycopy(buffer, 0, data, 0, bytes);
                        text_msg.post(new Runnable() {//也是个线程，运行在主线程中
                            @Override
                            public void run() {
                                text_msg.setText(getResources().getString(R.string.get_msg)+new String(data));
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                text_state.post(new Runnable() {
                    @Override
                    public void run() {
                        text_state.setText(getResources().getString(R.string.connect_error));
                    }
                });
            }
        }
        /**
         * 发送数据
         *
         * @param msg
         */
        public void sendMsg(final String msg) {
            byte[] bytes = msg.getBytes();
            if (outputStream != null) {
                try {
                    //发送数据
                    outputStream.write(bytes);
                    text_msg.post(new Runnable() {
                        @Override
                        public void run() {
                            text_msg.setText(getResources().getString(R.string.send_msgs)+msg);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    text_msg.post(new Runnable() {
                        @Override
                        public void run() {
                            text_msg.setText(getResources().getString(R.string.send_msg_error)+msg);
                        }
                    });
                }
            }
        }
    }
    /**
     * 监听线程
     */
    private class ListenerThread extends Thread {

        private BluetoothServerSocket serverSocket;
        private BluetoothSocket socket;

        @Override
        public void run() {
            try {
                Log.d("listenThread", "运行了");
                /*
                创建一个监听Rfcommon端口的蓝牙监听, 使用accept()方法监听,
                并获取BluetoothSocket对象; 该系统会根据一个服务名称(name)
                和唯一的识别码(uuid)来创建一个SDP服务, 远程蓝牙设备可以根据
                唯一的UUID来连接这个SDP服务器;返回值 : 正在监听蓝牙端口;
                 */
                Log.d("listenThread", "运行了2");
                serverSocket = bTAdatper.listenUsingRfcommWithServiceRecord(
                        NAME, BT_UUID);

                while (true) {
                    Log.d("listenThread", "运行了2");
                    //线程阻塞，等待别的设备连接
                    socket = serverSocket.accept();
                    Log.d("listenThread", "运行了3");
                    text_state.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("listenThread", "运行了4");
                            text_state.setText(getResources().getString(R.string.connecting));
                        }
                    });
                    connectThread = new ConnectThread(socket, false);
                    connectThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
