# Fightowo Android overlay game

This is a minimal Android app that spawns images fetched from e621.net as overlay views on the device screen. Tap an image repeatedly (default 5 taps) to remove it. If too many images are on-screen the heart meter fills; a fuller heart increases spawn rate. When the heart meter maxes out, a short overload burst happens (many images spawn quickly and require extra taps).

Features added:
- Overlay permission handling
- Foreground service that spawns overlay ImageViews
- Settings for difficulty, UI location, vibration, and tags (basic autocomplete against e621)
- Uses OkHttp and Glide to fetch and display images

Notes and disclaimers:
- e621 hosts adult content. This app will fetch images from that site by default; use responsibly and ensure you are legally allowed to view such content.
- This is a minimal proof-of-concept. It does not include production hardening, error handling, or optimizations.

How to build:
- Open the project in Android Studio and build/run on a device.
- You'll need to grant the "Display over other apps" permission for the overlay to work.

