package com.appgambit.android_logger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.net.Uri;
import android.text.format.Formatter;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class WebServerService extends Service {
    private static final int SERVER_PORT = 12993;
    private ServerSocket serverSocket;
    private boolean isServerRunning;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String basePath;

    @Override
    public void onCreate() {
        super.onCreate();
        basePath = "/storage/emulated/0/Android/data/com.appgambit.android_logger/files/Log_Datas";
        startServer();
    }

    private void startServer() {
        isServerRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(SERVER_PORT);

                    while (isServerRunning) {
                        Socket clientSocket = serverSocket.accept();
                        handleClientRequest(clientSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    // You should add more detailed error handling here
                }
            }
        }).start();
        // Use the Handler to post the code to the UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                String deviceIpAddress = getDeviceIpAddress();
                if (deviceIpAddress != null) {
                    String urlToOpen = "http://" + deviceIpAddress + ":" + SERVER_PORT + "/";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    // Handle the case where you couldn't retrieve the device IP address
                }
            }
        });
    }

    private String getDeviceIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress != 0) {
            return Formatter.formatIpAddress(ipAddress);
        } else {
            return null;
        }
    }

    private void handleClientRequest(Socket clientSocket) {
        try {
            String deviceIpAddress = getDeviceIpAddress();
            String folderPath = basePath; // Use the base path
            File folder = new File(folderPath);
            FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile() && file.getName().endsWith(".txt");
                }
            };
            File[] files = folder.listFiles(filter);

            if (deviceIpAddress != null) {
                String request = getRequest(clientSocket);
                if (request != null && request.startsWith("GET /download?file=")) {
                    // Extract the requested file name
                    String requestedFileName = request.substring(20);
                    File requestedFile = new File(folder, requestedFileName);

                    if (requestedFile.exists()) {
                        // Serve the requested file
                        serveFile(requestedFile, clientSocket);
                    } else {
                        // Handle file not found
                        serveFileNotFound(clientSocket);
                    }
                } else {
                    // Serve the list of files
                    serveFileList(clientSocket, files);
                }
            } else {
                // Handle the case where you couldn't retrieve the device IP address
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            // You should add more detailed error handling here
        }
    }


    private String getRequest(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String request = reader.readLine();
            if (request != null) {
                String[] parts = request.split(" ");
                if (parts.length > 1) {
                    return parts[1];
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void serveFile(File file, Socket clientSocket) {
        try {
            OutputStream os = clientSocket.getOutputStream();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;

            os.write("HTTP/1.1 200 OK\r\n".getBytes());
            os.write("Content-Type: application/octet-stream\r\n".getBytes());
            os.write(("Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n").getBytes());
            os.write("\r\n".getBytes());

            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            fis.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void serveFileNotFound(Socket clientSocket) {
        try {
            OutputStream os = clientSocket.getOutputStream();
            os.write("HTTP/1.1 404 Not Found\r\n".getBytes());
            os.write("Content-Type: text/plain\r\n".getBytes());
            os.write("\r\n".getBytes());
            os.write("File not found".getBytes());
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void serveFileList(Socket clientSocket, File[] files) {
        try {
            OutputStream os = clientSocket.getOutputStream();
            StringBuilder response = new StringBuilder();

            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Content-Type: text/html\r\n");
            response.append("\r\n");

            response.append("<!DOCTYPE html>\n");
            response.append("<html>\n");
            response.append("<head>\n");
            response.append("    <title>File List</title>\n");
            response.append("</head>\n");
            response.append("<body>\n");
            response.append("    <h1>File List</h1>\n");
            response.append("    <ul>\n");

            for (File file : files) {
                response.append("        <li><a href=\"/download?file=" + file.getName() + "\">" + file.getName() + "</a></li>\n");
            }

            response.append("    </ul>\n");
            response.append("</body>\n");
            response.append("</html>\n");

            os.write(response.toString().getBytes("UTF-8"));
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServerRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                // You should add more detailed error handling here
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
