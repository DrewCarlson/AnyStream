<h1 align="center">AnyStream</h1>
<h3 align="center">A self-hosted streaming service for your media collection.</h3>

---

<p align="center">
<img alt="Status" src="https://img.shields.io/static/v1?label=status&message=wip&color=red"/>
<a href="https://github.com/drewcarlson/AnyStream/releases/latest" style="text-decoration: none !important;">
<img alt="Latest Release" src="https://img.shields.io/github/v/tag/drewcarlson/anystream?label=release&sort=semver">
</a>
<a href="https://matrix.to/#/#anystream-general:matrix.drews.website">
<img alt="Matrix" src="https://img.shields.io/matrix/anystream-general:matrix.drews.website">
</a>
<img alt="Tests" src="https://github.com/DrewCarlson/AnyStream/workflows/Tests/badge.svg"/>
<a href="https://codecov.io/gh/DrewCarlson/AnyStream">
  <img alt="Codecov" src="https://img.shields.io/codecov/c/github/drewcarlson/anystream?token=X4G9RL8QZF">
</a>
<a href="https://github.com/DrewCarlson/AnyStream/pkgs/container/anystream" style="text-decoration: none !important;">
<img alt="Docker Push" src="https://img.shields.io/github/workflow/status/drewcarlson/anystream/Publish%20Docker%20image?label=Docker">
</a>
<a href="https://raw.githubusercontent.com/DrewCarlson/AnyStream/main/LICENSE" style="text-decoration: none !important;">
<img alt="AGPL 3.0 License" src="https://img.shields.io/github/license/drewcarlson/anystream"/>
</a>
</p>

### Features

- Track and organize your existing media library
- Stream to all your favorite devices
- Share your library with fine-grained permissions

<details>
<summary>Screenshots</summary>

![](media/screenshot-android-home.png)
![](media/screenshot-web-home.png)

</details>

### Structure

- [anystream-server](anystream-server) &mdash; Web server for managing and streaming media built with [Ktor](https://github.com/ktorio/ktor)
- [anystream-data-models](anystream-data-models) &mdash; Data models shared between the server and clients
- [anystream-client-core](anystream-client-core) &mdash; Multiplatform infrastructure for AnyStream client applications built with [Mobius.kt](https://github.com/DrewCarlson/mobius.kt)
- [anystream-client-android](anystream-client-android) &mdash; Android client implementation built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [anystream-client-ios](anystream-client-ios) &mdash; iOS client implementation built with [SwiftUI](https://developer.apple.com/xcode/swiftui/)
- [anystream-client-web](anystream-client-web) &mdash; Web client implementation built with [Jetbrains Compose](https://github.com/JetBrains/compose-jb/)

### Development Setup

- Install [Intellij IDEA](https://www.jetbrains.com/idea/) (preferred) or [Android Studio](https://developer.android.com/studio/)
- (macOS) Install [Xcode](https://developer.apple.com/xcode/) and command line tools `xcode-select --install`
- Clone this repo `git clone https://github.com/DrewCarlson/AnyStream.git`
- Open the `AnyStream` folder in your IDE

### License

This project is licensed under AGPL-3.0, found in [LICENSE](LICENSE).
