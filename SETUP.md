### Setup Information

These instructions will help you to set up your development environment, get the source code of the OpenCloud for Android app and build it by yourself. If you want to help developing the app take a look to the [contribution guidelines][0].

Sections 1) and 2) are common for any environment. The rest of the sections describe how to set up a project in different tool environments. Nowadays we recommend to use Android Studio (section 2), but you can also build the app from the command line (section 3).

If you have any problem, remove the 'android' folder, start again from 1) and work your way down. If something still does not work as described here, please open a new issue describing exactly what you did, what happened, and what should have happened.


### 0. Common software dependencies.

There are some tools needed, no matter what is your specific IDE or build tool of preference.

[git][1] is used to access to the different versions of the OpenCloud's source code. Download and install the version appropriate for your operating system from [here][2]. Add the full path to the 'bin/' directory from your git installation into the PATH variable of your environment so that it can be used from any location.

The [Android SDK][3] is necessary to build the app. There are different options to install it in your system, depending of the IDE you decide to use. Check Google documentation about [installation][4] for more details on these options. After installing it, add the full path to the directories 'tools/' and 'platform-tools/' from your Android SDK installation into the PATH variable of your environment.

Open a terminal and type 'android' to start the Android SDK Manager. To build the OpenCloud for Android app you will need to install at least the next SDK packages:

* Android SDK Tools and Android SDK Platform-tools (already installed); upgrade to their last versions is usually a good idea.
* No longer need to specify a version for the build tools, Gradle plugin uses the minimum required version by default.
* Android 12.0 (API 31), SDK Platform; needed to build the OpenCloud app.

Install any other package you consider interesting, such as emulators.

For other software dependencies check the details in the section corresponding to your preferred IDE or build system.


### 1. Fork and download the opencloud-eu/android repository.

You will need [git][1] to access to the different versions of the OpenCloud's source code. The source code is hosted in Github and may be read by anybody without needing a Github account. You will need a Github account if you want to contribute to the development of the app with your own code.

Next steps will assume you have a Github account and that you will get the code from your own fork. 

* In a web browser, go to https://github.com/opencloud-eu/android, and click the 'Fork' button near the top right corner.
* Open a terminal and go on with the next steps in it.
* Clone your forked repository: ```git clone https://github.com/YOURGITHUBNAME/android.git```.
* Move to the project folder with ```cd android```.
* Fetch and apply any changes from your remote branch 'main': ```git fetch``` + ```git rebase```
* Make official OpenCloud repo known as upstream: ```git remote add upstream https://github.com/opencloud-eu/android.git```
* Make sure to get and apply the latest changes from official android/main branch: ```git fetch upstream``` + ```git rebase upstream/main```

At this point you can continue using different tools to build the project. Section 2 and 3 describe the existing alternatives.


### 2. Working with Android Studio.

[Android Studio][5] is currently the official Android IDE. Due to this, we recommend it as the IDE to use in your development environment.

We recommend to use the last version available in the stable channel of Android Studio updates. See what update channel is your Android Studio checking for updates in the menu path 'Help'/'Check for Update...'/link 'Updates' in the dialog.

To set up the project in Android Studio follow the next steps:

* Open Android Studio and select 'Import Project (Eclipse ADT, Gradle, etc)'. Browse through your file system to the folder 'android' where the project is located. Android Studio will then create the '.iml' files it needs. If you ever close the project but the files are still there, you just select 'Open Project...'. The file chooser will show an Android face as the folder icon, which you can select to reopen the project.
* Android Studio will try to build the project directly after importing it. To build it manually, follow the menu path 'Build'/'Make Project', or just click the 'Play' button in the tool bar to build and run it in a mobile device or an emulator. The resulting APK file will be saved in the 'build/outputs/apk/' subdirectory in the project folder.


### 3. Working in a terminal with Gradle:

[Gradle][6] is the build system used by Android Studio to manage the building operations on Android apps. You do not need to install Gradle in your system, and Google recommends not to do it, but instead trusting on the [Gradle wrapper][7] included in the project.

* Open a terminal and go to the 'android' directory that contains the repository.
* Run the 'clean' and 'build' tasks using the Gradle wrapper provided
    - Windows: ```gradlew.bat clean build```
    - Mac OS/Linux: ```./gradlew clean build```
	
The first time the Gradle wrapper is called, the correct Gradle version will be downloaded automatically. An Internet connection is needed for it works.
	
The generated APK file is saved in android/build/outputs/apk as android-debug.apk


[0]: https://github.com/opencloud-eu/android/blob/main/CONTRIBUTING.md
[1]: https://git-scm.com/
[2]: https://git-scm.com/downloads
[3]: https://developer.android.com/sdk/index.html
[4]: https://developer.android.com/sdk/installing/index.html
[5]: https://developer.android.com/studio
[6]: https://gradle.org/
[7]: https://docs.gradle.org/current/userguide/gradle_wrapper.html
