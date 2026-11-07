# Waze Mod 10 consent-gated Binder contract (static reverse engineering)

| Request | Transaction | Reply |
|---|---:|---|
| get API version | 2 | Int |
| get snapshot | 3 | parcelled Bundle |
| register listener | 4 | binder callback |
| unregister listener | 5 | binder callback |

Intent component service: `com.waze/com.waze.gw.WazeModDataService`; binder descriptor `com.wazemod.api.IWazeModData`.
Consent screen: `com.waze/com.waze.gw.ApiConsentActivity`; action `com.wazemod.api.action.REQUEST_ACCESS`; must launch for activity result. Listener descriptor `com.wazemod.api.IWazeModListener`.

Snapshot keys used: `api`, `timestamp` (elapsed realtime), `speed`, `speed_limit`, `speed_min`, `overspeed`, `current_street`, `navigating`, `alerts` (Bundle array), `next_street`, `turn_distance_m`.

Per-alert: `type`, `distance_m`, `value`, `jam_severity`, `jam_delay_min`.

**Important:** This contract has been inferred from supplied APK bytecode; it has not been verified by on-device Binder integration tests. Snapshot does not contain coordinates for signs. Waze Mod's grant logic checks caller identity. No bypass is implemented.
