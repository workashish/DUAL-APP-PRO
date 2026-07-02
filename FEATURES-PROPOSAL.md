# DualSpace — Feature Proposal & Improvement Roadmap

## Current State Summary

| Feature | Status |
|---------|--------|
| App cloning via BlackBox | Working |
| Per-clone device identity | Working (SecureRandom + 15 device profiles) |
| Per-clone GMS | Auto-installs after each clone |
| Anti-detection (32 packages, proc maps) | Working |
| Log viewer with in-app logging | Working (LogManager self-initializes) |
| Home screen with app icons | Working (loads from PackageManager) |
| Long-press context menu | Working (bottom sheet) |
| Bubble quick-switch | Working |
| Clone batch operations | Working |

---

## TIER 1 — Core Experience (Must-Have)

### 1. App Data Backup & Restore

**Problem**: When user reinstalls the app, all clones and their data are lost.

**Solution**:
- Export clone data (identity + settings) as encrypted ZIP
- Import on same or different device
- Auto-backup to local storage on clone creation

**Files to modify**:
- New: `data/backup/CloneBackupManager.kt`
- New: `ui/screens/backup/BackupScreen.kt`
- Modify: `AppRepository.kt` — add export/import methods

**Complexity**: Medium (2-3 days)

---

### 2. Per-Clone Notification Forwarding

**Problem**: Cloned app notifications don't appear in host app notification tray.

**Solution**:
- Register `NotificationListenerService` for cloned apps
- Intercept notifications from BlackBox proxy processes
- Group by clone name in notification tray
- Tap notification → opens correct clone

**Files to modify**:
- Rewrite: `service/NotificationForwarder.kt` (currently a stub)
- New: `service/CloneNotificationListener.kt`
- New: AndroidManifest: `BIND_NOTIFICATION_LISTENER_SERVICE` permission

**Complexity**: High (4-5 days)

---

### 3. Process Monitoring & Crash Detection

**Problem**: `isRunning` status in DB is never updated back to false.

**Solution**:
- Background coroutine polls `BlackBoxCore.isRunningApplication()` every 5s
- Auto-detect crashes (running → not running transition)
- Show crash count per clone on home screen
- Offer "Relaunch" button after crash

**Files to modify**:
- New: `service/ProcessMonitor.kt`
- Modify: `HomeViewModel.kt` — observe process state
- Modify: `HomeScreen.kt` — show crash indicator

**Complexity**: Medium (2 days)

---

### 4. Identity Rotation Scheduler

**Problem**: Users must manually reset identity. No automation.

**Solution**:
- Per-clone rotation schedule (24h, 7d, 30d, manual)
- WorkManager background job for rotation
- Notification before rotation ("WhatsApp identity will change in 1h")
- History of all identities used

**Files to modify**:
- New: `service/IdentityRotationWorker.kt`
- New: `data/model/IdentityHistory.kt`
- Modify: `ClonedAppEntity.kt` — add rotation schedule fields
- Modify: `SettingsScreen.kt` — global rotation settings

**Complexity**: Medium (3 days)

---

## TIER 2 — Power Features (Should-Have)

### 5. Clone Templates

**Problem**: Users must configure each clone manually.

**Solution**:
- Save clone configuration as template (name, identity profile, GMS, settings)
- One-tap apply template to new clones
- Share templates between users (JSON export)
- Pre-built templates: "Work Device", "Gaming Device", "Privacy Device"

**Files to modify**:
- New: `data/model/CloneTemplate.kt`
- New: `data/local/dao/TemplateDao.kt`
- New: `ui/screens/templates/TemplateScreen.kt`
- Modify: `CloneScreen.kt` — template selector

**Complexity**: Medium (3 days)

---

### 6. App Isolation Levels

**Problem**: No granularity in isolation strength.

**Solution**:
```
Level 1 (Basic):    Separate data dir, same identity
Level 2 (Standard): Separate data + unique identity + GMS
Level 3 (Maximum):  Level 2 + root hiding + proc maps cleanup + VPN network
```

- User selects level per clone at creation time
- Higher levels = more resources but harder to detect
- Visual indicator on home screen showing level

**Files to modify**:
- Modify: `ClonedAppEntity.kt` — add `isolationLevel` field
- Modify: `BlackBoxEngine.kt` — apply settings based on level
- New: `ui/screens/isolation/IsolationSettings.kt`

**Complexity**: Medium (3 days)

---

### 7. Clipboard Isolation

**Problem**: Clipboard data leaks between clones and host.

**Solution**:
- Hook ClipboardManager in BlackBox
- Each clone gets isolated clipboard
- Optional: "Share clipboard" toggle per clone
- Clear clipboard on clone close

**Files to modify**:
- New: `service/ClipboardIsolation.kt`
- Modify: `BlackBoxEngine.kt` — register clipboard hook

