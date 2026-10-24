package com.iceflow.core;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// The test APK runs this service without the target APK's Kotlin runtime.
public class TunProbeService extends Service {
    @Override
    @SuppressWarnings("deprecation")
    public IBinder onBind(Intent intent) {
        ResultReceiver receiver = intent.getParcelableExtra("receiver");
        String host = intent.getStringExtra("host");
        if (receiver != null && host != null) {
            new Thread(() -> {
                String response;
                try (Socket socket = new Socket()) {
                    socket.setSoTimeout(5000);
                    socket.connect(new InetSocketAddress(host, 80), 5000);
                    socket.getOutputStream().write("GET /tun HTTP/1.0\r\nHost: fixture\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = socket.getInputStream().read(buffer)) >= 0 && output.size() < 8192) {
                        output.write(buffer, 0, count);
                        if (output.toString("UTF-8").contains("iceflow-tun-ok")) break;
                    }
                    response = output.toString("UTF-8");
                } catch (Exception error) {
                    response = "probe failed: " + error.getMessage();
                }
                Bundle data = new Bundle();
                data.putString("response", response);
                receiver.send(0, data);
            }, "tun-probe").start();
        }
        return new Binder();
    }
}
