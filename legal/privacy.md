---
app: Sodogku
title: Privacy Policy
updated: 2026-09-25
contact: elijahdangerfield111@gmail.com
---

Sodogku has no accounts and no sign-in, so nothing here is attached to a name, an email address or a profile. Your puzzles and your progress live on your phone. Some things do leave the device: crash reports, gameplay analytics, requests for the app's own configuration, and whatever Google's ad software collects when it shows you an ad. This page says what each of those contains.

This page describes what the build of Sodogku current on the date above does. When the app changes in a way that affects what it collects, the page is revised and that date moves.

## What stays on your phone

Levels completed, scores, paw ratings, daily results, streaks and badges are written to a database on the device. Your settings, your booster counts, the board you have in progress and the versions of these documents you have accepted are stored in a file next to it.

None of this is copied to a server, because there is no server that holds player data. Android's Auto Backup is switched off for Sodogku, so it is not uploaded to your Google Drive either. Uninstalling the app deletes all of it.

## The install identifier

On first launch the app generates a random identifier (a UUID) and saves it. It is not your advertising identifier, it is not derived from your hardware, and it is not connected to anything that identifies you as a person.

It is attached to every crash report, every analytics record and every request the app makes to our own server. It is what lets a crash be read together with the events that led to it. It stays the same until you uninstall the app, and a fresh install gets a new one.

## Crash reports and diagnostics

