# Run Valthorne examples

This download includes compiled examples, assets, native libraries and launcher
scripts. **No example needs Gradle to run.**

## Windows: extract and double-click

Click any demo name in the repository's [download catalog](README.md#choose-a-demo)
to download that demo. Extract its complete ZIP and double-click **Start.bat**.
Each individual download includes Java and all required dependencies. To check it
automatically, run `Start.bat --smoke` in that extracted directory.

The following launch-file table applies to the optional **complete collection**:

Download `Valthorne-examples-windows-x64-2.0.1.zip` and extract the whole archive to
a writable folder. Java is included; no Java installation or environment setup is
needed. Open the extracted folder and double-click a demo:

| Launch file | Example |
| --- | --- |
| `starter.bat` | Application starter |
| `scene.bat` | 3D scene |
| `physics.bat` | Physics playground |
| `lighting2d.bat` | 2D lighting |
| `ui.bat` | UI gallery |
| `audio.bat` | Audio studio |
| `lighting-studio.bat` | Lighting studio |
| `path-tracing.bat` | Path tracing |
| `physics-studio.bat` | Physics studio |
| `fps.bat` | FPS arena |

Keep `runtime/`, `lib/` and `bin/` in the extracted folder. The launch files use
the bundled Java even if another Java installation is configured on the computer.
They create captures/saves under that folder's `build/`. If a double-click launch
fails, its console stays open so the error can be read. Compatible graphics drivers
are still required; FPS and the advanced studios need OpenGL 4.3 on Windows x64.

## Optional commands and other platforms

The smaller `Valthorne-examples-runtime-2.0.1.zip` supplies the same examples and
launchers without bundled Java. For that archive, supply Java 25 through `JAVA_HOME`
or `PATH`. Linux/macOS users can run `./scene.sh` or another supported demo's `.sh`
file. macOS cannot run OpenGL 4.3 examples or the Windows Filament backend.

Extract the complete archive to a writable folder. Open PowerShell in the extracted
distribution directory on Windows:

```powershell
.\fps.bat --smoke
.\fps.bat
```

The first command runs the bounded FPS smoke check and exits. The second opens the
playable arena. Click **Enter arena** to play; Escape pauses and releases the mouse.
FPS requires Windows x64 and OpenGL 4.3 for Filament texture sharing.

Use `--list` to see all demos, or `fps --help` for options. On Linux/macOS use
`./bin/Valthorne-examples` and a supported demo such as `scene`. The Unix launcher
sets macOS's first-thread flag automatically. Keep the entire `lib/` folder beside
`bin/`; this distribution supplies dependencies rather than downloading them.
Captures and saves are written under `build/` relative to the current directory.

The bundled Eclipse Temurin 25.0.4.1+1 runtime is unmodified. Its license files
are under `runtime/legal/`; its upstream package, checksum and source location are
recorded in `java-runtime.properties`. Matching OpenJDK source is also available
beside the desktop download on the release page.

Source and walkthroughs: [Valthorne examples](https://github.com/tehnewb/Valthorne-examples).
See [platform and Gradle startup troubleshooting](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/platforms.md).
The optional `java tools/JavaLoopbackCheck.java` diagnostic requires JDK 25, opens
only local connections, and does not use Gradle or change machine settings.

Code is Apache-2.0; see `LICENSE`. Example asset licenses/provenance are preserved
inside the example JAR's `valthorne/` resource directories. Dependencies retain
their notices in their respective JARs. `THIRD_PARTY_NOTICES.md` describes the source
project's original resource locations; keep all notices when redistributing.
