# Project Bird — Phase 1 Implementation Checklist

## Locked Decisions

- Build from scratch
- Mock processor for Phase 1
- Offline-only for Phase 1
- Analytics across all sessions
- Heatmap toggle: `Detections` / `Intensity`
- Recording must continue when the app goes to background or is swiped from recents
- Store raw audio chunks only if they are meaningful
- Meaningful = detection above threshold **or** high intensity
- UI should be clean, minimal, and elegant
- Recommended `minSdk` = `26`

---

## Recommended Project Structure

### Top-level package layout

- `com.projectbird.app`
- `com.projectbird.core`
- `com.projectbird.data`
- `com.projectbird.feature.home`
- `com.projectbird.feature.analytics`
- `com.projectbird.feature.map`
- `com.projectbird.feature.history`
- `com.projectbird.feature.sessiondetail`

### Detailed package structure

#### `app`
Contains:
- `ProjectBirdApp`
- `MainActivity`
- `navigation/`
- `theme/`

Suggested contents:
- `navigation/AppDestination`
- `navigation/AppNavHost`
- `navigation/BottomNavItem`
- `theme/Color.kt`
- `theme/Theme.kt`
- `theme/Type.kt`

#### `core.audio`
Contains recording and amplitude helpers.

Suggested classes:
- `AudioRecorder`
- `AudioRecorderImpl`
- `AudioChunkWriter`
- `WaveformSampler`
- `AudioFormatConfig`

Responsibilities:
- record 2-second temp chunks
- compute amplitude/RMS/intensity
- manage recorder lifecycle safely

#### `core.location`
Suggested classes:
- `LocationProvider`
- `LocationProviderImpl`
- `LocationSnapshot`

Responsibilities:
- get latest known location
- subscribe to updates while recording
- expose graceful fallback if unavailable

#### `core.processor`
Suggested classes:
- `AudioProcessor`
- `MockAudioProcessor`
- `DetectionResult`
- `DetectedItem`
- `MeaningfulnessEvaluator`

Responsibilities:
- process audio chunk + timestamp + location
- return detections, confidence, intensity, environment label
- decide keep/delete

#### `core.service`
Suggested classes:
- `RecordingForegroundService`
- `RecordingServiceController`
- `RecordingNotificationFactory`
- `RecordingCoordinator`

Responsibilities:
- own background recording lifecycle
- persist captures
- keep notification updated
- survive UI closure

#### `core.storage`
Suggested classes:
- `SessionFileManager`
- `TempFileManager`

Responsibilities:
- session folders
- temp folder
- move temp chunk to permanent chunk
- delete discarded chunks
- delete all files for a session

#### `core.common`
Suggested utilities:
- `UuidProvider`
- `TimeProvider`
- `DispatchersProvider`
- `DateTimeFormatter`
- `ResultState`

#### `data.local`
Contains Room database pieces.

Suggested subpackages:
- `entity/`
- `dao/`
- `relation/`
- `converter/`
- `db/`

#### `data.repository`
Suggested repositories:
- `SessionRepository`
- `CaptureRepository`
- `AnalyticsRepository`
- `RecordingRepository`

#### `feature.home`
Suggested files:
- `HomeScreen`
- `HomeViewModel`
- `HomeUiState`
- `components/RecordButton`
- `components/WaveformCard`
- `components/LiveDetectionCard`
- `components/RecordingStatusCard`

#### `feature.analytics`
Suggested files:
- `AnalyticsScreen`
- `AnalyticsViewModel`
- `AnalyticsUiState`
- `components/SummaryStatsRow`
- `components/DetectionTimelineChart`
- `components/BirdDistributionChart`
- `components/IntensityChart`

#### `feature.map`
Suggested files:
- `MapScreen`
- `MapViewModel`
- `MapUiState`
- `components/HeatmapToggle`
- `components/MapLegend`

#### `feature.history`
Suggested files:
- `HistoryScreen`
- `HistoryViewModel`
- `HistoryUiState`
- `components/SessionCard`

#### `feature.sessiondetail`
Suggested files:
- `SessionDetailScreen`
- `SessionDetailViewModel`
- `SessionDetailUiState`
- `components/SessionSummaryCard`

---

## Database Design

### Entities

#### `SessionEntity`
Fields:
- `id`
- `name`
- `startTime`
- `endTime`
- `isActive`
- `createdAt`
- `visibility`
- `isSynced`

#### `CapturePointEntity`
Fields:
- `id`
- `sessionId`
- `timestamp`
- `latitude`
- `longitude`
- `audioFilePath`
- `durationMs`
- `intensity`
- `environmentLabel`
- `retentionReason`
- `visibility`
- `isSynced`

