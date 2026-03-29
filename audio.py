import torch
import numpy as np
import sounddevice as sd
from transformers import AutoFeatureExtractor, Wav2Vec2ForSequenceClassification


model_name = "ehcalabres/wav2vec2-lg-xlsr-en-speech-emotion-recognition"

print("🔄 Loading voice model...")
extractor = AutoFeatureExtractor.from_pretrained(model_name)

model = Wav2Vec2ForSequenceClassification.from_pretrained(
    model_name,
    ignore_mismatched_sizes=True
)

model.eval()

SAMPLE_RATE = 16000


# =========================
# 🔹 MAIN FUNCTION (FOR API)
# =========================
def get_audio_stress(duration=4):
    try:
        print("\n🎤 Speak now...")

        # Record audio
        audio = sd.rec(int(duration * SAMPLE_RATE),
                       samplerate=SAMPLE_RATE,
                       channels=1,
                       dtype="float32")
        sd.wait()

        audio = audio.flatten()

        # Normalize
        if np.max(np.abs(audio)) > 0:
            audio = audio / np.max(np.abs(audio))

        # Feature extraction
        inputs = extractor(
            audio,
            sampling_rate=SAMPLE_RATE,
            return_tensors="pt",
            padding=True
        )

        # Prediction
        with torch.no_grad():
            logits = model(**inputs).logits

        probs = torch.softmax(logits, dim=-1)

        predicted_id = torch.argmax(probs).item()
        confidence = float(probs[0][predicted_id].item() * 100)

        emotion = model.config.id2label[predicted_id].lower()

        # =========================
        # 🔥 EMOTION → STRESS MAP
        # =========================
        stress_map = {
            "angry": 85,
            "fear": 90,
            "sad": 70,
            "happy": 10,
            "neutral": 30,
            "surprise": 40,
            "disgust": 75
        }

        stress = stress_map.get(emotion, 50)

        return {
            "stress": int(stress),
            "emotion": emotion,
            "confidence": round(confidence, 1)
        }

    except Exception as e:
        print("❌ Audio error:", e)

        return {
            "stress": 50,
            "emotion": "unknown",
            "confidence": 0
        }


# =========================
# 🔹 TEST
# =========================
if __name__ == "__main__":
    result = get_audio_stress()

    print("\n🎯 RESULT")
    print(result)