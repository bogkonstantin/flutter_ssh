package tech.bogomolov.ssh;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;

public class SshPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
    private static final String CHANNEL = "ssh";

    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result rawResult) {
        Result result = new MethodResultWrapper(rawResult);
        if (call.method.equals("connectToHost")) {
            connectToHost((HashMap) call.arguments, result);
        } else if (call.method.equals("execute")) {
            execute((HashMap) call.arguments, result);
        } else if (call.method.equals("portForwardL")) {
            portForwardL((HashMap) call.arguments, result);
        } else if (call.method.equals("startShell")) {
            startShell((HashMap) call.arguments, result);
        } else if (call.method.equals("writeToShell")) {
            writeToShell((HashMap) call.arguments, result);
        } else if (call.method.equals("closeShell")) {
            closeShell((HashMap) call.arguments);
        } else if (call.method.equals("isConnected")) {
            isConnected((HashMap) call.arguments, result);
        } else if (call.method.equals("disconnect")) {
            disconnect((HashMap) call.arguments, result);
        } else {
            result.notImplemented();
        }
    }

    private static class MethodResultWrapper implements Result {
        private final Result methodResult;
        private final Handler handler;

        MethodResultWrapper(Result result) {
            methodResult = result;
            handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void success(final Object result) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            methodResult.success(result);
                        }
                    });
        }

        @Override
        public void error(
                final String errorCode, final String errorMessage, final Object errorDetails) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            methodResult.error(errorCode, errorMessage, errorDetails);
                        }
                    });
        }

        @Override
        public void notImplemented() {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            methodResult.notImplemented();
                        }
                    });
        }
    }

    private static class MainThreadEventSink implements EventSink {
        private final EventSink eventSink;
        private final Handler handler;

        MainThreadEventSink(EventSink eventSink) {
            this.eventSink = eventSink;
            handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void success(final Object o) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    eventSink.success(o);
                }
            });
        }

        @Override
        public void error(final String s, final String s1, final Object o) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    eventSink.error(s, s1, o);
                }
            });
        }

        @Override
        public void endOfStream() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    eventSink.endOfStream();
                }
            });
        }
    }

    private static class SSHClient {
        Session _session;
        String _key;
        BufferedReader _bufferedReader;
        DataOutputStream _dataOutputStream;
        Channel _channel = null;
    }

    private static final String LOGTAG = "SshPlugin";

    Map<String, SSHClient> clientPool = new HashMap<>();
    private EventSink eventSink;

    private SSHClient getClient(final String key, final Result result) {
        SSHClient client = clientPool.get(key);
        if (client == null)
            result.error("unknown_client", "Unknown client", null);
        return client;
    }

    @Override
    public void onListen(Object arguments, EventSink events) {
        this.eventSink = new MainThreadEventSink(events);
    }

    @Override
    public void onCancel(Object arguments) {
        this.eventSink = null;
    }

    private void connectToHost(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String key = args.get("id").toString();
                    String host = args.get("host").toString();
                    int port = (int) args.get("port");
                    String username = args.get("username").toString();

                    JSch jsch = new JSch();

                    String password = "";
                    if (args.get("passwordOrKey").getClass() == args.getClass()) {
                        HashMap keyPairs = (HashMap) args.get("passwordOrKey");
                        byte[] privateKey = keyPairs.containsKey("privateKey") ? keyPairs.get("privateKey").toString().getBytes() : null;
                        byte[] publicKey = keyPairs.containsKey("publicKey") ? keyPairs.get("publicKey").toString().getBytes() : null;
                        byte[] passphrase = keyPairs.containsKey("passphrase") ? keyPairs.get("passphrase").toString().getBytes() : null;
                        jsch.addIdentity("default", privateKey, publicKey, passphrase);

                    } else {
                        password = args.get("passwordOrKey").toString();
                    }

                    Session session = jsch.getSession(username, host, port);

                    if (password.length() > 0)
                        session.setPassword(password);

                    Properties properties = new Properties();
                    properties.setProperty("StrictHostKeyChecking", "no");
                    session.setConfig(properties);
                    session.connect();

                    if (session.isConnected()) {
                        SSHClient client = new SSHClient();
                        client._session = session;
                        client._key = key;
                        clientPool.put(key, client);

                        Log.d(LOGTAG, "Session connected");
                        result.success("session_connected");
                    }
                } catch (Exception error) {
                    Log.e(LOGTAG, "Connection failed: " + error.getMessage());
                    result.error("connection_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void disconnect(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SSHClient client = getClient(args.get("id").toString(), result);
                    if (client == null)
                        return;

                    Session session = client._session;
                    session.disconnect();
                    result.success("disconnected");
                } catch (Exception error) {
                    Log.e(LOGTAG, "Error executing command: " + error.getMessage());
                    result.error("execute_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void execute(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SSHClient client = getClient(args.get("id").toString(), result);
                    if (client == null)
                        return;

                    Session session = client._session;
                    ChannelExec channel = (ChannelExec) session.openChannel("exec");

                    InputStream in = channel.getInputStream();

                    channel.setCommand(args.get("cmd").toString());
                    channel.connect();

                    String line, response = "";
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    while ((line = reader.readLine()) != null) {
                        response += line + "\r\n";
                    }

                    result.success(response);
                } catch (Exception error) {
                    Log.e(LOGTAG, "Error executing command: " + error.getMessage());
                    result.error("execute_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void portForwardL(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SSHClient client = getClient(args.get("id").toString(), result);
                    if (client == null)
                        return;

                    Session session = client._session;
                    int rport = Integer.parseInt(args.get("rport").toString());
                    int lport = Integer.parseInt(args.get("lport").toString());
                    String rhost = args.get("rhost").toString();
                    int assinged_port = session.setPortForwardingL(lport, rhost, rport);

                    result.success(Integer.toString(assinged_port));
                } catch (JSchException error) {
                    Log.e(LOGTAG, "Error connecting portforwardL:" + error.getMessage());
                    result.error("portforwardL_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void startShell(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String key = args.get("id").toString();
                    SSHClient client = getClient(args.get("id").toString(), result);
                    if (client == null)
                        return;

                    Session session = client._session;
                    Channel channel = session.openChannel("shell");

                    InputStream in = channel.getInputStream();

                    ((ChannelShell) channel).setPtyType(args.get("ptyType").toString());
                    channel.connect();

                    client._channel = channel;
                    client._bufferedReader = new BufferedReader(new InputStreamReader(in));
                    client._dataOutputStream = new DataOutputStream(channel.getOutputStream());

                    result.success("shell_started");

                    String line;
                    while (client._bufferedReader != null && (line = client._bufferedReader.readLine()) != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", "Shell");
                        map.put("key", key);
                        map.put("value", line + '\n');
                        sendEvent(map);
                    }

                } catch (Exception error) {
                    Log.e(LOGTAG, "Error starting shell: " + error.getMessage());
                    result.error("shell_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void writeToShell(final HashMap args, final Result result) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SSHClient client = getClient(args.get("id").toString(), result);
                    if (client == null)
                        return;

                    client._dataOutputStream.writeBytes(args.get("cmd").toString());
                    client._dataOutputStream.flush();
                    result.success("write_success");
                } catch (IOException error) {
                    Log.e(LOGTAG, "Error writing to shell:" + error.getMessage());
                    result.error("write_failure", error.getMessage(), null);
                }
            }
        }).start();
    }

    private void closeShell(final HashMap args) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    SSHClient client = clientPool.get(args.get("id"));
                    if (client == null)
                        return;

                    if (client._channel != null) {
                        client._channel.disconnect();
                    }

                    if (client._dataOutputStream != null) {
                        client._dataOutputStream.flush();
                        client._dataOutputStream.close();
                    }

                    if (client._bufferedReader != null) {
                        client._bufferedReader.close();
                        client._bufferedReader = null;
                    }
                } catch (IOException error) {
                    Log.e(LOGTAG, "Error closing shell:" + error.getMessage());
                }
            }
        }).start();
    }

    private void isConnected(final HashMap args, final Result result) {
        SSHClient client = clientPool.get(args.get("id"));
        if (client == null) {
            result.success("false");
        } else if (client._session == null || !client._session.isConnected()) {
            result.success("false");
        } else {
            result.success("true");
        }
    }

    private void sendEvent(Map<String, Object> event) {
        if (eventSink != null)
            eventSink.success(event);
    }
}