#### `AnalysisResultEntity`
Fields:
- `id`
- `capturePointId`
- `processedAt`
- `processorType`
- `processorVersion`
- `visibility`
- `isSynced`

#### `DetectedEntityEntity`
Fields:
- `id`
- `analysisResultId`
- `entityName`
- `confidence`
- `visibility`
- `isSynced`

### DAOs

#### `SessionDao`
- create session
- end session
- mark active/inactive
- get active session
- get all sessions ordered by newest
- get session by id
- delete session

#### `CapturePointDao`
- insert capture point
- get captures by session
- get captures across all sessions
- count captures per session
- fetch map points
- fetch intensity over time
- delete captures by session

#### `AnalysisResultDao`
- insert analysis result
- get analysis by capture id
- delete by session through repository coordination

#### `DetectedEntityDao`
- insert list of detections
- count species frequency
- top species across all sessions
- top species by session
- detections over time buckets
- delete by session relation/repository

### Room relations
- `SessionWithCapturePoints`
- `CapturePointWithAnalysisAndDetections`

---

## Dependency Checklist

### Required
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- Kotlin coroutines
- Google Maps Compose
- Google Maps utility library / heatmap support
- location services
- lifecycle ViewModel + compose integration

### Likely useful
- permissions helper library if needed
- logging utility
- Compose-friendly chart library

### Avoid for now
- heavy DI setup unless already fast with it
- background job scheduler for the recording loop
- TFLite integration in early Phase 1

---

## Full Implementation Checklist

## Phase A — Project Setup

### App foundation
- [ ] Create Android project with Compose
- [ ] Set `minSdk = 26`
- [ ] Set target and compile SDK to latest stable
- [ ] Configure package name
- [ ] Add Material 3 theme
- [ ] Set up bottom navigation
- [ ] Create placeholder screens:
  - [ ] Home
  - [ ] Analytics
  - [ ] Map
  - [ ] History
- [ ] Add `Session Detail` route

### Permissions and manifest
- [ ] Add microphone permission
- [ ] Add fine location permission
- [ ] Add notification permission if required
- [ ] Add foreground service declarations
- [ ] Add service entry in manifest
- [ ] Add Maps API key config

### Build sanity
- [ ] App launches cleanly
- [ ] Navigation works
- [ ] Theme looks consistent
- [ ] Maps key is recognized

---

## Phase B — Core Data Layer

### Room setup
- [ ] Create `AppDatabase`
- [ ] Add entities:
  - [ ] `SessionEntity`
  - [ ] `CapturePointEntity`
  - [ ] `AnalysisResultEntity`
  - [ ] `DetectedEntityEntity`
- [ ] Add type converters for UUID/date-time
- [ ] Create DAOs
- [ ] Add repository classes
- [ ] Add DB provider / singleton setup

### Repository responsibilities
- [ ] Session creation/finalization
- [ ] Capture insert/delete
- [ ] Analysis insert
- [ ] Detection insert/query
- [ ] Aggregation queries for analytics
- [ ] Full session delete with audio cleanup

---

## Phase C — File Storage Layer

### Session file system plan
- [ ] Create app recordings root directory
- [ ] Create temp chunk directory
- [ ] Create per-session directory structure
- [ ] Build file naming strategy using:
  - session id
  - timestamp
  - unique chunk id

### File manager behavior
- [ ] Write temp chunk file
- [ ] Move kept chunk to session folder
- [ ] Delete discarded temp chunk
- [ ] Delete all files for session
- [ ] Verify file exists before DB insert
- [ ] Gracefully handle storage errors

---

## Phase D — Foreground Recording Service

### Service implementation
- [ ] Create `RecordingForegroundService`
- [ ] Add persistent notification
- [ ] Add notification action to stop recording
- [ ] Ensure service starts via explicit action
- [ ] Ensure service stops cleanly
- [ ] Use sticky service behavior as appropriate
- [ ] Expose active recording state for UI

### Service controller
- [ ] Add helper to start service
- [ ] Add helper to stop service
- [ ] Add helper to query active state

### Notification behavior
- [ ] Show recording status
- [ ] Show session elapsed time if feasible
- [ ] Provide stop action
- [ ] Ensure notification updates while recording

### Lifecycle validation
- [ ] Recording continues when app goes background
- [ ] Recording continues when screen turns off
- [ ] Recording continues when app is swiped from recents
- [ ] Reopening app reconnects correctly

---

## Phase E — Audio Capture Pipeline

### Recorder
- [ ] Create `AudioRecorder` interface
- [ ] Build `AudioRecorderImpl`
- [ ] Support fixed 2-second chunk recording
- [ ] Return temp file path after chunk capture
- [ ] Compute or expose amplitude data
- [ ] Ensure recorder resources are always released

