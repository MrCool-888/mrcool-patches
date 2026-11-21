# OpenBubbles bridge build

This isolated build owns the phone transport, protocol, Java patch helpers, and host-linkage audit.
It uses the tested Android Gradle Plugin 8.9.2 / Kotlin 2.1.20 toolchain with JDK 17, Android SDK 35,
build tools 35.0.0, and Python 3. The top-level patches template uses JDK 21 independently.

Use an OpenBubbles **2.0.0, version code 20002296** base APK to derive the fixture. The verified
ARM64 base APK SHA-256 is `cad2a3fc0320402883024841194d238e3ccfb6b8e6a82dfc61c927e7ac0fc4aa`.
The APKPure ARMv7 base APK (SHA-256 `3ffa2f522455f26b44152d6c4bdc93a35b912d586c6d8416ac71045a70750ff9`)
contains the same DEX files.
Use dex-tools 2.4 `d2j-dex2jar` against that base APK to generate a class JAR, then
`d2j-class-version-switch 8 input.jar work/openbubbles-classes-v52.jar` to produce `work/openbubbles-classes-v52.jar`.
Archive byte hashes can differ if a converter embeds different ZIP timestamps; validate the original APK
and use the same converter/options when reproducing the recorded fixture.
These files are compile-only, ignored developer inputs. Do not commit or upload them with this project.

Run the tests and build commands in the repository README. `prepare-embedded.py` chooses only missing
dependency classes and rejects host/platform class overlap, unresolved members, conflicting definitions,
and unsupported resource integration. The stock classes are never merged into the MPE.
The template build uses the checked-in MPE and checks its source digest and checksum.
After any bridge/protocol/build-source change, rebuild and run `python verify-extension.py --refresh`.
The full Android helper tests require the local host fixture; ordinary CI runs the patch-structure tests,
earbuds harness, source-lock check, and Python audit tests without that fixture.

The `protocol` module is the same v4 contract as OB-wearos. Synchronize both copies on wire changes.