**Complexity**: Low (1 day)

---

### 8. Per-Clone Network Configuration

**Problem**: All clones share same network. No way to isolate network per clone.

**Solution**:
- VPN-based network isolation per clone
- Proxy settings per clone (SOCKS5/HTTP)
- DNS override per clone
- Network speed limits per clone

**Files to modify**:
- New: `service/CloneVpnService.kt`
- New: `ui/screens/network/NetworkSettings.kt`
- Modify: `ClonedAppEntity.kt` — network config fields

**Complexity**: High (5-6 days)

---

### 9. Widget Support

**Problem**: Users must open app to launch clones.

**Solution**:
- Glance-based widget showing all clones
- Tap to launch clone directly from home screen
- Widget shows clone status (running/crashed)
- Customizable widget (size, theme, selected clones)

**Files to modify**:
- New: `widget/CloneWidget.kt`
- New: `widget/CloneWidgetReceiver.kt`
- New: `res/xml/widget_info.xml`
- Modify: `AndroidManifest.xml` — register widget

**Complexity**: Medium (3 days)

---

### 10. Dark/Light Theme Per Clone

**Problem**: No visual distinction between clones.

**Solution**:
- Each clone can have custom theme color
- App card on home screen shows clone color
- Notification channel inherits clone color
- Optional: dark mode override per clone

**Files to modify**:
- Already partially implemented (`customIconColor` field)
- Enhance: `HomeScreen.kt` — color-coded cards
- Enhance: `NotificationForwarder.kt` — colored notifications

**Complexity**: Low (1 day)

---

## TIER 3 — Advanced Features (Nice-to-Have)

### 11. App Usage Analytics Per Clone

**Problem**: No visibility into clone usage patterns.

**Solution**:
- Track launch count, session duration, crash count per clone
- Usage graph (daily/weekly/monthly)
- Most used clones ranking
- Storage usage breakdown per clone
- Export analytics as CSV/PDF

**Files to modify**:
- New: `data/model/UsageAnalytics.kt`
- New: `service/UsageTracker.kt`
- New: `ui/screens/analytics/AnalyticsScreen.kt`
- New: `ui/screens/analytics/AnalyticsViewModel.kt`

**Complexity**: Medium (3 days)

---

### 12. App Auto-Update Detection

**Problem**: When original app updates, clones still run old version.

**Solution**:
- Package broadcast receiver for `ACTION_PACKAGE_REPLACED`
- Compare versionCode of original vs clone
- "Update available" badge on home screen
- One-tap update all clones of an app

**Files to modify**:
- New: `service/AppUpdateReceiver.kt`
- Modify: `HomeViewModel.kt` — check versions
- Modify: `HomeScreen.kt` — update badge

**Complexity**: Low (1-2 days)

---

### 13. Shared Data Between Clones

**Problem**: Clones are fully isolated. No way to share data.

**Solution**:
- Controlled data sharing via shared content provider
- "Share clipboard" toggle per clone pair
- Shared photo/media folder per clone group
- Sync contacts between specific clones

**Files to modify**:
- New: `provider/SharedDataProvider.kt`
- New: `ui/screens/shared/SharedDataSettings.kt`
- Modify: `AndroidManifest.xml` — register provider

**Complexity**: High (4-5 days)

---

### 14. Root Detection Response System

**Problem**: Apps detect root and refuse to run.

**Solution**:
- Per-clone root hiding toggle (already in BlackBox via `isHideRoot`)
- Magisk hide integration
- SafetyNet/Play Integrity bypass per clone
- Root detection test screen (run detection, show results)

**Files to modify**:
- New: `ui/screens/rootcheck/RootCheckScreen.kt`
- Modify: `DualAppsApp.kt` — configure root hiding per clone
- New: `service/RootDetectionBypass.kt`

**Complexity**: Medium (3 days)

---

### 15. Batch Operations

**Problem**: Must manage clones one-by-one.

**Solution**:
- Multi-select clones on home screen
- Batch actions: launch all, stop all, delete all, reset identity
- Batch GMS install/uninstall
- Batch export/import

**Files to modify**:
- Modify: `HomeScreen.kt` — multi-select mode
- Modify: `HomeViewModel.kt` — batch operations
- Modify: `CloneViewModel.kt` — batch clone

**Complexity**: Low (2 days)

---

### 16. In-App Browser for Cloned Apps

**Problem**: No way to interact with cloned apps without launching them.

**Solution**:
- Embedded WebView that loads cloned app's web interface
- Quick access to web-based cloned apps (WhatsApp Web, etc.)
- No need to launch full app for simple tasks

**Files to modify**:
- New: `ui/screens/webapp/WebAppScreen.kt`
- New: `ui/screens/webapp/WebAppViewModel.kt`

**Complexity**: Medium (3 days)

---

