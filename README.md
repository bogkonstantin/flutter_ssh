# ssh

SSH client for Flutter. Wraps iOS library [NMSSH](https://github.com/NMSSH/NMSSH) (not tested) and Android library [JSch](http://www.jcraft.com/jsch/).
Based on [ssh](https://github.com/shaqian/flutter_ssh) plugin. Updated to new Android plugins API

## Installation

Add `ssh` as a [dependency in your pubspec.yaml file](https://flutter.io/using-packages/).  
```
dependencies:
  ssh:
    git:
      url: git@github.com:bogkonstantin/flutter_ssh.git
```

## Known issue

- Platform exception in release mode for Android:

  ```
  PlatformException(connection_failure, java.lang.ClassNotFoundException: com.jcraft.jsch.jce.Random, null)
  ```

  There are 2 workarounds:
  
  - Disable shrink:

    `flutter build apk --no-shrink`
  
  - Configure proguard-rules. Refer to [this comment](https://github.com/shaqian/flutter_ssh/issues/27#issuecomment-599180850) for details. 

## Usage

### Create a client using password authentication
```dart
import 'package:ssh/ssh.dart';

var client = new SSHClient(
  host: "my.sshtest",
  port: 22,
  username: "sha",
  passwordOrKey: "Password01.",
);
```

### Create a client using public key authentication
```dart
import 'package:ssh/ssh.dart';

var client = new SSHClient(
  host: "my.sshtest",
  port: 22,
  username: "sha",
  passwordOrKey: {
    "privateKey": """-----BEGIN RSA PRIVATE KEY-----
    ......
-----END RSA PRIVATE KEY-----""",
    "passphrase": "passphrase-for-key",
  },
);
```

#### OpenSSH keys

Recent versions of OpenSSH introduce a proprietary key format that is not supported by most other software, including this one, you must convert it to a PEM-format RSA key using the `puttygen` tool. On Windows this is a graphical tool. On the Mac, install it per [these instructions](https://www.ssh.com/ssh/putty/mac/). On Linux install your distribution's `putty` or `puttygen` packages.

* Temporarily remove the passphrase from the original key (enter blank password as the new password)  
`ssh-keygen -p -f id_rsa`
* convert to RSA PEM format  
`puttygen id_rsa -O private-openssh -o id_rsa_unencrypted`
* re-apply the passphrase to the original key  
`ssh-keygen -p -f id_rsa`
* apply a passphrase to the converted key:  
`puttygen id_rsa_unencrypted -P -O private-openssh -o id_rsa_flutter`
* remove the unencrypted version:  
`rm id_rsa_unencrypted`

### Connect client
```dart
await client.connect();
```

### Close client
```dart
await client.disconnect();
```

### Execute SSH command
```dart
var result = await client.execute("ps");
```

### Shell

#### Start shell: 
- Supported ptyType: vanilla, vt100, vt102, vt220, ansi, xterm
```dart
var result = await client.startShell(
  ptyType: "xterm", // defaults to vanilla
  callback: (dynamic res) {
    print(res);     // read from shell
  }
);
```

#### Write to shell: 
```dart
await client.writeToShell("ls\n");
```

#### Close shell: 
```dart
await client.closeShell();
```
