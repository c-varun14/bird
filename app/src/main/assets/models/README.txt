BirdNET model assets

Place these files in this directory:
1. birdnet.tflite
2. birdnet_labels.txt

Recommended model contract:
- input: FLOAT32 waveform
- sample rate: 48,000 Hz mono
- window length: 3 seconds (144,000 samples)
- output: per-species confidence probabilities

Expected by:
- com.example.projectbird.core.processor.BirdNetTfliteAudioProcessor

Notes:
- The app runs fully offline.
- Multi-label detection is enabled by confidence threshold + top-K pruning.
- Temporal smoothing uses weighted history plus hysteresis for stable live results.
- If model assets are missing or incompatible, the processor automatically falls back to MockAudioProcessor.
- birdnet_labels.txt must contain one species label per line and match output index order.
