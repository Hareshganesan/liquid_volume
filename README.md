# Liquid Volume 💧

A lightweight, modern Android volume panel with **native GPU hardware blur** (`RenderEffect`) for Android 12+.

Designed specifically to bypass OEM restrictions (like ColorOS / OxygenOS `LTW=light`) safely by running as a standalone user-space overlay.

---

## Features
- **Real-Time Gaussian Blur:** True hardware-accelerated frosted glass effect rendered directly via GPU.
- **Zero Paywalls & No Ads:** Completely free, open, and lightweight.
- **Volume Button Interception:** Custom `AccessibilityService` seamlessly catches volume keys and suppresses the stock OEM slider.
- **Interactive Touch Sliders:** Slide your finger directly on the glass panel to smoothly adjust volume levels with haptic feedback.
- **Customizable:**
  - Left / Right side placement.
  - Adjustable blur radius (10px to 60px).
  - Modern accent colors (Indigo, Cyan, Emerald, Amber, Rose).

---

## How to Build the APK in 2 Minutes (Using GitHub Actions)

You don't need Android Studio or any SDK installed on your computer. GitHub's cloud will compile it automatically:

1. **Initialize git and commit the code:**
   ```bash
   cd C:\Users\hares\.gemini\antigravity\scratch\VolumePanelApp
   git init
   git add .
   git commit -m "Initial commit of Liquid Volume app"
   ```

2. **Create a new repository on GitHub:**
   - Go to [github.com/new](https://github.com/new).
   - Name your repo (e.g. `liquid-volume`).
   - Leave it **Public** or **Private** and click **Create repository**.

3. **Push to GitHub:**
   ```bash
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
   git push -u origin main
   ```

4. **Download your APK:**
   - Go to the **Actions** tab on your GitHub repository.
   - Click on the active workflow run named **"Build Android APK"**.
   - Under the **Artifacts** section at the bottom, download **`LiquidVolume-Debug-APK`**.
   - Unzip and install `app-debug.apk` directly on your phone!
