# Legal documents

`privacy.md` and `terms.md` are the **source of truth** for what Sodogku
publishes at:

- <https://nightjarlabs.llc/sodogku/privacy>
- <https://nightjarlabs.llc/sodogku/terms>

They live here, next to the code, because a privacy policy is a claim about what
a specific binary does. Keeping it in a marketing repo guarantees the day comes
when the app collects something the policy does not mention, and nobody notices
until a store reviewer does. That is not hypothetical: it is why this folder
exists rather than a `pages/` folder of hand-written HTML.

## These are not starting points

Unlike the template's copies, both files here were written against this app's
code and are owner-accepted. `privacy.md` was derived from the tree in `1ec24e0`
and re-derived on 2026-09-10; `docs/store/data-safety.md` is the working that
produced it and cites the file behind every claim. They moved here from
`pages/*.html` on 2026-09-25 with the prose unchanged.

Re-read them whenever a network call, an SDK or a telemetry attribute changes.
The four things most likely to invalidate a sentence are listed at the top of
`data-safety.md`.

## How they get published

`.github/workflows/legal-sync.yml` watches `legal/**` on `main`. When either
file changes it opens a pull request against the website repository, copying
both files into `src/content/legal/sodogku/`. That repo's
`auto-merge-legal.yml` merges the PR as soon as the site builds, and publishes.

Run `./scripts/setup_legal_sync.sh` once to create the two secrets it needs.

**So editing these files is publishing them.** There is no click between your
commit and `nightjarlabs.llc`, give or take a couple of minutes. Write them as
if they are already live, because shortly they are.

The one gate is the build. The site validates frontmatter against a zod schema,
so a malformed file leaves the PR open and the live site serving the last good
version. It does not check whether the words are true. Nothing does. That part
is on you, which is the whole reason these files sit next to the code.

## Rules

- **Frontmatter is a contract.** `app`, `title`, `updated` and `contact` are
  validated by a zod schema in the website repo. A missing or misspelled key
  fails *that* repo's build, not this one, so the failure shows up somewhere
  confusing. Bump `updated` whenever the text changes; both stores expect a
  policy to carry a date.
- **Do not edit the copies in the website repo.** The next sync silently reverts
  them.
- **The URLs get filed with Apple and Google.** They are also the compiled
  defaults in `LegalConfigValues.kt`. Changing the slug after submission
  means re-filing on both stores and waiting out two reviews.

## app-ads.txt moved with them

`pages/app-ads.txt` went when `pages/` did. AdMob crawls the domain in the
**store listing's website field**, so the copy on GitHub Pages stopped being
read the moment that field pointed at `nightjarlabs.llc`. The studio site serves
the same single line at <https://nightjarlabs.llc/app-ads.txt>, and it covers
every Nightjar app because it is one publisher id. Verified identical before the
old file was deleted.

The listing's website field has to be the **bare domain with no path**, or the
crawler looks in the wrong place. A missing `app-ads.txt` does not error
anywhere; it quietly drops fill rate.
