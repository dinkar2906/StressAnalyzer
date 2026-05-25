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
