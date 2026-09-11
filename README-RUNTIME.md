# Valthorne examples — prebuilt runtime

This download includes compiled examples, assets, native libraries and launcher
scripts. **Gradle and an engine source checkout are not required.** Supply a
64-bit Java 25 runtime and a compatible graphics driver. Set `JAVA_HOME` to Java 25
or make Java 25 available on `PATH`.

Extract the complete archive to a writable folder. Open PowerShell in the extracted
`Valthorne-examples-runtime-2.0.0` directory on Windows (or the `installDist` root
when building locally):

```powershell
.\bin\Valthorne-examples.bat fps --smoke
.\bin\Valthorne-examples.bat fps
```

The first command runs the bounded FPS smoke check and exits. The second opens the
playable arena. Click **Enter arena** to play; Escape pauses and releases the mouse.
FPS requires Windows x64 and OpenGL 4.3 for Filament texture sharing.

Use `--list` to see all demos, or `fps --help` for options. On Linux/macOS use
`./bin/Valthorne-examples` and a supported demo such as `scene`. The Unix launcher
sets macOS's first-thread flag automatically. Keep the entire `lib/` folder beside
`bin/`; this distribution supplies dependencies rather than downloading them.
Captures and saves are written under `build/` relative to the current directory.

Source and walkthroughs: [Valthorne examples](https://github.com/tehnewb/Valthorne-examples).
See [platform and Gradle startup troubleshooting](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/platforms.md).
The optional `java tools/JavaLoopbackCheck.java` diagnostic requires JDK 25, opens
only local connections, and does not use Gradle or change machine settings.

Code is Apache-2.0; see `LICENSE`. Example asset licenses/provenance are preserved
inside the example JAR's `valthorne/` resource directories. Dependencies retain
their notices in their respective JARs. `THIRD_PARTY_NOTICES.md` describes the source
project's original resource locations; keep all notices when redistributing.
