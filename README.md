# Polish Lyrics TV (Fire TV receiver)

A small Android TV app for Fire TV (Fire OS 6–8). It shows the lyrics screen of Polish Lyrics Translator in full screen.

## How it works

The PC **connects out** to the TV, the way casting does. This works even when the PC firewall blocks incoming connections, as on managed laptops.

- **Port 8789.** The app listens there.
  - The PC connects to `ws://<tv-ip>:8789/link`. The first time it must send the 6-digit code shown on the TV. It then receives a secret it uses for reconnects. After 5 wrong codes, the TV shows a new code.
  - The TV's own WebView loads `http://127.0.0.1:8789/`. Every request is tunnelled to the PC over that WebSocket. HTTP from other devices on the network is refused.
- **Contents.** The app contains no lyrics, no Spotify credentials and no API keys. The TV page itself comes from the PC.
- **Remote.** Play/pause, rewind and fast-forward control Spotify. The arrow keys move between buttons. Back exits the app.

## Build (no Android Studio needed)

This folder is meant to be the root of its own GitHub repository.

1. Create a new **public** repository on GitHub, e.g. `polish-lyrics-tv`. It can be public because it contains no secrets.
2. Upload the **contents** of this folder through *Add file → Upload files*, dragging the files and folders.
   - If the `.github` folder isn't uploaded, use *Add file → Create new file*. Name it `.github/workflows/build-apk.yml` and paste the content of the file from this folder.
3. The **Actions** tab runs *Build APK* in about 3–5 minutes. When it finishes, the APK is published at:
   `https://github.com/<your-user>/<repo>/releases/download/tv-latest/polish-lyrics-tv.apk`

## Install on the Fire TV

1. Install **Downloader** from the Amazon Appstore.
2. Show the developer options: go to *Settings → My Fire TV → About* and select the device name 7 times. Then go back and open *Developer options → Install unknown apps* and turn on **Downloader**.
3. Open Downloader, type the APK address from the release page, then choose **Install** and **Open**.
4. The app shows the **TV address** and a **code**. On the PC, open the **TV** tab, enter both under **Fire TV app** and press **Connect**.

From then on, open "Polish Lyrics TV" from the Fire TV home screen. The PC reconnects by itself while the app on the PC is running.
