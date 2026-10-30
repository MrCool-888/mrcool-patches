# OpenBubbles bridge build

This isolated build owns the phone transport, protocol, Java patch helpers, and host-linkage audit.
It uses the tested Android Gradle Plugin 8.9.2 / Kotlin 2.1.20 toolchain with JDK 17, Android SDK 35,
build tools 35.0.0, and Python 3. The top-level patches template uses JDK 21 independently.

Supply your own native OpenBubbles 2.0.0 / 20002289 base APK. Verify its SHA-256 is
`a382a9cbef6a83288f11ed8c4763b56c5fedb24ee6505cd21b6bbb545cdc6cc0` before deriving the fixture.
Use dex-tools 2.4 `d2j-dex2jar` against that base APK to generate a class JAR, then
`d2j-class-version-switch 8 input.jar work/openbubbles-classes-v52.jar` to produce `work/openbubbles-classes-v52.jar`.
The verified local fixture SHA-256 is `c0301f11fa889e0b4568ae5d5909a9c71ab92bef5197e1317771778781d51d92`.
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
