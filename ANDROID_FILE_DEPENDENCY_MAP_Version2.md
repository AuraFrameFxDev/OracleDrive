```mermaid
flowchart TD
    subgraph Gradle & Config
        A1[TOML (libs.versions.toml)]
        A2[build.gradle]
        A3[settings.gradle]
    end

    subgraph App Structure
        B1[MainActivity.kt]
        B2[AndroidManifest.xml]
        B3[colors.xml]
        B4[Composable.kt]
        B5[themes.xml]
        B6[activity_main.xml]
    end

    %% Connections
    A1 -- "Version aliases and dependency info" --> A2
    A2 -- "Declares plugins & dependencies" --> B1
    A2 -- "Declares package, applicationId" --> B2
    A3 -- "Includes modules" --> A2

    B1 -- "Declared as activity in" --> B2
    B2 -- "Must have android:name match package/class" --> B1
    B3 -- "Colors referenced by" --> B4
    B4 -- "Composable UI (Jetpack Compose)" --> B1
    B5 -- "Theme (Compose/Views)" --> B4
    B6 -- "setContentView in MainActivity" --> B1

    %% Special note for Compose
    B3 -. "If using Jetpack Compose,\ncolor names in colors.xml\nmust match Color usages in Composables" .-> B4
```