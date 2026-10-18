# How to Install Lohe APK on Android Device

## APK Location
The debug APK has been successfully built and is located at:
```
C:\Users\davem\rea\work\Lohe\app\build\outputs\apk\debug\app-debug.apk
```
File size: 33,885,527 bytes (~33.9 MB)

## Prerequisites
1. **Android Device** with USB debugging enabled
2. **USB Cable** to connect device to PC
3. **Android SDK Platform Tools** (ADB) installed and accessible

## Step-by-Step Installation Guide

### 1. Enable Developer Options and USB Debugging on Your Android Device
- Go to **Settings** → **About phone**
- Tap **Build number** 7 times to enable Developer Options
- Go back to **Settings** → **System** → **Developer options**
- Enable **USB debugging**

### 2. Connect Your Device to PC
- Use a USB cable to connect your Android device to your Windows PC
- When prompted on your device, select **File Transfer** or **MTP** mode
- Accept the RSA key fingerprint dialog if it appears (allow USB debugging)

### 3. Verify Device Connection
Open Command Prompt or PowerShell and run:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

You should see output like:
```
List of devices attached
XXXXXXXXXXXXXX    device
```

If you see your device ID listed, the connection is successful.

### 4. Install the APK
Run the following command to install the Lohe app:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r "C:\Users\davem\rea\work\Lohe\app\build\outputs\apk\debug\app-debug.apk"
```

The `-r` flag means "reinstall if already installed", which is useful for updates.

### 5. Launch the App
After installation completes successfully, you can:
- Find the Lohe app icon in your app drawer and tap it
- Or launch it directly via ADB:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -n com.lohe.app/.MainActivity
```

## Troubleshooting

### "Device unauthorized" or "No permissions"
- Check your Android device screen for an RSA key fingerprint dialog
- Make sure you've tapped **Allow** on the USB debugging authorization prompt
- Try disconnecting and reconnecting the USB cable

### "Device not found"
- Ensure USB debugging is enabled in Developer Options
- Try a different USB cable or USB port
- Restart the ADB server:
  ```cmd
  C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe kill-server
  C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe start-server
  ```

### "INSTALL_FAILED_TEST_ONLY" or similar
This shouldn't happen with our debug build, but if it does:
```cmd
C:\Users\davem\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r -t "C:\Users\davem\rea\work\Lohe\app\build\outputs\apk\debug\app-debug.apk"
```

## Alternative: Install via Android Studio
If you have Android Studio installed:
1. Open Android Studio
2. Go to **File** → **New** → **Import Project**
3. Select the Lohe project folder
4. Click **Run** → **app** to deploy to your connected device

## Notes
- This is a **debug build** suitable for testing and development
- For distribution, a release build would need to be created with proper signing
- The app requires Android 8.0 (API level 26) or higher to run
- First launch may take a few seconds as the app initializes its database

## Verifying Installation
After installation, you should see:
- App icon labeled "Lohe" with the Kani mascot (dragonfly with headphones)
- Default dark theme interface
- Bottom navigation with Home, Podcasts, Queue, and Player tabs
- Functional play/pause controls in the Player screen

Enjoy using your new Lohe podcast app! 🎧