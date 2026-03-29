# 📄 🧠 PRODUCT REQUIREMENTS DOCUMENT (PRD)

## 🟢 Product Name (working)

**Project Bird** (you can rename later)

---

# 🎯 PRODUCT VISION

> A mobile-first environmental intelligence platform that captures real-world audio + location data, processes it locally, and visualizes biodiversity insights through interactive analytics.

---

# 🟩 PHASE 1: LOCAL INTELLIGENCE APP (HACKATHON SCOPE)

## 🎯 Goal

Build a **fully functional Android app** that:

* Captures **audio + GPS + timestamp**
* Processes data locally (mock or real model)
* Stores structured data
* Visualizes insights using **graphs + maps**

---

## 📦 Core Features

---

## 1. 🎤 Data Capture System

### Requirements:

* Start/Stop recording session
* Capture:

  * Audio (chunked: 1–3 seconds)
  * GPS coordinates
  * Timestamp

### Output:

Each capture creates a **CapturePoint**

---

## 2. 🧠 Processing Layer (Pluggable)

### Requirements:

* Accept:

  * Audio chunk
  * Location
  * Timestamp
* Return:

  * Detected entities (birds etc.)
  * Confidence scores
  * Intensity / environment

### Implementation:

* Mock OR integrate lightweight model via
  TensorFlow Lite

### Design Requirement:

* Must use **interface-based architecture**

```kotlin
interface AudioProcessor {
    fun process(audio: AudioChunk): DetectionResult
}
```

---

## 3. 💾 Local Storage (Room DB)

### Entities:

* Session
* CapturePoint
* AnalysisResult
* DetectedEntity

### Requirements:

* Store all captures locally
* Support querying for:

  * Time-based data
  * Location-based data
  * Detection frequency

---

## 4. 📊 Visualization Engine (CORE USP)

### 4.1 Detection Timeline

* X-axis: Time
* Y-axis: Detection count

---

### 4.2 Bird Distribution Chart

* Pie / bar chart
* Shows most detected entities

---

### 4.3 Activity Intensity Graph

* Line chart
* Shows sound/activity levels over time

---

### 4.4 🗺️ Map View

* Display capture points
* Each point = detection event

---

### 4.5 🔥 Heatmap (High Priority)

* Density of detections
* Weighted by:

  * Intensity OR
  * Number of detections

---

## 5. 📁 Session Management

### Requirements:

* Start new session
* End session
* View past sessions
* Tap session → detailed analytics

---

## 6. 🎨 UI/UX Requirements

### Screens:

#### 🏠 Home

* Start/Stop recording
* Live waveform
* Current detections

---

#### 📊 Analytics

* Charts (timeline, distribution, intensity)

---

#### 🗺️ Map

* Heatmap + markers

---

#### 📁 History

* List of sessions

---

### UX Goals:

* Real-time feel
* Smooth animations
* Clean, modern interface

---

## ⚙️ Technical Stack

### Mobile:

* Kotlin + Jetpack Compose

### Storage:

* Room DB

### Maps:

* Google Maps SDK

### Charts:

* MPAndroidChart / Compose charts

### ML (optional):

* TensorFlow Lite

---

## ⚡ Performance Requirements

* Audio processing latency < 2–3 seconds
* Smooth UI (no frame drops)
* Efficient local storage

---

## ❌ Out of Scope (Phase 1)

* User accounts
* Cloud sync
* Real-time multi-user data
* Advanced ML training
* Perfect detection accuracy

---

# 🟡 PHASE 2: PUBLIC DATA + DISCOVERY PLATFORM

## 🎯 Goal

Transform app into a **crowdsourced biodiversity network**

---

## 🌐 New Capabilities

---

## 1. ☁️ Data Sync

### Requirements:

* User can mark session/data as:

  * Private
  * Public

* Upload:

  * CapturePoint
  * Analysis results

---

## 2. 🔍 Search & Discovery

### Features:

* Search by:

  * Animal/bird name
* Show:

  * Locations where detected
  * Frequency
  * Time patterns

---

## 3. 🗺️ Global Map

* Aggregated heatmaps
* Filter by:

  * Species
  * Time range

---

## 4. 👥 Community Layer (Optional Extension)

* Contributions leaderboard
* Verified sightings
* Comments / notes

---

# 🧱 Phase 2 Architecture Considerations (IMPORTANT)

Design Phase 1 with this in mind:

---

## ✅ 1. Use UUIDs everywhere

* Needed for syncing later

---

## ✅ 2. Keep schema normalized

* Avoid JSON blobs if possible

---

## ✅ 3. Add “isSynced” flag

```kotlin
val isSynced: Boolean
```

---

## ✅ 4. Add “visibility” field

```kotlin
val visibility: String // PRIVATE / PUBLIC
```

---

## ✅ 5. Decouple processing

So later:

* Local ML OR
* Cloud processing

---

# 🏆 Success Criteria

## Phase 1 Success:

* Real-time capture works
* Data stored locally
* At least 3 meaningful visualizations
* Smooth demo experience

---

## Phase 2 Success:

* Users can explore biodiversity data globally
* Search-based discovery works
* Data aggregation is meaningful