### 17. Cloud Sync for Workspace Config

**Problem**: Clone configs lost on device change.

**Solution**:
- Firebase/Supabase backend for config sync
- Encrypted sync (end-to-end)
- Multi-device support
- Conflict resolution for same clone on multiple devices

**Files to modify**:
- New: `data/remote/SyncService.kt`
- New: `data/remote/SyncApi.kt`
- New: `ui/screens/sync/SyncSettings.kt`
- Modify: `build.gradle.kts` — add Firebase/Supabase

**Complexity**: High (7-10 days)

---

### 18. Voice Commands

**Problem**: No hands-free operation.

**Solution**:
- "Launch WhatsApp clone" voice command
- "Stop all clones" voice command
- "Switch to clone 2" voice command
- Integration with Google Assistant / Bixby

**Files to modify**:
- New: `service/VoiceCommandService.kt`
- New: `res/xml/voice_interaction_service.xml`
- Modify: `AndroidManifest.xml` — register voice service

**Complexity**: Medium (3-4 days)

---

### 19. Biometric Lock Per Clone

**Problem**: No per-clone security.

**Solution**:
- Fingerprint/PIN lock per clone
- Biometric prompt on clone launch
- Auto-lock after timeout
- Emergency wipe on failed attempts

**Files to modify**:
- New: `service/BiometricLockManager.kt`
- Modify: `ClonedAppEntity.kt` — add lock fields
- Modify: `BlackBoxEngine.kt` — intercept launch with biometric check

**Complexity**: Medium (2-3 days)

---

### 20. App Permission Manager

**Problem**: No granular permission control per clone.

**Solution**:
- View all permissions granted to each clone
- Revoke/grant permissions per clone
- Permission templates (privacy-first, full-access, etc.)
- Auto-revoke dangerous permissions on clone creation

**Files to modify**:
- New: `ui/screens/permissions/PermissionScreen.kt`
- New: `service/PermissionManager.kt`
- Modify: `BlackBoxEngine.kt` — permission interception

**Complexity**: Medium (3 days)

---

## TIER 4 — Enterprise/Pro Features (Revenue)

### 21. Pro Subscription Model

**Free tier**: 3 clones, basic identity, no GMS
**Pro tier**: Unlimited clones, GMS, identity rotation, backup/restore
**Enterprise**: Multi-device sync, admin panel, API access

**Implementation**:
- Google Play Billing Library integration
- Feature gating via BuildConfig flags
- License verification

**Complexity**: Medium (3-4 days)

---

### 22. Admin Dashboard (Enterprise)

**Problem**: No centralized management for organizations.

**Solution**:
- Web dashboard for managing clones across devices
- Policy enforcement (max clones, required isolation level)
- Usage analytics dashboard
- Remote wipe capability

**Complexity**: High (10+ days)

---

### 23. API for Automation

**Problem**: No programmatic access.

**Solution**:
- REST API for clone management
- Webhook notifications for clone events
- CLI tool for batch operations
- Integration with Tasker/Automate

**Files to create**:
- New: `service/api/CloneApiService.kt`
- New: `service/api/CloneApiRouter.kt`
- New: REST endpoints

**Complexity**: High (7-10 days)

---

## Priority Matrix

| Priority | Feature | Effort | Impact | Revenue |
|----------|---------|--------|--------|---------|
| P0 | Process Monitoring | 2d | High | - |
| P0 | Identity Rotation | 3d | High | - |
| P1 | Clone Templates | 3d | Medium | - |
| P1 | Widget Support | 3d | High | - |
| P1 | Notification Forwarding | 4d | High | - |
| P2 | App Backup/Restore | 2d | High | - |
| P2 | Batch Operations | 2d | Medium | - |
| P2 | App Update Detection | 1d | Medium | - |
| P2 | Isolation Levels | 3d | Medium | - |
| P3 | Biometric Lock | 2d | Medium | - |
| P3 | Usage Analytics | 3d | Low | - |
| P3 | Clipboard Isolation | 1d | Low | - |
| P4 | Pro Subscription | 3d | - | High |
| P4 | Cloud Sync | 7d | High | High |
| P4 | Admin Dashboard | 10d | - | Very High |
| P4 | API for Automation | 7d | Medium | High |

---

## Recommended Implementation Order

**Phase 1 (1 week)**: P0 + P1 features
- Process monitoring
- Identity rotation scheduler
- Widget support
- Clone templates

**Phase 2 (1 week)**: P2 features
- App backup/restore
- Batch operations
- App update detection
- Isolation levels

**Phase 3 (1 week)**: P3 features
- Biometric lock per clone
- Usage analytics
- Clipboard isolation
- Notification forwarding

**Phase 4 (2 weeks)**: P4 revenue features
- Pro subscription
- Cloud sync
- API for automation

**Total estimated effort**: 6 weeks for all features
