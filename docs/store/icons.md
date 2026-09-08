# App icon and store artwork: what ships today, what has to be replaced

Audited 2026-09-08 against the tree at that date. Nothing here was changed; the icon redraw is on
the user's list (SPEC §20) and two of the files below are template placeholders that will fail
review.

---

## 1. Summary

| Surface | State | Blocking? |
|---|---|---|
| Android launcher icon (legacy + adaptive) | **Real art.** The dog head, no numerals. | No |
| Android adaptive background layer | Real. Flat brand cream `#FAF5EB`. | No |
| Android themed (monochrome) icon | **Absent.** No `<monochrome>` element, so Android 13+ themed icons fall back to the standard icon. | No, cosmetic |
| Android notification icon | Not present, and not needed: the app posts no notifications. | No |
| Play Store listing icon, 512x512 | **Template placeholder**, reads "YOUR APPS IMAGE HERE". | **Yes** |
| Play feature graphic, 1024x500 | Does not exist. | **Yes**, Play requires one |
| iOS app icon set | **Template placeholder**, same grey "YOUR APPS IMAGE HERE" square. | **Yes**, App Store review rejects placeholder icons |
| `art/source/stills/dog-appmark.png` | Has a sudoku grid with the numerals 3, 7, 1, 9. Sodogku has no numbers. | Source art, not shipped |

Two of these are hard blockers on submission and neither is a code problem: they are files waiting
for artwork.

---

## 2. Android, in detail

### 2.1 What is referenced

`apps/compose/src/androidMain/AndroidManifest.xml:11` and `:13`:

```
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

On API 26 and up those resolve to the adaptive XMLs in `mipmap-anydpi-v26/`. Both
`ic_launcher.xml` and `ic_launcher_round.xml` are identical:

```
<background android:drawable="@drawable/ic_launcher_background"/>
<foreground android:drawable="@mipmap/ic_launcher_foreground"/>
```

Below API 26, the flat `mipmap-*/ic_launcher.webp` and `ic_launcher_round.webp` are used directly.

### 2.2 What is actually there

All of these were replaced with the dog art in commit `df270fc`, "feat: replace the template
launcher icon with the dog". The icon on the emulator today is a cream dog head with tan ears and
**no sudoku grid and no numerals**, so the specific problem called out in SPEC §20 does not affect
the Android launcher. It affects the source PNG and the store listing icon.

| File | Size | Content |
|---|---|---|
| `apps/compose/src/androidMain/res/mipmap-mdpi/ic_launcher.webp` | 48x48 | dog head, full bleed |
| `.../mipmap-hdpi/ic_launcher.webp` | 72x72 | same |
| `.../mipmap-xhdpi/ic_launcher.webp` | 96x96 | same |
| `.../mipmap-xxhdpi/ic_launcher.webp` | 144x144 | same |
| `.../mipmap-xxxhdpi/ic_launcher.webp` | 192x192 | same |
| `.../mipmap-*/ic_launcher_round.webp` | same five sizes | same |
| `.../mipmap-*/ic_launcher_foreground.webp` | 108, 162, 216, 324, 432 | dog head on transparency, the adaptive foreground |
| `.../drawable/ic_launcher_background.xml` | 108dp vector | a single path filled `#FAF5EB`, with a comment explaining that a flat cream lets the dog carry the shape |

**Safe-zone check on the adaptive foreground.** Android masks an adaptive icon to the central 66dp
of the 108dp canvas, which is 19.4% to 80.6% on each axis. Measured on the 432px xxxhdpi layer, the
non-transparent art occupies x 25.9%–73.8% and y 34.0%–68.3%. It sits inside the safe zone and is
horizontally centred to within a pixel.

It is also **small**: the art fills 48% of the canvas width where the safe zone allows 61%, so on a
home screen the dog reads noticeably smaller than neighbouring icons. Worth fixing on the redraw
rather than now.

### 2.3 Template leftovers that are still in the tree

Neither is used, neither is harmful, and both should go when someone is next in these files.

- **`apps/compose/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml`** is the Android
  Studio green-robot clipart vector, 29 lines of gradient paths. It is **dead**: both adaptive XMLs
  reference `@mipmap/ic_launcher_foreground`, which names the mipmap explicitly, so this drawable is
  never resolved.
- **`apps/compose/src/androidMain/res/values/ic_launcher_background.xml`** declares
  `<color name="ic_launcher_background">#262627</color>`, a dark grey from the template. It is also
  unused, and for a subtle reason worth knowing: `@drawable/ic_launcher_background` and
  `@color/ic_launcher_background` are two different resources that share a name, and the adaptive
  icon asks for the drawable, so the cream vector wins and the grey colour is never read. Nothing
  else in the tree references either name. If someone ever changes an adaptive XML to
  `@color/ic_launcher_background`, the icon silently goes dark grey.

### 2.4 What is missing

