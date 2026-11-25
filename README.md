# FastRemote

FastRemote is a desktop SSH and SFTP client for macOS, Windows, and Linux.
The community edition includes the terminal, SFTP file manager, saved hosts,
encrypted credential vault, AI integration, and MCP integration. It runs
without a FastRemote license or an account on YNPE.

Official downloads are available from [YNPE](https://download.ynpe.com/community/)
and [GitHub Releases](https://github.com/feinir/FastRemote/releases).

## Build from source

Install **BellSoft Liberica JDK 21 Full** for your operating system and CPU.
Set `JAVA_HOME` to that JDK. The Full distribution supplies the JavaFX modules.
No Go toolchain, signing credentials, or license server is required.

```sh
./gradlew check
./gradlew run
./gradlew packageAppImage
```

On Windows, use `gradlew.bat`. Native app images are built on their target
operating system. The community image is under `build/jpackage/community`.
The development run keeps its data in `.fastremote-dev/community`.

The Mac App Store build uses `-PfastremoteEdition=store`. An uploadable package
requires the App Store certificate pair and `packageMacAppStore` on macOS.
The store edition uses App Sandbox and receives updates through the store.

## Licensing

FastRemote's original source code in this repository is available under the
[Mozilla Public License 2.0](LICENSE). The corresponding source for an official
binary is the Git tag with its version. Vendored JediTermFX, RichTextFX,
third-party fonts and icons retain their own licenses. See
[Third-party notices](THIRD_PARTY_NOTICES.txt) and [Source availability](SOURCE_OFFER.txt).
The product name and logo are covered by the [trademark notice](TRADEMARKS.md).
