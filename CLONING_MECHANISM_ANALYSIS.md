# Cloning & Launching Mechanism Analysis

## Three Approaches Compared

### 1. MultiApp (WaxMoon) — Native Sandbox
**How it works:**
- Abandoned Java dynamic proxy entirely
- Binder components (service/receiver/provider) maintained by engine itself
- Activity proxy uses "more reliable solution" (not standard proxy forwarding)
- Native hooks via **seccomp/bpf** (not Dobby) for system call interception
- Closer to true sandbox concept

**Why it works on OPPO:**
- seccomp/bpf operates at kernel level — OEM framework can't block it
- No dependency on Android's ActivityManager routing
- Binder calls go through engine's own implementation, not system hooks

**Status:** Commercial (AGPL-3.0), GitHub no longer maintained, core engine proprietary

### 2. Shelter — Android Work Profile
**How it works:**
- Creates a **managed work profile** via `DevicePolicyManager`
- Apps install natively in the work profile (separate UID, separate data)
- No hooking, no proxy, no native code — pure Android API
- Apps run as if on a different user on the same device

**Why it works on OPPO:**
- Work Profile is an official Android feature supported by all OEMs
- No framework interception needed
- System-level isolation, not virtual

**Limitations:**
- Work profile badge visible on app icons
- No device identity spoofing (same device, just different user UID)
- Requires device admin privileges
- Limited to what Work Profile APIs allow

**Status:** GPL-3.0, actively maintained, 3.5k stars

### 3. NewBlackbox (ALEX5402) — VirtualApp Proxy
**How it works:**
- Java dynamic proxy for Activity routing via ProxyActivity$P0-P49
- Native hooks (Dobby) for system service interception
- `:black` server process manages virtual environment
- `:p0` proxy process hosts cloned app activities

**Why it fails on OPPO:**
- `shouldUseFallbackMode()` activates after 2 Binder failures
- Fallback uses HOST PackageManager (not virtual) → broken intent
- `PackageItemInfo.metaData` null → proxy process crashes
- OPPO's ColorOS kills `:black` server process → triggers fallback
- Cross-user activity starts blocked by OPPO framework

**Status:** Apache-2.0, fork of VirtualApp, we have source access

## Key Architectural Difference

```
MultiApp:   App → Engine's own Binder → Native seccomp/bpf → System
Shelter:    App → Android WorkProfile API → System (no interception)
NewBlackbox: App → Java Proxy → Dobby hooks → System (blocked on OPPO)
```

## Recommendation

The most reliable path forward is a **hybrid approach**:

1. **Keep BlackBox for identity spoofing** (the parts that work: install, GMS, device identity)
2. **Use Work Profile as the launch mechanism** (like Shelter) for apps that need to actually open
3. **Fallback to real app** only when Work Profile isn't available

This gives us:
- ✅ Real app isolation (Work Profile)
- ✅ Device identity spoofing (BlackBox)
- ✅ GMS per workspace (BlackBox)
- ✅ Works on OPPO (Work Profile is official Android API)
- ⚠️ Work profile badge visible (minor cosmetic issue)

The alternative is to fix BlackBox's proxy system by porting MultiApp's seccomp/bpf approach, but that's a massive native code undertaking.
