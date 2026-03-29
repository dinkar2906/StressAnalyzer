from flask import Flask, request, jsonify
import random
import numpy as np
import cv2
import torch
import soundfile as sf
import io
import os
import tempfile

# ── Load face model ────────────────────────────────────────────────────────────
from keras.models import load_model as keras_load

print("🔄 Loading face model...")
face_model = keras_load("fer2013_mini_XCEPTION.102-0.66.hdf5", compile=False)

emotion_labels = ['angry', 'disgust', 'fear', 'happy', 'sad', 'surprise', 'neutral']

stress_map = {
    "angry":   85,
    "fear":    90,
    "sad":     70,
    "happy":   10,
    "neutral": 30,
    "surprise":40,
    "disgust": 60
}

face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)

# ── Load voice model ───────────────────────────────────────────────────────────
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification

model_name = "ehcalabres/wav2vec2-lg-xlsr-en-speech-emotion-recognition"

print("🔄 Loading voice model...")
extractor  = AutoFeatureExtractor.from_pretrained(model_name)
voice_model = Wav2Vec2ForSequenceClassification.from_pretrained(
    model_name, ignore_mismatched_sizes=True
)
voice_model.eval()

print("✅ All models loaded!")

# ── App ────────────────────────────────────────────────────────────────────────
app = Flask(__name__)

SAMPLE_RATE = 16000


# ── Helper: analyze face from image bytes ─────────────────────────────────────
def analyze_face(image_bytes):
    try:
        nparr  = np.frombuffer(image_bytes, np.uint8)
        frame  = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if frame is None:
            return {"stress": 50, "emotion": "unknown"}

        gray  = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = face_cascade.detectMultiScale(gray, 1.3, 5)

        if len(faces) == 0:
            return {"stress": 40, "emotion": "no_face"}

        # Use the largest face
        (x, y, w, h) = max(faces, key=lambda f: f[2] * f[3])

        face = gray[y:y+h, x:x+w]
        face = cv2.resize(face, (64, 64))
        face = face / 255.0
        face = np.reshape(face, (1, 64, 64, 1))

        preds   = face_model.predict(face, verbose=0)
        emotion = emotion_labels[np.argmax(preds)]
        stress  = stress_map.get(emotion, 50)

        return {"stress": int(stress), "emotion": emotion}

    except Exception as e:
        print("❌ Face error:", e)
        return {"stress": 50, "emotion": "error"}


# ── Helper: analyze audio from file bytes ─────────────────────────────────────
def analyze_audio(audio_bytes):
    try:
        # Write to temp file so soundfile can read it
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            tmp.write(audio_bytes)
            tmp_path = tmp.name

        audio, sr = sf.read(tmp_path)
        os.unlink(tmp_path)

        # Convert stereo → mono
        if len(audio.shape) > 1:
            audio = audio.mean(axis=1)

        # Resample to 16000 if needed
        if sr != SAMPLE_RATE:
            import librosa
            audio = librosa.resample(audio, orig_sr=sr, target_sr=SAMPLE_RATE)

        audio = audio.astype(np.float32)

        # Normalize
        if np.max(np.abs(audio)) > 0:
            audio = audio / np.max(np.abs(audio))

        inputs = extractor(
            audio,
            sampling_rate=SAMPLE_RATE,
            return_tensors="pt",
            padding=True
        )

        with torch.no_grad():
            logits = voice_model(**inputs).logits

        probs        = torch.softmax(logits, dim=-1)
        predicted_id = torch.argmax(probs).item()
        confidence   = float(probs[0][predicted_id].item() * 100)
        emotion      = voice_model.config.id2label[predicted_id].lower()

        voice_stress_map = {
            "angry":   85,
            "fear":    90,
            "sad":     70,
            "happy":   10,
            "neutral": 30,
            "surprise":40,
            "disgust": 75
        }

        stress = voice_stress_map.get(emotion, 50)

        return {
            "stress":     int(stress),
            "emotion":    emotion,
            "confidence": round(confidence, 1)
        }

    except Exception as e:
        print("❌ Audio error:", e)
        return {"stress": 50, "emotion": "error", "confidence": 0}


# ── Helper: simulated ECG stress (40–60%) ─────────────────────────────────────
def get_ecg_stress():
    # Simulated realistic ECG — replace with real sensor later
    return random.randint(40, 60)


# ══════════════════════════════════════════════════════════════════════════════
# ROUTES
# ══════════════════════════════════════════════════════════════════════════════

@app.route('/')
def home():
    return "✅ Stress API Running"


# ── /analyze/voice ─────────────────────────────────────────────────────────────
@app.route('/analyze/voice', methods=['POST'])
def analyze_voice():
    """
    Accepts: multipart form with 'audio' file (WAV)
    Returns: { stress, emotion, confidence }
    """
    try:
        audio_file = request.files.get('audio')

        if not audio_file:
            return jsonify({"error": "No audio file"}), 400

        result = analyze_audio(audio_file.read())
        print(f"🎤 Voice → {result}")
        return jsonify(result)

    except Exception as e:
        print("❌ /analyze/voice error:", e)
        return jsonify({"stress": 50, "emotion": "error", "confidence": 0})


# ── /analyze/face ──────────────────────────────────────────────────────────────
@app.route('/analyze/face', methods=['POST'])
def analyze_face_route():
    """
    Accepts: multipart form with 'image' file (JPEG/PNG)
    Returns: { stress, emotion }
    """
    try:
        image_file = request.files.get('image')

        if not image_file:
            return jsonify({"error": "No image file"}), 400

        result = analyze_face(image_file.read())
        print(f"📷 Face → {result}")
        return jsonify(result)

    except Exception as e:
        print("❌ /analyze/face error:", e)
        return jsonify({"stress": 50, "emotion": "error"})


# ── /analyze/final ─────────────────────────────────────────────────────────────
@app.route('/analyze/final', methods=['POST'])
def analyze_final():
    """
    Accepts: multipart form with 'audio' + 'image'
    Returns: { voice_stress, face_stress, ecg_stress, final_stress,
               voice_emotion, face_emotion }

    Weights: voice 40% + face 40% + ecg 20%
    """
    try:
        audio_file = request.files.get('audio')
        image_file = request.files.get('image')

        voice_result = analyze_audio(audio_file.read()) if audio_file else {"stress": 50, "emotion": "unknown"}
        face_result  = analyze_face(image_file.read())  if image_file else {"stress": 50, "emotion": "unknown"}
        ecg_stress   = get_ecg_stress()

        final_stress = int(
            0.40 * voice_result["stress"] +
            0.40 * face_result["stress"]  +
            0.20 * ecg_stress
        )

        response = {
            "voice_stress": voice_result["stress"],
            "voice_emotion": voice_result.get("emotion", "unknown"),
            "face_stress":  face_result["stress"],
            "face_emotion": face_result.get("emotion", "unknown"),
            "ecg_stress":   ecg_stress,
            "final_stress": final_stress
        }

        print(f"📊 Final → {response}")
        return jsonify(response)

    except Exception as e:
        print("❌ /analyze/final error:", e)
        return jsonify({
            "voice_stress": 50, "voice_emotion": "error",
            "face_stress":  50, "face_emotion":  "error",
            "ecg_stress":   50, "final_stress":  50
        })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)