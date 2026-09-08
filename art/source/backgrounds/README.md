# Background art

Drop the paw-and-bone background PNGs here, then copy them into
`libraries/resources/src/commonMain/composeResources/drawable/` under the same
name. This folder is the archive of originals; that one is what the app
compiles against, the same split the dog stills use.

Compose generates `Res.drawable.<filename without extension>`, so the name has
to be lowercase snake_case and is the name the code will reference.

Expected, from the three images described in chat:

| File | What it is | Where it goes |
|---|---|---|
| `bg_paws_bones.png` | Bones and paws scattered, fading out toward the bottom | The launch handoff, behind the dog |
| `bg_paws.png` | Paws only, sparse through the middle | The welcome screen, so the title and buttons stay readable |
| `bg_tile.png` | Dense, seamlessly tileable | Anywhere a repeating fill is wanted |

Say which is which if the names do not match what you saved, and note whether
`bg_tile.png` really does tile edge to edge. A pattern that almost tiles shows
a seam on a tall screen, and that is not something a screenshot of one tile
will reveal.

Transparent backgrounds, please, not white. The screens behind these are cream
(`ColorResource.Cream100`), and a white-backed PNG will paint a visible
rectangle over it.
