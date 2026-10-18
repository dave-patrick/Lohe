# Wireless ADB Installation Guide for Lohe App

## 📱 Prerequisites
1. **Android device** and **Windows PC** must be on the **same Wi-Fi network**
2. **USB debugging enabled** on your Android device (Settings → Developer options)
3. **Initial USB connection** required for first-time wireless ADB setup
4. **Lohe APK** built and ready at: `C:\Users\davem\rea\work\Lohe\app\build\outputs\apk\debug\app-debug.apk`

## 📋 Your Device Information
Based on your input, your Android device appears to be at: **192.168.0.118**

## 🔧 Step-by-Step Wireless ADB Installation

### Step 1: Initial USB Connection (One-time setup)
If you've never used wireless ADB with this device before:

1. Connect your Android device to your PC via USB cable
2. Enable **File Transfer/MTP** mode when prompted
3. On your device, allow USB debugging when the RSA key fingerprint dialog appears
4. Verify connection:
   ```cmd
   C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
   ```
   You should see your device listed

### Step 2: Switch to Wireless Mode
Once connected via USB:

1. Find your device's IP address (you've indicated it's 192.168.0.118):
   - On your device: Settings → About phone → Status → IP address
   - Or via ADB: `adb shell ip route get 8.8.8.8`

2. Switch ADB to TCP/IP mode (port 5555 is standard):
   ```cmd
   C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe tcpip 5555
   ```

3. Disconnect the USB cable

### Step 3: Connect Wirelessly
Now connect to your device using its IP address:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe connect 192.168.0.118:5555
```

You should see:
```
connected to 192.168.0.118:5555
```

Verify the connection:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```
Output should show:
```
List of devices attached
192.168.0.118:5555   device
```

### Step 4: Install the Lohe APK
Now install the app wirelessly:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.0.118:5555 install -r "C:\Users\davem\rea\work\Lohe\app\build\outputs\apk\debug\app-debug.apk"
```

You should see:
```
Performing Streamed Install
Success
```

### Step 5: Launch the App
Start the Lohe app:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.0.118:5555 shell am start -n com.lohe.app/.MainActivity
```

## 🔄 Alternative: Pairing Code Method (Android 11+)
If your device supports Android 11+ and you prefer not to use USB initially:

1. On your Android device:
   - Go to Settings → System → Developer options
   - Enable **Wireless debugging**
   - Tap **Pair device with pairing code**
   - Note the 6-digit code and IP address/port shown

2. On your PC:
   ```cmd
   C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe pair IP_ADDRESS:PORT
   ```
   (Enter the 6-digit code when prompted)

3. Then connect:
   ```cmd
   C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe connect IP_ADDRESS:PORT
   ```

## 🛠️ Troubleshooting

### Connection Issues
- **Device not found**: Ensure device and PC are on same Wi-Fi network
- **Connection refused**: Verify ADB is in TCP/IP mode (`adb tcpip 5555`)
- **Connection timeout**: Check if device screen is on and not asleep
- **Invalid IP**: Double-check your device's actual IP address

### Installation Issues
- **INSTALL_FAILED_ALREADY_EXISTS**: The `-r` flag in the install command handles reinstallation
- **INSTALL_FAILED_TEST_ONLY**: Shouldn't occur with our debug build
- **Insufficient storage**: Clear cache/data or free up space on device

### Verifying Installation
After successful installation:
1. Look for the Lohe app icon (Kani mascot) in your app drawer
2. The app should launch in dark mode by default
3. Bottom navigation should show: Home, Podcasts, Queue, Player
4. Player screen should have functional play/pause controls

## 📴 Disconnecting Wireless ADB
When finished, you can disconnect:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe disconnect 192.168.0.118:5555
```
Or to disconnect all:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe disconnect
```

## 💡 Tips
- Wireless ADB connection may drop if device sleeps - keep screen awake during development
- For better performance, ensure strong Wi-Fi signal
- USB connection is still faster for large file transfers
- To revert to USB mode: Connect via USB and run `adb usb`

---

**Your Lohe app is ready for wireless installation!**  
Follow these steps to install and enjoy your custom podcast player with glassmorphism UI and Kani mascot. 🎧