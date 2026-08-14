package com.ecoguard.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String APP_TITLE = "EcoGuard Home";
    private static final String CHANNEL_ID = "ecoguard_alerts";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final int BLUE = Color.rgb(11, 61, 121);
    private final int BLUE_DARK = Color.rgb(6, 43, 86);
    private final int GREEN = Color.rgb(67, 160, 71);
    private final int RED = Color.rgb(211, 47, 47);
    private final int ORANGE = Color.rgb(245, 124, 0);
    private final int SURFACE = Color.rgb(246, 249, 252);
    private final int CARD = Color.WHITE;
    private final int TEXT = Color.rgb(16, 32, 51);
    private final int MUTED = Color.rgb(96, 112, 128);

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private Spinner deviceSpinner;
    private Button connectButton;

    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readerThread;
    private volatile boolean keepReading = false;
    private volatile boolean connected = false;

    private TextView bluetoothCard;
    private TextView fireCard;
    private TextView gasCard;
    private TextView smartCard;
    private TextView alarmCard;
    private TextView sensorReadings;
    private TextView lastMessage;

    private final Switch[] relaySwitches = new Switch[8];
    private boolean updatingSwitches = false;
    private boolean lastFireAlert = false;
    private boolean lastGasAlert = false;

    private final char[] relayOnCommands = {'L', 'R', 'Q', 'G', 'D', 'E', 'C', 'I'};
    private final char[] relayOffCommands = {'P', 'T', 'W', 'F', 'V', 'N', 'M', 'O'};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(BLUE_DARK);
            getWindow().setNavigationBarColor(BLUE_DARK);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        createNotificationChannel();
        requestRequiredPermissions();
        buildInterface();
        refreshPairedDevices();
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(22));
        header.setBackground(roundedGradient(BLUE_DARK, BLUE));
        root.addView(header, matchWrapMargins(0, 0, 0, 14));

        TextView title = new TextView(this);
        title.setText(APP_TITLE);
        title.setTextColor(Color.WHITE);
        title.setTextSize(31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Smart Protection & Energy Automation");
        subtitle.setTextColor(Color.rgb(210, 235, 255));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(6), 0, 0);
        header.addView(subtitle);

        TextView tagline = new TextView(this);
        tagline.setText("Monitor  |  Protect  |  Automate");
        tagline.setTextColor(Color.WHITE);
        tagline.setTextSize(13);
        tagline.setGravity(Gravity.CENTER_HORIZONTAL);
        tagline.setPadding(0, dp(12), 0, 0);
        header.addView(tagline);

        root.addView(sectionTitle("Connection"));
        LinearLayout connectionCard = cardContainer();
        root.addView(connectionCard, matchWrapMargins(0, 0, 0, 14));

        TextView hint = new TextView(this);
        hint.setText("Pair your phone with HC-05 first, then select it here. Common pairing PIN: 1234 or 0000.");
        hint.setTextColor(MUTED);
        hint.setTextSize(13);
        hint.setPadding(0, 0, 0, dp(10));
        connectionCard.addView(hint);

        deviceSpinner = new Spinner(this);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<String>());
        deviceSpinner.setAdapter(deviceAdapter);
        connectionCard.addView(deviceSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout connectionButtons = row();
        connectionCard.addView(connectionButtons, matchWrapMargins(0, 10, 0, 0));
        Button refreshButton = primaryButton("Refresh Devices", BLUE);
        connectButton = primaryButton("Connect", GREEN);
        connectionButtons.addView(refreshButton, weightedButtonParams(1));
        connectionButtons.addView(connectButton, weightedButtonParams(1));
        refreshButton.setOnClickListener(v -> refreshPairedDevices());
        connectButton.setOnClickListener(v -> {
            if (connected) disconnect(); else connectSelectedDevice();
        });

        root.addView(sectionTitle("Live Status"));
        GridLayout statusGrid = new GridLayout(this);
        statusGrid.setColumnCount(2);
        root.addView(statusGrid, matchWrapMargins(0, 0, 0, 14));

        bluetoothCard = statusCard("Bluetooth", "Disconnected", BLUE);
        fireCard = statusCard("Fire", "SAFE", GREEN);
        gasCard = statusCard("Gas", "SAFE", GREEN);
        smartCard = statusCard("Smart Mode", "OFF", MUTED);
        alarmCard = statusCard("Alarm", "OFF", MUTED);
        statusGrid.addView(bluetoothCard, gridParams());
        statusGrid.addView(fireCard, gridParams());
        statusGrid.addView(gasCard, gridParams());
        statusGrid.addView(smartCard, gridParams());
        statusGrid.addView(alarmCard, gridParams());

        sensorReadings = new TextView(this);
        sensorReadings.setText("Gas ADC: --   |   Flame ADC: --");
        sensorReadings.setTextColor(MUTED);
        sensorReadings.setTextSize(13);
        sensorReadings.setPadding(dp(10), dp(8), dp(10), dp(8));
        sensorReadings.setBackground(roundedStroke(Color.WHITE, Color.rgb(221, 229, 238), dp(14)));
        root.addView(sensorReadings, matchWrapMargins(0, 0, 0, 14));

        root.addView(sectionTitle("Safety & Automation"));
        LinearLayout quickCard = cardContainer();
        root.addView(quickCard, matchWrapMargins(0, 0, 0, 14));
        LinearLayout firstActionRow = row();
        quickCard.addView(firstActionRow);
        Button smartOn = primaryButton("Smart ON", GREEN);
        Button smartOff = primaryButton("Smart OFF", BLUE);
        firstActionRow.addView(smartOn, weightedButtonParams(1));
        firstActionRow.addView(smartOff, weightedButtonParams(1));
        smartOn.setOnClickListener(v -> sendCommand('A'));
        smartOff.setOnClickListener(v -> sendCommand('H'));

        LinearLayout secondActionRow = row();
        quickCard.addView(secondActionRow, matchWrapMargins(0, 10, 0, 0));
        Button allOff = primaryButton("All Loads OFF", ORANGE);
        Button muteAlarm = primaryButton("Mute Alarm", RED);
        secondActionRow.addView(allOff, weightedButtonParams(1));
        secondActionRow.addView(muteAlarm, weightedButtonParams(1));
        allOff.setOnClickListener(v -> sendCommand('Y'));
        muteAlarm.setOnClickListener(v -> sendCommand('X'));

        Button statusRequest = outlineButton("Request Current Status");
        quickCard.addView(statusRequest, matchWrapMargins(0, 10, 0, 0));
        statusRequest.setOnClickListener(v -> sendCommand('S'));

        root.addView(sectionTitle("Appliance Control"));
        LinearLayout relayCard = cardContainer();
        root.addView(relayCard, matchWrapMargins(0, 0, 0, 14));
        addRelaySwitch(relayCard, 0, "CH1 - Load 1");
        addRelaySwitch(relayCard, 1, "CH2 - Door / Servo Load");
        addRelaySwitch(relayCard, 2, "CH3 - Load 3");
        addRelaySwitch(relayCard, 3, "CH4 - Load 4");
        addRelaySwitch(relayCard, 4, "CH5 - Smart Load 5");
        addRelaySwitch(relayCard, 5, "CH6 - Smart Load 6");
        addRelaySwitch(relayCard, 6, "CH7 - Smart Load 7");
        addRelaySwitch(relayCard, 7, "CH8 - Load 8");

        root.addView(sectionTitle("System Log"));
        lastMessage = new TextView(this);
        lastMessage.setText("Waiting for EcoGuard Home data...");
        lastMessage.setTextColor(MUTED);
        lastMessage.setTextSize(13);
        lastMessage.setPadding(dp(14), dp(12), dp(14), dp(12));
        lastMessage.setMinLines(3);
        lastMessage.setBackground(roundedStroke(Color.WHITE, Color.rgb(221, 229, 238), dp(14)));
        root.addView(lastMessage, matchWrapMargins(0, 0, 0, 0));

        setContentView(scrollView);
    }

    private void addRelaySwitch(LinearLayout parent, int index, String label) {
        Switch relaySwitch = new Switch(this);
        relaySwitch.setText(label + "   OFF");
        relaySwitch.setTextColor(TEXT);
        relaySwitch.setTextSize(15);
        relaySwitch.setPadding(dp(8), dp(10), dp(8), dp(10));
        relaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.setText(label + (isChecked ? "   ON" : "   OFF"));
            if (!updatingSwitches) {
                sendCommand(isChecked ? relayOnCommands[index] : relayOffCommands[index]);
            }
        });
        relaySwitches[index] = relaySwitch;
        parent.addView(relaySwitch, matchWrapMargins(0, 0, 0, 4));
    }

    @SuppressLint("MissingPermission")
    private void refreshPairedDevices() {
        pairedDevices.clear();
        deviceAdapter.clear();

        if (bluetoothAdapter == null) {
            deviceAdapter.add("Bluetooth is not supported on this phone");
            deviceAdapter.notifyDataSetChanged();
            return;
        }

        if (!hasBluetoothPermission()) {
            requestRequiredPermissions();
            deviceAdapter.add("Bluetooth permission is required");
            deviceAdapter.notifyDataSetChanged();
            return;
        }

        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                deviceAdapter.add("No paired devices found");
            } else {
                for (BluetoothDevice device : bonded) {
                    pairedDevices.add(device);
                    String name = device.getName() == null ? "Unknown Device" : device.getName();
                    deviceAdapter.add(name + "\n" + device.getAddress());
                }
            }
        } catch (SecurityException ex) {
            deviceAdapter.add("Bluetooth permission is required");
        }
        deviceAdapter.notifyDataSetChanged();
    }

    @SuppressLint("MissingPermission")
    private void connectSelectedDevice() {
        if (bluetoothAdapter == null) {
            toast("Bluetooth is not supported on this phone.");
            return;
        }
        if (!hasBluetoothPermission()) {
            requestRequiredPermissions();
            toast("Please allow Bluetooth permission, then try again.");
            return;
        }
        if (pairedDevices.isEmpty()) {
            toast("Pair with HC-05 first, then press Refresh Devices.");
            return;
        }

        int selectedIndex = deviceSpinner.getSelectedItemPosition();
        if (selectedIndex < 0 || selectedIndex >= pairedDevices.size()) {
            toast("Select the HC-05 device first.");
            return;
        }

        BluetoothDevice selectedDevice = pairedDevices.get(selectedIndex);
        setBluetoothUi("Connecting...", ORANGE);
        connectButton.setEnabled(false);

        new Thread(() -> {
            try {
                if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
                BluetoothSocket socket = selectedDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                bluetoothSocket = socket;
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                connected = true;
                runOnUiThread(() -> {
                    connectButton.setEnabled(true);
                    connectButton.setText("Disconnect");
                    setBluetoothUi("Connected", GREEN);
                    toast("Connected to " + safeDeviceName(selectedDevice));
                });
                startReaderThread();
                sendCommand('S');
            } catch (Exception ex) {
                closeSocketQuietly();
                connected = false;
                runOnUiThread(() -> {
                    connectButton.setEnabled(true);
                    connectButton.setText("Connect");
                    setBluetoothUi("Connection failed", RED);
                    toast("Connection failed. Make sure HC-05 is powered and paired.");
                    lastMessage.setText("Connection error: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void startReaderThread() {
        keepReading = true;
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[256];
            StringBuilder line = new StringBuilder();
            while (keepReading && connected && inputStream != null) {
                try {
                    int count = inputStream.read(buffer);
                    if (count <= 0) continue;
                    for (int i = 0; i < count; i++) {
                        char c = (char) buffer[i];
                        if (c == '\n' || c == '\r') {
                            if (line.length() > 0) {
                                String message = line.toString().trim();
                                line.setLength(0);
                                handleIncomingMessage(message);
                            }
                        } else {
                            line.append(c);
                            if (line.length() > 300) line.setLength(0);
                        }
                    }
                } catch (IOException ex) {
                    if (keepReading) {
                        handleDisconnected("Bluetooth connection lost.");
                    }
                    break;
                }
            }
        });
        readerThread.start();
    }

    private void handleIncomingMessage(String message) {
        runOnUiThread(() -> {
            lastMessage.setText(message);
            if (message.startsWith("STATUS") || message.startsWith("ALERT")) {
                parseStatusMessage(message);
            } else if (message.startsWith("SAFE")) {
                updateFireStatus(false);
                updateGasStatus(false);
                setAlarmUi(false);
            } else if (message.startsWith("READY")) {
                lastMessage.setText("EcoGuard Home controller is ready.");
            }
        });
    }

    private void parseStatusMessage(String message) {
        Map<String, String> map = new HashMap<>();
        String[] parts = message.split(",");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals > 0 && equals < part.length() - 1) {
                map.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
            }
        }

        boolean fire = isOne(map.get("FIRE"));
        boolean gas = isOne(map.get("GAS"));
        boolean smart = isOne(map.get("SMART"));
        boolean alarm = isOne(map.get("ALARM"));

        updateFireStatus(fire);
        updateGasStatus(gas);
        setStatusCard(smartCard, "Smart Mode", smart ? "ON" : "OFF", smart ? GREEN : MUTED);
        setAlarmUi(alarm);

        String gasAdc = map.containsKey("GAS_ADC") ? map.get("GAS_ADC") : "--";
        String flameAdc = map.containsKey("FLAME_ADC") ? map.get("FLAME_ADC") : "--";
        sensorReadings.setText("Gas ADC: " + gasAdc + "   |   Flame ADC: " + flameAdc);

        updatingSwitches = true;
        for (int i = 0; i < relaySwitches.length; i++) {
            String relayValue = map.get("R" + (i + 1));
            if (relayValue != null && relaySwitches[i] != null) {
                relaySwitches[i].setChecked(isOne(relayValue));
            }
        }
        updatingSwitches = false;

    }

    private void updateFireStatus(boolean fireDetected) {
        setStatusCard(fireCard, "Fire", fireDetected ? "ALERT" : "SAFE", fireDetected ? RED : GREEN);
        if (fireDetected && !lastFireAlert) {
            triggerUserAlert("Fire Detected", "EcoGuard Home detected flame/fire. Check the area immediately.", 101);
        }
        lastFireAlert = fireDetected;
    }

    private void updateGasStatus(boolean gasDetected) {
        setStatusCard(gasCard, "Gas", gasDetected ? "ALERT" : "SAFE", gasDetected ? ORANGE : GREEN);
        if (gasDetected && !lastGasAlert) {
            triggerUserAlert("Gas Detected", "EcoGuard Home detected abnormal gas level. Ventilate and check the area immediately.", 102);
        }
        lastGasAlert = gasDetected;
    }

    private void setAlarmUi(boolean active) {
        setStatusCard(alarmCard, "Alarm", active ? "ACTIVE" : "OFF", active ? RED : MUTED);
    }

    private boolean isOne(String value) {
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("ON"));
    }

    private void sendCommand(char command) {
        if (!connected || outputStream == null) {
            toast("Connect to HC-05 first.");
            return;
        }
        new Thread(() -> {
            try {
                outputStream.write((byte) command);
                outputStream.flush();
            } catch (IOException ex) {
                handleDisconnected("Command failed. Bluetooth connection may be lost.");
            }
        }).start();
    }

    private void disconnect() {
        keepReading = false;
        connected = false;
        closeSocketQuietly();
        runOnUiThread(() -> {
            connectButton.setText("Connect");
            connectButton.setEnabled(true);
            setBluetoothUi("Disconnected", BLUE);
            toast("Disconnected");
        });
    }

    private void handleDisconnected(String message) {
        keepReading = false;
        connected = false;
        closeSocketQuietly();
        runOnUiThread(() -> {
            connectButton.setText("Connect");
            connectButton.setEnabled(true);
            setBluetoothUi("Disconnected", RED);
            lastMessage.setText(message);
            toast(message);
        });
    }

    private void closeSocketQuietly() {
        try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) { }
        try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) { }
        try { if (bluetoothSocket != null) bluetoothSocket.close(); } catch (Exception ignored) { }
        inputStream = null;
        outputStream = null;
        bluetoothSocket = null;
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void triggerUserAlert(String title, String text, int notificationId) {
        toast(title);
        vibratePhone();
        showNotification(title, text, notificationId);
    }

    private void showNotification(String title, String text, int notificationId) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM);

        if (manager != null) manager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EcoGuard Safety Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Fire and gas safety alerts from EcoGuard Home");
            channel.enableVibration(true);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void vibratePhone() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(700);
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 2001);
        }
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "HC-05" : name;
        } catch (SecurityException ex) {
            return "HC-05";
        }
    }

    private void setBluetoothUi(String state, int color) {
        setStatusCard(bluetoothCard, "Bluetooth", state, color);
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(BLUE_DARK);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(2), dp(8), dp(2), dp(8));
        return title;
    }

    private LinearLayout cardContainer() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
        layout.setBackground(roundedStroke(CARD, Color.rgb(221, 229, 238), dp(18)));
        return layout;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private Button primaryButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedFill(color, dp(12)));
        return button;
    }

    private Button outlineButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(BLUE_DARK);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedStroke(Color.WHITE, BLUE, dp(12)));
        return button;
    }

    private TextView statusCard(String label, String value, int accentColor) {
        TextView textView = new TextView(this);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(15);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setPadding(dp(10), dp(14), dp(10), dp(14));
        setStatusCard(textView, label, value, accentColor);
        return textView;
    }

    private void setStatusCard(TextView view, String label, String value, int accentColor) {
        if (view == null) return;
        view.setText(label + "\n" + value);
        view.setTextColor(accentColor);
        view.setBackground(roundedStroke(Color.WHITE, accentColor, dp(16)));
    }

    private GradientDrawable roundedFill(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedStroke(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedGradient(int start, int end) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightedButtonParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), weight);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private GridLayout.LayoutParams gridParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