The app uses [Sentry](https://sentry.io/privacy/) for crash and error reporting. A report can contain:

- The crash or error itself, with a stack trace.
- Your device model and operating system version, and the app version and the commit it was built from.
- The screen you were on, the install identifier, and an identifier for the current app session.
- Recent log lines from the app's own code, carried as breadcrumbs. In a release build these are the app's informational lines and above.

The Sentry SDK is configured not to attach personally identifying data of its own, and the app never gives it a name, an email address or a user id. Sodogku also sends Sentry performance measurements, sampled at 15% in a release build.

## Gameplay analytics

The app sends a stream of gameplay events to [Grafana Cloud](https://grafana.com/legal/privacy-policy/), on an account we control. These are counts and measurements of how the game is being played: the app starting, going to the background and how long the session lasted; a level being started, completed or failed, with its size, difficulty, duration, score, wrong guesses and boosters used; daily puzzle results and streaks; tutorial steps reached; whether an ad was watched, dismissed or failed, and how long it took; whether a purchase succeeded or failed.

Every record carries the install identifier, a session identifier, whether the device was offline at the time, the app version and build number, the commit it was built from, and the platform. Log lines from our own code at warning level and above are forwarded to the same place, including the message text, the part of the app that logged it, and the type and message of any exception.

Records are buffered in a file on the device before they are sent, so events survive a restart or a period offline. There is no setting inside the app that turns this off. We can switch it off remotely for everyone, but you cannot switch it off for yourself.

## Ads

Sodogku is free and pays for itself with ads through Google AdMob. Most are rewarded: one is requested when you choose to watch it in exchange for something in the game, such as refilling your bones or covering a missed day of a streak. The other kind is a full-screen ad between two levels, requested only when you have cleared several levels without seeing any ad; it gives nothing and asks nothing, and closing it opens the next level. There are no banners and nothing appears while you are playing a board.

Google's ad software collects its own data when it runs, including your device's advertising identifier, and Google is a separate company rather than a service acting on our behalf. What it collects and what Google does with it is covered by [Google's own policy](https://policies.google.com/technologies/partner-sites). On our side we record only the outcome of the ad and how long it took.

Two things happen before the first ad is ever requested, on both platforms. In the EEA and the UK, the app shows Google's consent form, and if the result is that ads may not be requested, none are and Google's ad software is not started at all. On iPhone and iPad, the app shows Apple's App Tracking Transparency prompt, so your advertising identifier is available to Google only if you allow it there. Neither prompt appears at launch. They appear the first time you reach for an ad, which is the point at which they mean something.

Ads are requested on the basis that Sodogku is a general-audience game and not directed at children.

Buying Sodogku Pro gives you the same rewards without an ad being shown.

## Purchases

Purchases go through Google Play or the App Store. The app never sees your card or your billing details, and it stores nothing about the transaction except a flag saying that Pro is active on this device.

Analytics records the outcome only: whether a purchase or a restore succeeded, was cancelled or failed, and the store's error code if it failed. No price, no order number, no receipt.

## Feedback and bug reports

Sending feedback or a bug report is entirely your choice. Nothing is sent unless you write something and press send.

When you do, the report carries your message as you typed it, the app version and the commit it was built from, and a copy of the recent log lines from your current session as an attached text file. Those log lines are the app's own diagnostic output: screens visited, network calls, errors. Before they are kept, bearer tokens, JWTs, anything labelled as a token, secret, password or API key, and anything shaped like an email address are replaced with a placeholder. What remains is deliberately readable, including screen names and the session identifier. The report itself is tagged with the session and install identifiers, the same way a crash report is.

Reports go to Sentry, where a person reads them. There is no separate feedback inbox. The form has no email field and asks for no contact details, so if you want a reply you have to put an address in the message, and it arrives as you wrote it.

Builds handed to testers before release carry an extra panel that can also attach a screenshot of the screen you were on, with a switch for whether to include the logs. That panel is not present in the App Store or Google Play build.

## Game Center

On iPhone and iPad the app connects to Apple's Game Center at launch, using whichever Game Center account is signed in on the device, and can submit your lifetime score and your longest streak to it. Those scores go to Apple under your Game Center identity. We do not receive them and keep no copy. If you are not signed in to Game Center, nothing is submitted and the app carries on without it.

## Requests to our own server

The app fetches its configuration from one endpoint on our server. Each request carries the install identifier, a session identifier, the platform, the app version and build number, the languages your operating system reports, and your country code.

That country code is the region set in your device's language and region settings. It is not GPS, not your SIM, and not derived from your IP address. Sodogku requests no location permission on either platform and has no way to read your location.

Our server uses the install identifier to decide which configuration values you get. It does not store it. Your IP address is held in memory to limit how often a single device can call the server, and is not written down or used to work out where you are.

## What the app does not collect

- Your name, email address, phone number, postal address, contacts or calendar.
- Your location, at any precision.
- Photos, files, audio, health or fitness data, browsing or search history, or a list of the apps you have installed.

We do not sell your data and we do not send it to data brokers. The one third party that receives data for its own purposes rather than ours is Google, through the ads described above.

## Permissions

Sodogku itself declares no Android permissions. The software it bundles adds internet and network-state access, Google Play's billing permission, Google's advertising identifier permissions, and a notifications permission that the app never asks you for. Sodogku sends no notifications.

On iPhone and iPad the app asks for nothing when you open it. The tracking prompt described above is the only permission it ever requests.

## Who else receives data

- [Sentry](https://sentry.io/privacy/), for crash reports, diagnostics and feedback, processing on our behalf.
- [Grafana Labs](https://grafana.com/legal/privacy-policy/), for gameplay analytics, processing on our behalf.
- [Google](https://policies.google.com/privacy), for ads through AdMob and for purchases through Google Play.
- [Apple](https://www.apple.com/legal/privacy/), for purchases through the App Store and for Game Center scores.

Everything the app sends goes over an encrypted connection. Any service receiving a request necessarily sees the IP address it came from.

## How long it is kept, and how to have it removed

Everything stored on your phone goes when you uninstall the app. Analytics and crash reports are held under our Sentry and Grafana Cloud accounts and expire on those services' retention schedules.

To ask for the records we hold to be deleted, use the form at [nightjarlabs.llc/delete-data](https://nightjarlabs.llc/delete-data), or write to the address at the bottom of this page. We will delete what we find and write back to confirm.

We should be straight about the limit on that. The only key on any record we hold is the install identifier, and the app does not currently show it to you anywhere, so a request that does not carry it gives us nothing to search for. A message sent from the feedback form in Settings does carry it, so the most reliable route today is to ask there, from the phone the records came from, and we will delete everything that shares that identifier. If you have already uninstalled, nothing further is being sent and the identifier is gone with it; a reinstall starts over with a new one.

## Children

Sodogku is a general-audience game and is not directed at children under 13. Ads are requested from Google on that basis. We do not knowingly collect anything from a child under 13.

## Changes to this policy

If this policy changes, the new version will be posted here and the "Last updated" date at the top will be revised.

## Contact

Questions, or a request about your data? Write to [elijahdangerfield111@gmail.com](mailto:elijahdangerfield111@gmail.com).
