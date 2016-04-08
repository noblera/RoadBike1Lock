package com.noble.roadbike1lock;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Lock extends AppCompatActivity {

    private final String MY_UUID = "c899f350-eab9-11e5-a837-0800200c9a66";

    private TextView mLockStatus;
    private Button mLockButton;
    private ImageView mLockImage;

    private BluetoothAdapter mBA;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mSocket;

    private InputStream mInStream;
    private OutputStream mOutStream;

    private final String mSecret = "RoadBike" + Long.toString(1);
    private long mMovingFactor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        final byte[] secretBytes = mSecret.getBytes();

        mLockStatus = (TextView) findViewById(R.id.lock_status_text_view);
        mLockStatus.setText(R.string.status_locked);

        mLockImage = (ImageView) findViewById(R.id.lock_status_image_view);
        mLockImage.setImageResource(R.drawable.lock_icon);

        mLockButton = (Button) findViewById(R.id.lock_button);
        mLockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLockStatus.getText().toString().equals("Unlocked")) {
                    mLockStatus.setText(R.string.status_locked);
                    mLockImage.setImageResource(R.drawable.lock_icon);
                } else {
                    Toast.makeText(Lock.this, "Bike Already Locked", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mBA = BluetoothAdapter.getDefaultAdapter();

        // enable discoverability so client can find lock
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
        startActivity(discoverableIntent);

        // thread for accepting and handling bluetooth communication
        Runnable r = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        mServerSocket = mBA.listenUsingInsecureRfcommWithServiceRecord("BikeLock", UUID.fromString(MY_UUID));
                    } catch (IOException e ) { }

                    while (true) {
                        try {
                            mSocket = mServerSocket.accept();
                        } catch (IOException e) {
                            break;
                        }

                        if (mSocket != null) {
                            try {
                                mServerSocket.close();
                            } catch (IOException e) {
                            }

                            try {
                                mInStream = mSocket.getInputStream();
                                mOutStream = mSocket.getOutputStream();
                            } catch (IOException e) {
                            }

                            byte[] buffer = new byte[1024];
                            int bytes;

                            try {
                                bytes = mInStream.read(buffer);
                            } catch (IOException e) {
                                //break;
                            }
                            // if we get here, we've gotten an unlock or query attempt
                            // check if password is correct
                            boolean query = true;
                            String q = "q";
                            byte[] byteQ = q.getBytes();
                            String s = new String(buffer, Charset.forName("UTF-8"));
                            for (int i = 0; i < byteQ.length; i++) {
                                if (byteQ[i] != buffer[i]) {
                                    query = false;
                                    break;
                                }
                            }
                            if (!query) { // this is an unlock attempt, check if password is correct
                                if (mLockStatus.getText().equals("Unlocked")) { // First check if bike is already unlocked
                                    String answerUnlocked = "u";
                                    byte[] byteUnlocked = answerUnlocked.getBytes();
                                    try {
                                        mOutStream.write(byteUnlocked);
                                    } catch (IOException e) {
                                    }
                                } else {
                                    boolean correct = true;
                                    String otp = "temp";
                                    // generate OTP
                                    mMovingFactor = (System.currentTimeMillis() / 1000) / 60;
                                    try {
                                        otp = HOTPAlgorithm.generateOTP(secretBytes, mMovingFactor, 6, 0);
                                    } catch (NoSuchAlgorithmException nsae) {
                                    } catch (InvalidKeyException ike) {
                                    }
                                    byte[] otpBytes = otp.getBytes();
                                    for (int i = 0; i < otpBytes.length; i++) {
                                        if (otpBytes[i] != buffer[i]) {
                                            String answer = "n";
                                            byte[] byteAnswer = answer.getBytes();
                                            try {
                                                mOutStream.write(byteAnswer);
                                            } catch (IOException e) {
                                            }
                                            correct = false;
                                            break;
                                        }
                                    }
                                    if (correct) {
                                        mLockStatus.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                mLockStatus.setText(R.string.status_unlocked);
                                            }
                                        });
                                        mLockImage.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                mLockImage.setImageResource(R.drawable.unlock_icon);
                                            }
                                        });

                                        String answer = "y";
                                        byte[] byteAnswer = answer.getBytes();
                                        try {
                                            mOutStream.write(byteAnswer);
                                        } catch (IOException e) {
                                        }
                                    }
                                }
                            } else { // user is attempting to return bike, we need to check if bike is properly locked
                                if (mLockStatus.getText().toString().equals("Locked")) {
                                    String answer = "y";
                                    byte[] byteAnswer = answer.getBytes();
                                    try {
                                        mOutStream.write(byteAnswer);
                                    } catch (IOException e) { }
                                } else {
                                    String answer = "n";
                                    byte[] byteAnswer = answer.getBytes();
                                    try {
                                        mOutStream.write(byteAnswer);
                                    } catch (IOException e) { }
                                }
                            }
                            try {
                                mSocket.close();
                            } catch (IOException e) { }
                        }
                    }
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
    }
}
