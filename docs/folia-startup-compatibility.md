# Owned GuizhanLib 1.8.1 startup compatibility

The Folia 26.3 declared-addon startup test reproduced an
`UnsupportedOperationException` in GuizhanLib `AbstractAddon.onEnable` before GCE's
own `enable()` callback ran. The library unconditionally called its Bukkit
`Scheduler.repeat` to increment an addon-global integer. The original evidence is
[Slimefun Legacy run 39336509843](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/39336509843).

`AbstractAddon.onEnable` is final and exposes no counter scheduler injection point.
The current upstream source still has this restriction. Replacing the entire addon
lifecycle/configuration/localization stack would broaden this repair unnecessarily.

## Exact source and license

`src/main/java/net/guizhanss/guizhanlib/slimefun/addon/AbstractAddon.java` is a
compatibility copy of the **1.8.1** source, preserving its authors Mooy1 and ybw0014
and its GPL-3.0 license (the repository's `LICENSE`). It is not a copy of the newer
2.x implementation.

- Original project: https://github.com/ybw0014/guizhanlib
- Original archive: https://repo.maven.apache.org/maven2/net/guizhanss/GuizhanLib-api/1.8.1/GuizhanLib-api-1.8.1-sources.jar
- Source archive SHA-256: `0d6e265c0671f74fc44e44b83646830a49098693e1542d6768490bbc8d984cb8`
- Original `AbstractAddon.java` SHA-256: `eb9fba435e4774a751599a54c2d53a0b73672842042526ef45bc3e2e49fe6a6c`

## Ownership and behavior

Maven Shade explicitly excludes the dependency's original `AbstractAddon.class`
and its synthetic nested classes. The project supplies the single replacement,
which is relocated into GCE's existing private Guizhan namespace. Every other
dependency class, including the general `Scheduler`, remains unchanged.

The only runtime difference is ownership of the known integer-counter callback:
`AddonTickCounter` uses Folia's global region scheduler or Paper's existing Bukkit
scheduler, always starting after one tick and repeating at `max(1, interval)`.
It retains and cancels that task during disable and rejects late callback delivery
after cancellation. It never moves entity, block or machine work onto the global
Folia scheduler. The 1.8.1 counter's increment/overflow behavior remains unchanged.

The focused tests exercise the actual compiled helper, Paper/Folia dispatch,
intervals, cleanup and submission errors. A control executes the unmodified
Guizhan scheduler on the same rejecting Folia fixture and reproduces the original
failure. The artifact verifier compares the original/compiled/packaged protected
ABI and compiled/packaged instruction listings and rejects duplicate ownership or
test-code leakage. Native startup and restart evidence remains a separate required
check; these tests do not certify all gameplay on Folia.

## Publication

The two automatic publishing paths have been consolidated into one manual,
default-off, version-confirmed workflow. Ordinary pushes and pull requests can
validate changes without replacing the existing 1.0.3 or development releases.
