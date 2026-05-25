# StressAnalyzer

A real-time multimodal stress detection system that combines facial emotion recognition,
speech emotion recognition, and ECG-based physiological analysis to estimate stress levels.
Built as a B.Tech final year project at BIET Jhansi (2025–26).

[![Download APK](https://img.shields.io/badge/Download-APK-green?style=flat&logo=android)](https://github.com/dinkar2906/StressAnalyzer/releases/latest)

---

## Overview

Most stress detection systems rely on a single input signal, which makes them fragile — poor
lighting breaks facial systems, background noise breaks speech systems, electrode artifacts
break ECG systems. This project addresses that by fusing all three.

Three independent modalities are processed concurrently and combined using a weighted late
fusion formula:
Stress (%) = 0.4 × ECG + 0.3 × Face + 0.3 × Voice

The ECG modality receives the highest weight because it captures involuntary physiological
responses that cannot be consciously suppressed. Evaluated on 30 subjects using the Trier
Social Stress Test (TSST) protocol.

---

## Results

| Modality                        | Accuracy | Precision | Recall | F1-Score | MAE   |
|---------------------------------|----------|-----------|--------|----------|-------|
| Facial Emotion (mini_XCEPTION)  | 82.7%    | 80.3%     | 84.1%  | 0.821    | 9.4%  |
| Speech Emotion (wav2vec2)       | 80.3%    | 78.9%     | 81.7%  | 0.803    | 11.2% |
| ECG / HRV Analysis              | 85.4%    | 83.7%     | 87.2%  | 0.854    | 7.8%  |
| **Multi-Modal Fusion**          | **86.2%**| **84.9%** | **87.8%**| **0.863**| **6.9%** |

HRV features (RMSSD, SDNN, heart rate) showed statistically significant differences between
baseline and stress conditions across all 30 subjects (p < 0.001, Cohen's d > 1.5).

---

## Tech Stack

| Layer      | Technology                                                   |
|------------|--------------------------------------------------------------|
| Backend    | Python 3.9, Flask 2.3, ngrok                                 |
| Face       | TensorFlow/Keras 2.13, OpenCV 4.8, mini_XCEPTION CNN         |
| Speech     | HuggingFace Transformers 4.35, wav2vec2, SoundDevice 0.4.6   |
| ECG        | AD8232 + ESP32, NeuroKit2 0.2.7, PyBluez                     |
| Android    | Java, OkHttp 4.11, Gson 2.10, Android API 29+                |

## Architecture

```
Android App (Java)
├── Sends HTTP GET to /api/stress every 5 seconds
└── Displays per-modality and fused stress scores (colour-coded)

Flask Backend (Python)
├── /api/face   — OpenCV capture → mini_XCEPTION CNN → stress %
├── /api/voice  — SoundDevice 5s audio → wav2vec2 → stress %
├── /api/ecg    — ESP32 Bluetooth → NeuroKit2 HRV → stress %
└── /api/stress — ThreadPoolExecutor runs all 3 concurrently → fusion

Hardware
└── AD8232 ECG sensor → ESP32 (250 Hz ADC) → Bluetooth SPP → Flask
```

> **Total pipeline latency: ~3.8s per cycle** (within 5-second update window).  
> wav2vec2 inference dominates at ~2.1s · face ~0.9s · ECG ~0.8s

## Getting Started

**Requirements:** Python 3.9+, Android Studio, ngrok, ESP32 with AD8232

```bash
# Clone the repo
git clone https://github.com/dinkar2906/StressAnalyzer.git
cd StressAnalyzer

# Install dependencies
pip install -r requirements.txt

# Start the backend
python server.py

# Expose via ngrok (for Android access)
ngrok http 5000
```

Paste the ngrok URL into the Android app config, then build and run on a device or emulator.

**ECG Hardware Setup:**
Connect AD8232 OUTPUT → ESP32 GPIO34 (ADC1_CH6). Place electrodes in standard
3-lead configuration: RA (right clavicle), LA (left clavicle), RL (lower left abdomen).
Flash the ESP32 firmware from the `firmware/` directory using Arduino IDE.

---

## Project Structure

```
StressAnalyzer/
├── server.py              # Flask entry point — fusion logic, /api/stress
├── face.py                # mini_XCEPTION facial emotion recognition
├── audio.py               # wav2vec2 speech emotion recognition
├── ecg.py                 # NeuroKit2 HRV extraction, ECG stress mapping
├── models/
│   └── mini_xception.h5   # Trained FER model (~200 KB, FER2013)
├── java/                  # Android app source (MVP architecture)
├── res/                   # Android UI resources
└── requirements.txt
```

## Stress Level Classification

| Score (%)  | Level          | Colour |
|------------|----------------|--------|
| 0 – 25     | Low (Relaxed)  | Green  |
| 26 – 50    | Moderate       | Yellow |
| 51 – 75    | High           | Orange |
| 76 – 100   | Critical       | Red    |

---

## Known Limitations

- Facial module degrades below 100 lux ambient light (detection failure rate rises from 3.2% to 18.7%)
- wav2vec2 biases toward Neutral/Angry labels in environments above 60 dB SPL
- ECG accuracy is sensitive to electrode adhesion quality; motion artifacts are the most common error source
- No personalised baseline calibration — subjects with naturally low HRV may show elevated ECG stress scores at rest

---

## Author

Dinkar Upadhyay
[LinkedIn](https://linkedin.com/in/dinkar-up) · [GitHub](https://github.com/dinkar2906)
