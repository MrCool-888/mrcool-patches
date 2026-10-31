# MrCool Patches

Private Morphe patches based on [Morphe's patches template](https://github.com/MorpheApp/morphe-patches-template).

<!-- PATCHES_START -->
| Patch | Target | Purpose |
| --- | --- | --- |
| Wear companion bridge | OpenBubbles 2.0.0 (20002289) | Embeds the phone bridge and patch settings for the Wear OS companion. |
| Fix Galaxy Buds3 Pro widget | Smart Launcher 6.6 build 020 / 024 | Fixes Samsung One UI 8.5 widget setup and reconfiguration. |
| Enable Pro | Smart Launcher 6.6 build 020 / 024 | Enables Pro features; adapted from hoo-dles/morphe-patches. |
<!-- PATCHES_END -->

The watch app is developed separately in [OB-wearos](https://github.com/MrCool-888/OB-wearos).
The OpenBubbles bridge is embedded in the patched phone app; no standalone phone companion is built.
Only Buds3 Pro has device confirmation. The launcher signature compatibility dependency is included.

## Build

Use JDK 21, Python 3, and Android SDK 35. Follow Morphe's development setup for GitHub Packages
credentials (`gpr.user` / `gpr.key`, or `GITHUB_ACTOR` / `GITHUB_TOKEN` with read:packages).
GitHub Actions uses the template's repository-scoped token. Keep personal credentials out of this repository.

```sh
./gradlew :patches:test :extensions:smartlauncher:widgetTest :patches:buildAndroid
```

The bundle is `patches/build/libs/patches-<version>.mpp`. Apply it with Morphe Desktop/Manager.
Because this repository is private, use an authenticated download and import the bundle locally;
an anonymous Manager source URL cannot read this repository.
The release includes the combined `.mpp`, a patched Smart Launcher 6.6 build 024 ARM64 APK,
the existing tested OpenBubbles phone/watch APKs, validation notes, and SHA-256 checksums.
Use the `.mpp` with your existing Manager signing key when updating a Manager-signed launcher;
the downloadable launcher APK has a separate release signing certificate and stock version code.
OpenBubbles and the watch must use the same application ID and signing certificate for Wear Data Layer.
Preserve the existing phone installation's signing key and APK format; do not uninstall or clear data.

## Rebuilding the OpenBubbles extension

The checked-in `openbubbles-wear.mpe` contains the bridge and its added dependencies; the payload audit
excludes stock app classes. A source digest and artifact checksum reject stale or modified binaries.
The original APK, derived host class fixture, signing material, and device data are not stored here.
Changing the bridge requires a local rebuild with JDK 17 and the compile-only stock-derived fixture at
`integrations/openbubbles/work/openbubbles-classes-v52.jar` (SHA-256
`c0301f11fa889e0b4568ae5d5909a9c71ab92bef5197e1317771778781d51d92`).
The fixture targets native OpenBubbles 2.0.0 / 20002289, base APK SHA-256
`a382a9cbef6a83288f11ed8c4763b56c5fedb24ee6505cd21b6bbb545cdc6cc0`.
It is a developer input and must remain local. See the integration README for extraction and checks.

```sh
cd integrations/openbubbles
./gradlew :protocol:test :bridge:testDebugUnitTest :extension:test :extension:buildExtension
python -m unittest discover -s scripts
python verify-extension.py --refresh
```

Commit the regenerated MPE and `extension-lock.json` together with the source changes.

## Development and releases

Work on `dev`; `feat:` and `fix:` commits use the template's semantic release workflow.
Merge `dev` into `main` without squashing for stable releases. Release metadata and the patch list are generated.
The ordinary bundle build requires no stock app files or other repository checkout.
The isolated `integrations/openbubbles` build keeps the tested bridge dependency/linkage audit.
Its `protocol` source is the same v4 wire contract as OB-wearos; synchronize both copies when changing it.

## Attribution and license

GPL-3.0; see LICENSE and NOTICE. Template: MorpheApp/morphe-patches-template.
The Smart Launcher Pro and signature compatibility patches are adapted from hoo-dles/morphe-patches
(copyright 2026 Hoo-dles), revision `1f9d0da03804edd5a2e8642de6fc964814de9dd4`.
The Buds3 widget fix was developed for this project. The two launcher features can be selected independently.
The watch application source belongs in OB-wearos, not this patch bundle.
