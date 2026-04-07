# 🚨 SOS Offline Emergency App

A smart emergency communication system that works **without internet** using Bluetooth.  
Designed for disaster situations where network connectivity is unavailable.

---

## 🌟 Features

- 📡 **Offline Communication**
  - Uses Bluetooth to send SOS messages without internet

- 🔁 **Relay System (A → B → C)**
  - Messages can be forwarded between nearby devices

- 📍 **Live GPS Location**
  - Sends real-time latitude & longitude

- 📌 **Last Known Location**
  - Stores previous location for better tracking

- 🚨 **Manual SOS Button**
  - Send emergency alert instantly

- 📳 **Shake Detection (Auto SOS)**
  - Automatically sends SOS when shake is detected

- 🔔 **Vibration + Alarm Alert**
  - Notifies receiver with strong vibration & sound

- 🗺️ **Open in Google Maps**
  - Tap location to view on map

---

## 🛠️ Tech Stack

- **Language:** Kotlin  
- **Platform:** Android Studio  
- **Communication:** Bluetooth Classic (Socket)  
- **Location:** Google Play Services (FusedLocationProvider)  
- **Sensors:** Accelerometer (Shake Detection)  
- **Storage:** SharedPreferences  

---

## 📱 Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/a238b700-6957-4020-9024-b2dd9983522a" width="250"/><br>
  <b>Emergency Dashboard – Send SOS & Discover Nearby Devices</b>
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/537ad2ce-f3dd-467f-814a-45d30a64d90d" width="250"/><br>
  <b>SOS Alert Received – Displays Real-Time Location & Timestamp</b>
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/eb3970c2-c245-493d-b3e6-a040f4209c95" width="250"/><br>
  <b>Map View – Navigate to Victim Using Live GPS Coordinates</b>
</p>


---

## 🚀 How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/devika402/sos-app.git
