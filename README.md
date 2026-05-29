# Bitt Metronome

A metronome for drummers
An Android app built with Kotlin + Jetpack Compose and a low-latency C++ (Oboe) audio engine.

## Why this exists

This started as a university assignment about building with **Claude** (Anthropic's AI). Instead of a throwaway demo, I used the chance to build something I actually needed as a drummer: a metronome tailored to how I practice and play. Every feature here comes from a real use case I need for making sure I can stay within tempo for multiple songs. 

## What it does

The two things I built it around:

- **Change tempo mid-run** — switch between setlist on the fly.
- **Store multiple setlists** — keep several tempo/meter presets ready .

Plus:

- Two preset "boxes" per setlist with a **manual swap at the end of the bar** — line a tempo change up to land on the downbeat.
- A **visual bar/beat grid** that lights up in time.
- Time signature (2–6 beats per bar), phrase length (1–8 bars), and eighth-note **subdivision**.
- **Audio↔visual offset** calibration.
- **Lock mode** to avoid accidental edits during a take.
- **Background playback** — keeps clicking when the app is backgrounded.
- Light / Dark / Beige themes; portrait, landscape, and tablet-centered layouts.

## Tech

- **Kotlin** + **Jetpack Compose** UI
- **Oboe** (C++ / NDK) for low-latency, sample-accurate audio scheduling
- **DataStore** for preset + settings persistence
- A foreground service for background playback
- `minSdk 24`, `targetSdk 34`

## Build

Open the project in **Android Studio** with the Android **NDK** installed, then Run. The native audio engine is compiled via CMake/NDK as part of the Gradle build.

## Note

Built with the help of **Claude** (Claude Code) as part of a university assignment exploring AI-assisted development — then shaped, revision by revision, into a tool I use for real.