- **No `<monochrome>` layer.** Android 13+ themed icons need a third element in the adaptive XML
  pointing at a single-colour drawable. Without it the launcher uses the full-colour icon in themed
  mode, which is not broken, just not themed. Cheap to add with the redraw: one silhouette vector,
  referenced from both adaptive XMLs.
- **No notification icon.** Correct, for now. The app posts no notifications:
  `NotificationCompat` and `UNUserNotificationCenter` appear nowhere in Kotlin, and the only hit is
  `apps/ios/iosApp/Platform/PermissionManager.swift`, which is the template's permission plumbing.
  `apps/ios/iosApp/Info.plist:19` still carries `NSUserNotificationsUsageDescription` with the
  template string "We'd like to send you notifications." Harmless while unused; it becomes a
  reviewer question if a notification is ever requested and the string is still that.

---

## 3. iOS, in detail

`apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/` contains exactly two files:

- `Contents.json`, declaring three universal 1024x1024 entries: the default appearance, a `dark`
  luminosity variant, and a `tinted` variant. This is the modern single-size iOS 18 layout, which is
  correct.
- `icon_template.png`, 1024x1024, **the template placeholder**: a grey circle on white reading
  "YOUR APPS IMAGE HERE".

The `dark` and `tinted` entries have **no `filename` key**, so they are declared and empty. Xcode
falls back to the default appearance for both, which is legal. Filling them is optional polish and
worth doing for a game with a cream icon, because the default will look washed out in dark mode.

**This is a hard blocker.** App Store review rejects builds shipping placeholder art, and this one
literally says "your app's image here".

---

## 4. Store-listing artwork

| Asset | Required by | Spec | State |
|---|---|---|---|
| Play app icon | Play, mandatory | 512x512, 32-bit PNG, no alpha channel used for transparency, no rounded corners baked in (Play masks) | `apps/compose/src/androidMain/ic_launcher-playstore.png` exists at the right size and is **the template placeholder**, dated to the generation commit `02ec89c` |
| Play feature graphic | Play, mandatory | 1024x500 PNG or JPEG, no transparency, no text near the edges (Play crops it in some placements) | Does not exist |
| App Store icon | App Store, mandatory | 1024x1024, no alpha, no rounded corners | The `icon_template.png` above |
| Play promo video, App Store preview | Optional | | Neither exists |

---

## 5. Exactly what to replace when the artwork arrives

Assume the deliverable is a single square master with no numerals, plus a transparent foreground
layer. Then, in order:

**One master file, 1024x1024, no alpha, no baked rounded corners:**

1. `apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/icon_template.png` — replace in place, or
   rename and update the `filename` in `Contents.json`. Optionally add `dark` and `tinted` variants
   and give those entries filenames too.
2. `apps/compose/src/androidMain/ic_launcher-playstore.png` — downscale the same master to 512x512.
   This is the Play listing icon and is not compiled into the APK.

**One transparent foreground, 432x432 at xxxhdpi:**

3. `apps/compose/src/androidMain/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.webp`
   at 108, 162, 216, 324, 432. Keep the art inside 19.4%–80.6% on both axes; aim closer to the 61%
   the safe zone allows than the 48% it fills today.

**One masked square render of the whole icon:**

4. `apps/compose/src/androidMain/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` at
   48, 72, 96, 144, 192. These are the pre-API-26 fallback.
5. `.../ic_launcher_round.webp`, same five sizes, circular crop.

**Untouched unless the palette changes:**

6. `apps/compose/src/androidMain/res/drawable/ic_launcher_background.xml`, the flat `#FAF5EB`
   backdrop. It is already correct.

**Optional, same pass:**

7. A `res/drawable/ic_launcher_monochrome.xml` silhouette, plus a `<monochrome>` line in both
   `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
8. Delete `res/drawable-v24/ic_launcher_foreground.xml` and `res/values/ic_launcher_background.xml`
   (§2.3).

**Not an icon, but the same brief:**

9. A 1024x500 Play feature graphic. The daily board screenshot
   (`docs/store/screenshots/android-phone/05-daily-board.png`) is the best source of palette for it.

---

## 6. The source PNG with the numerals

`art/source/stills/dog-appmark.png` is 1024x1024: the dog in round glasses with a dark sudoku grid
tucked under its chin, showing 3, 7, 1 and 9. Sodogku has no numbers on any board, so this mark
promises a game the app is not.

It is **not** what the launcher ships. The launcher art (§2.2) is a plain dog head from the same
set and has no grid. The appmark is source art awaiting the redraw described in SPEC §20: a
colour-region grid instead of digits.

If a redraw is slow to arrive, the cheapest interim is the plain dog head already in
`ic_launcher_foreground.webp`, rendered on the cream backdrop and exported at 1024 and 512. That
unblocks both store submissions with art the app already ships, and it says nothing false.
