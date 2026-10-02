# Linphone Android – e-WG200 Companion Application

## Overview

This repository contains a modified fork of the open-source **Linphone Android application**, originally developed by [Belledonne Communications](https://www.linphone.org/).

The fork has been adapted by **Magneta** to operate as a lightweight VoIP companion application for the e-WG200 device, alongside the proprietary LWP application.

Rather than providing a standalone softphone experience, this modified version of Linphone is designed to receive configuration and call-control instructions from an external controller application.

The source code is published to provide access to the modifications made to Linphone and to preserve the rights granted to users under the applicable GNU GPL and GNU Affero GPL licenses.

## Modifications to the Original Application

This fork significantly reduces the original Linphone Android application's user interface and standalone functionality.

The following modifications have been made:

- Removal of most of the original user interface.
- Removal of the contacts list and call history interfaces.
- Removal of notification functionality.
- Removal of account configuration through the user interface.
- Retention of a simplified call-answering screen.
- Implementation of an external configuration and call-control mechanism.
- Implementation of Android broadcast-based communication between the companion application and its controller.

The resulting application operates primarily as a call-handling component rather than a conventional standalone SIP client.

For the full functionality of the original Linphone application, please refer to the upstream project.

## Architecture and Integration

### Companion Application Model

This fork is intended to run on **e-WG200 devices** as a companion application to the proprietary LWP application.

The architecture consists of two Android applications:

**1. LWP – Controller Application**

The LWP application acts as the main controller, providing configuration information and issuing instructions to the modified Linphone application.

**2. Modified Linphone – Companion Application**

The modified Linphone application manages the VoIP functionality using the Linphone SDK and exposes a simplified call-answering interface.

It receives configuration data and operational commands from the controller application, including instructions to initiate and terminate calls.

### Inter-Application Communication

Communication between the controller and companion applications is implemented using Android broadcasts.

This mechanism enables bidirectional communication, including:

- Delivery of configuration information.
- Call initiation commands.
- Call termination commands.
- Exchange of operational status information.
- Transmission of call-related events and telemetry.

The modified Linphone application does not provide its original standalone account-management interface. Its intended operation relies on configuration supplied through the external integration mechanism.

### Independent Integration

Although this fork was developed for use alongside the LWP application on e-WG200 devices, its source code is available under the applicable open-source licenses.

Developers are permitted to study, modify, rebuild and redistribute the covered software in accordance with those licenses, including adapting it to work with alternative controller applications.

The software is not restricted to integration with LWP.

## Source Code and Licensing

### Original Project

This repository is derived from the Linphone Android application developed by Belledonne Communications.

Original project:

https://gitlab.linphone.org/BC/public/linphone-android

Linphone SDK:

https://gitlab.linphone.org/BC/public/linphone-sdk

Copyright © Belledonne Communications and other respective contributors.

Modifications to this fork: Copyright © Magneta and respective contributors.

The original copyright notices, third-party acknowledgements and applicable licensing conditions remain in effect.

### Applicable Licenses

The original Linphone Android application is distributed under the **GNU General Public License, version 3 (GPLv3)**.

The Linphone SDK and several of its constituent libraries, including Liblinphone, are distributed under the **GNU Affero General Public License, version 3 (AGPLv3)**, subject to the specific licensing terms of each component.

The Linphone project is also available under alternative proprietary licensing agreements offered by Belledonne Communications.

This fork is distributed under the applicable open-source licensing terms of its upstream components.

The inclusion of modified source code in this repository does not alter or remove any pre-existing copyright or licensing obligations.

Please refer to the license files included in the repository and its dependencies for the complete applicable terms.

The full license texts are also available at:

- GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
- AGPLv3: https://www.gnu.org/licenses/agpl-3.0.html

### Source Code Availability

The source code in this repository is made available to allow recipients of the modified application to exercise their rights under the applicable licenses.

Recipients may, subject to those license terms:

- Access and inspect the source code.
- Modify the application.
- Compile their own versions.
- Redistribute the original or modified versions.
- Adapt the application for other compatible environments or integrations.

The Corresponding Source for any distributed binary must include all material required by the applicable licenses, including relevant modifications, build configuration files, installation scripts and covered dependencies.

The source code corresponding to a distributed release should be identified by its matching Git tag or commit.

### Device Integration and Installation Information

This application was originally developed for deployment on e-WG200 devices.

Users or developers wishing to integrate their own software with this fork, build a modified version, or obtain information concerning installation on supported devices may contact Magneta through the following support portal:

**https://magneta.odoo.com/en/helpdesk/support-4**

Please create a support ticket describing your request, the version of the application concerned and the target device.

Where required by the applicable open-source licenses, relevant installation information for covered software distributed with the device must be made available to eligible recipients.

The support portal is provided as an additional communication channel and does not replace or restrict the rights granted directly by the software licenses.

## Building the Application

### Requirements

To build this application, you will need:

- Android Studio or a compatible Android development environment.
- A compatible Android SDK.
- Gradle (the project includes a Gradle wrapper).
- The Android NDK, when required by the build configuration.
- The relevant Linphone SDK dependencies.

### Building with Android Studio

1. Clone this repository.
2. Open the project in Android Studio.
3. Allow Gradle synchronization to complete.
4. Resolve or download the required Linphone SDK dependencies.
5. Select the desired build configuration.
6. Build and install the application.

By default, the Gradle configuration can retrieve the Linphone SDK as an AAR dependency from the configured Maven repository.

### Building from the Command Line

To generate a debug APK:

```bash
./gradlew assembleDebug
```

To install the debug APK on a connected Android device:

```bash
./gradlew installDebug
```

To generate a release APK:

```bash
./gradlew assembleRelease
```

Generated APK files can be found in the following directories:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

Depending on the build configuration, the Android NDK may be required.

If necessary, configure the `ANDROID_NDK_HOME` environment variable to point to your installed NDK.

### Building with a Local Linphone SDK

The application can also be configured to use a locally compiled version of the Linphone SDK.

Clone the upstream SDK repository, including its submodules:

```bash
git clone \
  https://gitlab.linphone.org/BC/public/linphone-sdk.git \
  --recursive
```

Follow the instructions provided in the Linphone SDK repository to build the required Android libraries.

Next, edit or create your Gradle user properties file:

```text
~/.gradle/gradle.properties
```

Add the absolute path to the SDK build directory:

```properties
LinphoneSdkBuildDir=/home/<username>/linphone-sdk/build/
```

Rebuild the Android application using Android Studio or Gradle.

Ensure that the version of the SDK used to compile the application is compatible with this fork.

## Documentation

For additional technical information about the underlying Linphone implementation, consult the upstream documentation:

- [Linphone official website](https://www.linphone.org/)
- [Linphone developer documentation](https://wiki.linphone.org/xwiki/wiki/public/)
- [Linphone Android source code](https://gitlab.linphone.org/BC/public/linphone-android)
- [Linphone SDK source code](https://gitlab.linphone.org/BC/public/linphone-sdk)
- [Android development tutorials](https://gitlab.linphone.org/BC/public/tutorials/-/tree/master/android/kotlin)

## Attribution

This project is based on Linphone, originally developed and maintained by Belledonne Communications and its contributors.

We acknowledge and retain the copyright and licensing information of the upstream project.

This modified fork is maintained by Magneta for its specific integration requirements and is not represented as an official release of Belledonne Communications.

Linphone and related names remain the property of their respective owners.

## Support and Contributions

For questions related to this fork, its integration, or its installation on e-WG200 devices, please use the following support portal:

https://magneta.odoo.com/en/helpdesk/support-4

For questions related to the original Linphone application or SDK, please refer to the upstream Belledonne Communications repositories and documentation.

Contributions and modifications to this fork must respect the licensing conditions applicable to the modified components.