### Audio config
- [ ] Pick stable mono format
- [ ] Keep file size reasonable
- [ ] Validate quality is enough for mock processing
- [ ] Validate chunk generation is consistent over long sessions

### Reliability
- [ ] Handle recorder start failure
- [ ] Handle recorder interruption
- [ ] Handle no microphone permission
- [ ] Prevent duplicate start calls
- [ ] Prevent stop when already stopped

---

## Phase F — Location Integration

### Provider
- [ ] Create `LocationProvider` interface
- [ ] Build provider using fused location APIs
- [ ] Fetch latest known location
- [ ] Start updates when recording begins
- [ ] Stop updates when recording ends

### Fallback behavior
- [ ] If no live fix exists, use latest known
- [ ] If still unavailable, allow null or fallback location policy
- [ ] Show user when location is unavailable

### UI behavior
- [ ] Show current location status on Home
- [ ] Handle permission denial gracefully

---

## Phase G — Mock Processing Layer

### Models
- [ ] Define `DetectionResult`
- [ ] Define `DetectedItem`
- [ ] Define environment labels
- [ ] Define retention reason enum:
  - [ ] `DETECTION`
  - [ ] `INTENSITY`
  - [ ] `BOTH`

### Processor
- [ ] Create `AudioProcessor` interface
- [ ] Implement `MockAudioProcessor`
- [ ] Use intensity as real input
- [ ] Generate believable species labels
- [ ] Generate confidence values
- [ ] Generate environment label

### Meaningfulness evaluator
- [ ] Detection threshold rule
- [ ] Intensity threshold rule
- [ ] Keep chunk if either rule passes
- [ ] Assign retention reason
- [ ] Return keep/delete decision clearly

---

## Phase H — Recording Coordinator

### Coordinator responsibilities
- [ ] Start session if not active
- [ ] Loop every 2 seconds
- [ ] Record temp chunk
- [ ] Read/compute intensity
- [ ] Fetch latest location
- [ ] Call processor
- [ ] Evaluate meaningfulness
- [ ] Move/delete file
- [ ] Insert Room rows if kept
- [ ] Update current state for UI
- [ ] Finalize session on stop

### Failure handling
- [ ] If processing fails, delete temp chunk safely
- [ ] If DB insert fails, avoid orphaning kept files
- [ ] If file move fails, skip DB insert
- [ ] Log all major failures

---

## Phase I — Home Screen

### Home screen features
- [ ] Large record / stop button
- [ ] Recording status label
- [ ] Session elapsed timer
- [ ] Subtle waveform / amplitude visualization
- [ ] Current detections card
- [ ] Current environment label
- [ ] Location status chip
- [ ] If app reopens while recording, show active state correctly

### UI polish
- [ ] Clean spacing
- [ ] Minimal text
- [ ] Prominent primary action
- [ ] Elegant color palette
- [ ] Smooth state transitions
- [ ] No clutter

### States
- [ ] Idle
- [ ] Recording
- [ ] Permission needed
- [ ] Error
- [ ] No location available

---

## Phase J — Analytics Screen

### Summary cards
- [ ] Total sessions
- [ ] Total meaningful captures
- [ ] Top detected species
- [ ] Average intensity

### Detection timeline
- [ ] Query detections over time
- [ ] Pick sensible bucket size
- [ ] Render line or bar chart
- [ ] Handle empty state

### Bird distribution chart
- [ ] Query top entities
- [ ] Render bar chart
- [ ] Show counts cleanly
- [ ] Handle low-data state

### Intensity chart
- [ ] Query intensity over time
- [ ] Aggregate by time bucket
- [ ] Render line chart
- [ ] Handle sparse data

### Analytics UX
- [ ] Global overview header
- [ ] Consistent chart styling
- [ ] Minimal legends
- [ ] Clear empty-state copy

---

## Phase K — Map Screen

### Map basics
- [ ] Integrate Google Maps Compose
- [ ] Show current meaningful capture points
- [ ] Center map sensibly
- [ ] Add marker click behavior

### Marker mode
- [ ] Marker per kept capture point
- [ ] Show timestamp
- [ ] Show intensity
- [ ] Show top detection

### Heatmap mode
- [ ] Build heatmap dataset from meaningful captures
- [ ] Add toggle:
  - [ ] Weighted by detections
  - [ ] Weighted by intensity
- [ ] Update heatmap when toggle changes
- [ ] Validate rendering performance

### Map UX
- [ ] Top segmented control for weight mode
- [ ] Optional markers/heatmap toggle if desired
- [ ] Clean legend or label
- [ ] Good empty state when no map data exists

---

## Phase L — History Screen

### Session list
- [ ] Query sessions newest first
- [ ] Show date/time
- [ ] Show duration
- [ ] Show meaningful capture count
- [ ] Show top species preview if available
- [ ] Navigate to session detail

