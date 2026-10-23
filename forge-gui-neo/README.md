# Embedded NeoForge match UI

The Windows desktop match factory embeds NeoForge 1.0.31 in a `JFXPanel`. The
existing Swing lobby creates the same `HostedMatch`, players and DIY engine.
Neo's original `TableScreen`, card nodes, overlays, arrows, animations, choices
and shortcuts provide the match scene. Closing the match uses the existing
desktop navigation bar. `-Dforge.match.ui=swing` selects the original match UI.
The optional payload contains Windows x64 JavaFX 21.0.5 natives; other platforms
retain their existing Swing match factory.

`integration.json` pins the upstream commit, original binary hash, vendored
dependency hashes and exact compatibility sources. `vendor/neo-forge-1.0.31-ui.jar`
contains only upstream `forge/neo/` classes/resources, excluding upstream engine,
network and Adventure overrides. The corresponding unmodified module sources and
license are in `vendor/neo-forge-1.0.31-source.zip`. Compatibility sources here
replace only the API and embedding boundaries; the original UI remains intact.
`NeoPaymentPeek` and `NeoCostRelease` are upstream access helpers compiled against
the DIY engine. OpenJFX license and third-party notices are retained in `vendor`
and embedded under `META-INF/licenses` in the resulting Neo payload.

Build using JDK 17 and an existing DIY runtime, then verify the actual output JARs:

```powershell
./build-match.ps1 -RuntimeRoot <forge-diy-runtime>
./verify-match.ps1 -RuntimeRoot <forge-diy-runtime>
```

The build preserves existing game/desktop overlays, removes the superseded Swing
visual imitation, and emits replacement overlays in `target/match/overlays`.
It does not launch or close a game, change the aggregate JAR, or publish files.
Verification runs without a window and loads ten explicitly listed scripts. It
checks real cached Chinese artwork, a generated DIY card face, three arrow kinds,
impact animations and a complete `HostedMatch` through the installed DIY engine.
`NeoAnimationEventTest` checks coin/dice outcomes, legacy event compatibility and
counterspell target display without mutating the actual spell targets.

Card art goes through the existing desktop `ImageCache` on the Swing EDT and is
converted to a JavaFX image. Scene changes remain on the JavaFX thread;
`Platform.setImplicitExit(false)` keeps the toolkit available between matches.
No Neo application/lobby entry point is invoked. Online protocols remain the DIY
implementation. Animation events add result data with compatible no-argument
constructors and a default visitor method; they do not reroll or change outcomes.
