# The brain of `chaitany-claude`

Long-term, written memory for the agent working on the Focus launcher for Chaitany Patel.
The rules for reading and updating it are in `.claude/CLAUDE.md` and are mandatory.

**This brain is public.** It is committed to a public repository by the owner's decision
(2026-09-19). Nothing secret, nothing about the owner's server or device, and nothing observed on
his phone is written here. Facts of that kind that an agent needs are kept in `private/`, which is
git-ignored and exists only on the owner's machine.

```
1-basics.md                     TIER 1  always read, one screen: what, who, rules, where, commands, state
2-overview/                     TIER 2  read the areas your task touches, about a page each
    user-and-decisions.md               what the owner asked for and decided; open decisions
    app.md                              architecture, features, where each lives, what is easy to break
    device-testing.md                   building, installing and verifying on a phone that is in use
    website-and-server.md               the site, the server, deployment, search
    github-and-release.md               the public repo, the pre-push audit, signing, releasing
3-details/                      TIER 3  read only when changing or debugging that subsystem
    usage-tracking.md                   screen time algorithm, running total, how to prove it
    app-classification.md               social / game / video / tool / unsure
    timers-wall-consent.md              launch gate, wall, consent, the accessibility service
    weekly-review.md                    when the review appears, what it shows, how the week is summed
    home-drawer-menu-setup.md           home sections, shortcuts, drawer, long-press menu, Setup page
    ui-system.md                        theme, primitives, touch feedback, layout fit, motion
    calendar.md                         one calendar, emoji, agenda cache, the work-profile limit
    performance.md                      probes, findings, changes, results, honest memory numbers
    toolchain-and-build.md              versions and why, build types, checks, manifest
    server-nginx.md                     how the site is attached to the server, the change procedure
    seo.md                              what was done for search, what only the owner can do
    signing-keys.md                     the release key, the debug key, the owner's phone
    mistakes-and-lessons.md             what went wrong here and the platform traps behind it
    brain-upkeep.md                     keeping this brain complete, true and publishable; fact-check commands
    ci-and-releases.md                  the CI workflow, the release page, cutting a release, reviewing a contributor's PR
journal.md                      append-only record of each finished piece of work
private/                        NOT COMMITTED. Server, device and phone specifics; audit patterns.
```

Where does a new fact go? Every task needs it → Tier 1 (and keep Tier 1 to one screen). It
explains how an area works or its status → Tier 2. It is a mechanism, a number, an exact command
or the reasoning behind a decision → Tier 3. It is about *when* something happened → the journal.
It identifies the owner's server, device, accounts or habits → `private/`, never the tiers.