### Session cards
- [ ] Keep design clean
- [ ] Use concise metadata
- [ ] Avoid overloading with stats

### Empty state
- [ ] Helpful first-run message
- [ ] CTA to start recording

---

## Phase M — Session Detail Screen

### Session summary
- [ ] Session duration
- [ ] Meaningful capture count
- [ ] Average intensity
- [ ] Top detected species

### Session-specific insights
- [ ] Mini timeline
- [ ] Mini intensity view
- [ ] Session capture list if time permits

### Actions
- [ ] Delete session
- [ ] Optional jump to map focus

---

## Phase N — Delete and Cleanup Logic

### Delete session flow
- [ ] Confirm delete
- [ ] Delete DB records
- [ ] Delete session audio directory
- [ ] Return success state to UI
- [ ] Handle partial cleanup failures safely

### Chunk retention rules
- [ ] Delete non-meaningful temp file immediately
- [ ] Never insert DB row for deleted chunk
- [ ] If chunk is kept, ensure moved path is final path stored in DB

---

## Phase O — App State Recovery

### Reopen-app behavior
- [ ] If service is still recording, Home screen reflects it
- [ ] Elapsed time resumes correctly
- [ ] Current session is loaded from DB/service state
- [ ] Latest detections are shown

### Cold start while recording
- [ ] App should detect active session if service is alive
- [ ] UI should not show idle incorrectly

---

## Phase P — Testing and QA Checklist

### Permission flows
- [ ] First-time mic permission
- [ ] First-time location permission
- [ ] Notification permission if applicable
- [ ] Deny mic
- [ ] Deny location
- [ ] Deny notification
- [ ] Retry from app settings path

### Recording lifecycle
- [ ] Start/stop works repeatedly
- [ ] Background recording works
- [ ] Swipe-from-recents works
- [ ] Screen-off works
- [ ] Notification stop action works
- [ ] Reopen-app state sync works

### Data integrity
- [ ] Kept chunk has file + DB row
- [ ] Deleted chunk leaves no DB row
- [ ] Delete session removes all audio files
- [ ] Analytics update after session deletion

### Long session
- [ ] 20-minute dev validation
- [ ] 1-hour realistic validation before demo if possible
- [ ] No memory leak symptoms
- [ ] No runaway temp files

### UI polish
- [ ] Consistent spacing
- [ ] No overlapping text
- [ ] Smooth scroll
- [ ] Charts render correctly on small screens
- [ ] Map controls do not crowd the screen

---

## Screen-by-Screen Build Order

1. Home
2. History
3. Analytics
4. Map
5. Session Detail

---

## Recommended Reusable UI Components

- `PrimaryRecordButton`
- `StatCard`
- `SectionHeader`
- `EmptyStateCard`
- `PermissionInfoCard`
- `WaveformView`
- `DetectionChip`
- `SessionCard`
- `TogglePillGroup`

---

## Suggested Milestone Timeline

### Milestone 1
- app shell
- theme
- navigation
- permissions
- Room skeleton

### Milestone 2
- foreground service
- session start/stop
- notification action

### Milestone 3
- 2-second audio chunking
- temp file handling
- location integration

### Milestone 4
- mock processor
- meaningfulness logic
- DB persistence for kept chunks

### Milestone 5
- Home screen real-time UI
- reconnect on reopen

### Milestone 6
- History
- session deletion
- file cleanup

### Milestone 7
- Analytics charts

### Milestone 8
- Map markers
- heatmap
- weight toggle

### Milestone 9
- polish
- long-run testing
- demo readiness

---

## Critical Path

If time gets tight, finish these first:

1. foreground service
2. audio chunking
3. meaningful chunk retention
4. Room persistence
5. Home screen
6. History
7. one or two analytics charts
8. map markers

Heatmap should come immediately after markers if possible.

---

## Explicitly Out of Scope for Phase 1

- cloud sync
- authentication
- real ML integration
- species search
- playback browser for all chunks
- advanced filtering UI
- export/share workflows
- reboot restore
- force-stop survival

---

## Final Recommended Development Order

1. Project setup
2. Theme + navigation
3. Room entities and DAOs
4. Foreground recording service
5. Session start/stop logic
6. Temp audio chunk capture
7. Location provider
8. Mock processor
9. Meaningful keep/delete logic
10. DB inserts for kept chunks
11. Home screen live state
12. History screen
13. Session delete + file cleanup
14. Analytics queries and charts
15. Map markers
16. Heatmap + toggle
17. Session detail
18. Polish and stress testing

---

## Immediate Next Step

Start with:
- project setup
- navigation
- Room entities
- foreground service skeleton

That gets the riskiest engineering piece under control first.