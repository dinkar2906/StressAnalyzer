import cv2
import numpy as np
from keras.models import load_model

model = load_model("fer2013_mini_XCEPTION.102-0.66.hdf5", compile=False)

print("Model input shape:", model.input_shape)

emotion_labels = [
    'angry', 'disgust', 'fear', 'happy',
    'sad', 'surprise', 'neutral'
]

face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
)

cap = cv2.VideoCapture(0, cv2.CAP_V4L2)

if not cap.isOpened():
    print("Camera not working ❌")
    exit()

while True:
    ret, frame = cap.read()

    if not ret:
        print("Frame error ❌")
        break

    frame = cv2.resize(frame, (640, 480))

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(gray, 1.3, 5)

    for (x, y, w, h) in faces:
        face = gray[y:y+h, x:x+w]

        face = cv2.resize(face, (64, 64))
        face = face / 255.0
        face = np.reshape(face, (1, 64, 64, 1))

        preds = model.predict(face, verbose=0)
        emotion = emotion_labels[np.argmax(preds)]

        stress_map = {
            "angry": 85,
            "fear": 90,
            "sad": 70,
            "happy": 10,
            "neutral": 30,
            "surprise": 40,
            "disgust": 60
        }

        stress = stress_map.get(emotion, 50)

        cv2.rectangle(frame, (x, y), (x+w, y+h), (255, 0, 0), 2)

        cv2.putText(frame, f"Emotion: {emotion}", (x, y-10),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0,255,0), 2)

        cv2.putText(frame, f"Stress: {stress}%", (x, y+h+25),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0,0,255), 2)

    cv2.imshow("Final Emotion + Stress Detector", frame)

    if cv2.waitKey(1) == 27:
        break

cap.release()
cv2.destroyAllWindows()