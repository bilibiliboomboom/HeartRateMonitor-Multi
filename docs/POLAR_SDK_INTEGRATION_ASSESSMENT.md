# Polar BLE SDK integration assessment

## Decision

Use Polar's official BLE SDK as a dedicated Polar-device transport, but do not replace the existing generic BLE Heart Rate Service transport. Keep the UI, athlete roster, training session model, charts and export owned by this app.

Official upstream: https://github.com/polarofficial/polar-ble-sdk

## Verified upstream facts (checked 2026-11-20)

- The upstream repository contains Android/iOS SDK source and full example applications.
- Supported product list includes Verity Sense, OH1, H9 and H10.
- The Android quickstart currently instructs applications to set minSdk to 33 or higher and add the SDK through JitPack.
- The current upstream release listing exposes 8.4.0; pin an explicit version rather than using a floating dependency.
- The Polar SDK is governed by Polar's custom SDK Limited License Agreement, not a standard permissive OSS license. The agreement permits development and commercial use subject to its terms, copyright/license notices, and restrictions. Do not remove the notice or represent the app as a Polar product.
- The SDK exposes device discovery/connection callbacks and per-device HR streaming APIs. Multi-device concurrency and connection stability must be proven with several physical sensors before this app relies on it.

## Compatibility finding

Current app baseline is minSdk 26 (Android 8). Adding the current Polar SDK to the only app variant may cause a manifest/minSdk conflict because upstream requires API 33. Do not silently raise the whole app's minimum Android version.

Preferred implementation path:
1. Keep the generic BLE build variant at minSdk 26.
2. Add a separate Polar-enabled product flavor/build variant with minSdk 33 and Polar SDK dependency scoped only to that variant.
3. Share the same athlete/session/UI domain model between variants.
4. If Gradle/manifest merging or SDK dependency constraints make flavors impractical, build a small isolated Polar proof-of-concept app first; do not break the working generic build.

## Implementation gates

1. Inspect upstream Android example and SDK version-specific Gradle/JDK requirements.
2. Confirm the API supports multiple simultaneous Polar device connections; test at least 2, then 5+ sensors.
3. Implement a Polar adapter that emits normalized heart-rate, battery (when available), connection and signal information into the existing app model.
4. Keep generic BLE HR support as a separate adapter.
5. Do not infer swim distance, laps or pace from heart rate. Those require a separate trustworthy lap/distance data source.
6. Test poolside range, reconnection, stale-data timeout and tablet BLE concurrency with the actual sensors.

## Licensing

Before distributing a build containing the Polar SDK, include the unmodified Polar license notice and review the complete current agreement and third-party software listing. The SDK license is not permission to copy Polar Team's proprietary app code, UI assets, logos or branding.
