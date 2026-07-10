# Third-Party Notices

Open source libraries and models bundled with Scout.
Also surfaced in-app under Settings → Extras & Support → Licenses.

---

## llama.cpp
- Source: https://github.com/ggerganov/llama.cpp
- License: MIT License
- Copyright (c) 2023 Georgi Gerganov
- Used for: on-device inference of TinyLlama via the native LlamaEngine JNI bridge.
- Bundled as: libllama.so, libggml.so, libggml-base.so, libggml-cpu-android_armv8.2_2.so, libllama-common.so

## TinyLlama 1.1B Chat v1.0 (GGUF)
- Source: https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0
- License: Apache 2.0
- Used for: Scout's offline brain. Answers questions and holds conversations without internet.
- Delivered separately via download on first run (not bundled in APK).

## ArcFace MobileFaceNet (InsightFace)
- File: app/src/main/assets/MobileFaceNet.tflite
- Source: https://github.com/deepinsight/insightface
- License: MIT License
- Used for: on-device face recognition — produces 512-dimensional embeddings for identifying family members.

## TensorFlow Lite
- Source: https://www.tensorflow.org/lite
- License: Apache 2.0
- Copyright (c) Google LLC
- Used for: running MobileFaceNet.tflite on-device.

## ML Kit — Face Detection & Image Labeling
- Source: https://developers.google.com/ml-kit
- License: Google APIs Terms of Service
- Used for: detecting face bounding boxes (for embedding) and labeling objects Scout can see.

## CameraX
- Source: https://developer.android.com/jetpack/camerax
- License: Apache 2.0
- Copyright (c) Google LLC
- Used for: camera preview and frame capture.

## OkHttp
- Source: https://github.com/square/okhttp
- License: Apache 2.0
- Copyright (c) Square, Inc.
- Used for: Gemini API HTTP requests.

## Room
- Source: https://developer.android.com/jetpack/room
- License: Apache 2.0
- Copyright (c) Google LLC
- Used for: all on-device databases (memory, people, journal, conversation history).

## NWS Weather API (weather.gov)
- Source: https://www.weather.gov/documentation/services-web-api
- License: Public Domain — U.S. Government
- Used for: current conditions, forecasts, and precipitation data. Free for commercial use.
