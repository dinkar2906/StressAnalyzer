import neurokit2 as nk
import numpy as np
import time
import random


SAMPLING_RATE = 250
WINDOW_SIZE = 5


# fhjhvhjkh



print("🧪 Generating ECG (HR 60–80 → Stress 40–60%)")

while True:
    
    heart_rate = random.randint(60, 80)

    print(f"\n🎲 Generated HR: {heart_rate} bpm")

    ecg_signal = nk.ecg_simulate(
        duration=WINDOW_SIZE,
        sampling_rate=SAMPLING_RATE,
        heart_rate=heart_rate
    )

    buffer = []

    for value in ecg_signal:
        value = int(value * 1000)
        buffer.append(value)
        time.sleep(1 / SAMPLING_RATE)

    ecg_window = np.array(buffer)

    print("\n🧠 Processing ECG...")

    try:
        cleaned = nk.ecg_clean(ecg_window, sampling_rate=SAMPLING_RATE)
        signals, info = nk.ecg_process(cleaned, sampling_rate=SAMPLING_RATE)

        detected_hr = int(np.mean(signals["ECG_Rate"]))

       
        stress = 40 + (detected_hr - 60)

       
        stress = max(40, min(60, stress))

        if stress < 45:
            level = "Low-Moderate 🙂"
        elif stress < 55:
            level = "Moderate 😐"
        else:
            level = "High-Moderate 😰"

        
        print(f"💓 Detected HR: {detected_hr} bpm")
        print(f"❤️ Stress: {stress}% ({level})")

    except Exception as e:
        print("❌ Processing error:", e)