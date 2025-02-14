import 'dart:async';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

typedef void Callback(dynamic result);

class SSHClient {
  MethodChannel _channel = const MethodChannel('ssh');

  String? id;
  String host;
  int port;
  String username;
  dynamic passwordOrKey;
  Callback? shellCallback;
  Callback? uploadCallback;
  Callback? downloadCallback;

  SSHClient({
    required this.host,
    required this.port,
    required this.username,
    required this.passwordOrKey, // password or {privateKey: value, [publicKey: value, passphrase: value]}
  }) {
    var uuid = new Uuid();
    id = uuid.v4();
  }

  Future<String> connect() async {
    var result = await _channel.invokeMethod('connectToHost', {
      "id": id,
      "host": host,
      "port": port,
      "username": username,
      "passwordOrKey": passwordOrKey,
    });
    return result;
  }

  Future<String> execute(String cmd) async {
    var result = await _channel.invokeMethod('execute', {
      "id": id,
      "cmd": cmd,
    });
    return result;
  }

  Future<String> getPortForwardingL() async {
    var result = await _channel.invokeMethod('getPortForwardingL', {
      "id": id,
    });

    return result;
  }

  Future<String> portForwardL(int rport, int lport, String rhost) async {
    var result = await _channel.invokeMethod('portForwardL',
        {"id": id, "rhost": rhost, "rport": rport, "lport": lport});
    return result;
  }

  Future<String> startShell({
    String ptyType = "vanilla", // vanilla, vt100, vt102, vt220, ansi, xterm
    Callback? callback,
  }) async {
    shellCallback = callback;
    var result = await _channel.invokeMethod('startShell', {
      "id": id,
      "ptyType": ptyType,
    });
    return result;
  }

  Future<String> writeToShell(String cmd) async {
    var result = await _channel.invokeMethod('writeToShell', {
      "id": id,
      "cmd": cmd,
    });
    return result;
  }

  Future closeShell() async {
    shellCallback = null;
    await _channel.invokeMethod('closeShell', {
      "id": id,
    });
  }

  disconnect() {
    shellCallback = null;
    uploadCallback = null;
    downloadCallback = null;
    _channel.invokeMethod('disconnect', {
      "id": id,
    });
  }

  Future<bool> isConnected() async {
    bool connected = false; // default to false
    var result = await _channel.invokeMethod('isConnected', {
      "id": id,
    });
    if (result == "true") {
      // results returns a string, therefor we need to check the string 'true'
      connected = true;
    }
    return connected;
  }
}
