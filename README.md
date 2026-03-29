# 🧠 AI-Based Stress Detection System

A real-time **multimodal stress analysis application** that detects stress levels using **facial expressions, speech signals, and ECG data**.

---

## 🚀 Features

* 🎤 Speech Emotion Recognition (wav2vec2)
* 😀 Facial Emotion Detection (CNN - FER2013)
* ❤️ ECG-based stress estimation
* 📱 Android app integration
* ⚡ Real-time stress analysis
* 🌐 API communication using Flask + ngrok

---

## 🛠️ Tech Stack

* Python, OpenCV, TensorFlow/Keras
* Hugging Face Transformers
* Flask (Backend API)
* Android (Java/Kotlin)
* ngrok (for tunneling)

---

## 🧩 Project Architecture

1. Capture input (voice / face / ECG)
2. Process using ML models
3. Combine results (multimodal fusion)
4. Output stress percentage in real-time

---

## ⚙️ How to Run

### 1. Clone the repo

```
git clone https://github.com/YOUR_USERNAME/AI-Stress-Analyzer.git
cd AI-Stress-Analyzer
```

### 2. Install dependencies

```
pip install -r requirements.txt
```

### 3. Run backend server

```
python server.py
```

### 4. Start ngrok

```
ngrok http 5000
```

### 5. Connect Android app

* Paste ngrok URL in your app

---

## 📊 Output

* Displays stress percentage
* Real-time feedback using multiple inputs

---

## 🎯 Future Improvements

* Improve model accuracy
* Add wearable device integration
* Cloud deployment

---

## 👨‍💻 Author

Dinkar Upadhyay
