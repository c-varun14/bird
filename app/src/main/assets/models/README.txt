BirdNET model assets

Place these files in this directory:
1. birdnet.tflite
2. birdnet_labels.txt

Recommended model contract (BirdNET v2.4 FLOAT32 drop-in):
- input: FLOAT32 waveform
- sample rate: 48,000 Hz mono
- window length: 3 seconds (144,000 samples)
- output: per-species confidence probabilities

Expected by:
- com.example.projectbird.core.processor.BirdNetTfliteAudioProcessor

Notes:
- The app runs fully offline.
- Detection is optimized for noisy multi-bird scenes with multi-view fusion and overlap refinement.
- Temporal smoothing uses weighted history plus hysteresis for stable live overlap results.
- If model assets are missing or incompatible, the processor automatically falls back to MockAudioProcessor.
- birdnet_labels.txt must contain one species label per line and match output index order.
