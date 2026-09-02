# SF Genetic ChickEngineering

A maintained Slimefun resource-chicken addon based on **Genetic Chickengineering Reborn** by ybw0014 and the original work by kii-chan.

This fork is maintained for the **Slimefun Legacy** ecosystem and modern Paper-family Minecraft servers.

## Supported server range

| Platform | Support |
| --- | --- |
| Paper | 1.21.11 through 26.2 |
| Purpur | Supported on equivalent Paper-compatible versions |
| Leaf | Supported on equivalent Paper-compatible versions |
| Folia | Supported with region-aware addon scheduling |
| Spigot/CraftBukkit | Not supported |

### Java policy

- **Build JDK:** Java 25+
- **Plugin bytecode:** Java 21
- This lets the project compile against Paper 26.2 while retaining the Java 21 bytecode floor needed by older supported 1.21.11-era deployments.

CI compiles every change against both the **Paper 1.21.11 API floor** and the **Paper 26.2 API line**. If a future change accidentally uses a newer-only API, the minimum-version build should catch it before release.

## Releases

Release builds use the naming format:

`SF_GeneticChickEngineering<version>.jar`

Tagging a commit as `vX.Y.Z` builds the project on Java 25 and attaches the raw JAR directly to the GitHub release.

## Overview

Genetic Chickengineering is an implementation of resource chickens heavily inspired by SetyCz's popular Forge mod **Chicken**.

Instead of a fixed breeding tree, Genetic ChickEngineering uses a basic genetics simulation to determine progression. Players capture chickens, sequence their DNA, selectively breed useful traits, and eventually produce chickens capable of generating resources.

This is a mid- to late-game Slimefun addon.

## Plugin basics

Overworld chickens have almost completely become the dominant, "normal" chickens we know today, but some carry latent powers. With the right tools, time, and care, those traits can be uncovered.

The first step is to craft a **Chicken Net** and turn chickens into **Pocket Chickens**. Build a **Genetic Sequencer** to analyze their genotypes. Once you have favorable chickens, place two into a **Private Coop** and let genetics do its work.

Eventually you can breed chickens that produce resources. Put an eligible Pocket Chicken into an **Excitation Chamber** and it will generate its associated resource. Chickens with stronger genetic traits can produce resources faster.

The addon also includes upgraded Excitation Chambers, a Restoration Chamber, and a Growth Chamber.

## Compatibility design

This fork keeps server-version-sensitive behavior behind small compatibility boundaries instead of scattering implementation checks throughout gameplay code.

Current goals:

- depend on public Slimefun/Bukkit/Paper APIs wherever possible
- avoid server-implementation/NMS classes
- keep world access region-safe on Folia
- keep optional StackMob and WildStacker integrations optional
- retain the 1.21.11 compatibility floor while tracking Paper 26.x
- centralize Paper and dependency versions in Maven so future releases are simple version bumps

## Images

![A basic machine overview](/images/gce_machines.png)

![A baby chicken fresh out of the Genetic Sequencer](/images/gce_genseq.png)

![An experience chicken working](/images/gce_excham.png)

By default, a chicken displays its resource in its custom name after passing through a Genetic Sequencer. This can be disabled globally in `config.yml`.

![A nether quartz chicken named Crystal](/images/gce_names.png)

## Credits

This project exists because of the work of the original Genetic Chickengineering authors and the Slimefun community.

Special credit to:

- **kii-chan / kii-chan-reloaded** — original Genetic Chickengineering
- **ybw0014** — Genetic Chickengineering Reborn rewrite
- **CrispyXYZ** — later maintenance work and Ultimate Excitation Chamber improvements
- **TheBusyBiscuit and Slimefun contributors** — Slimefun
- translators and contributors to the upstream projects

This fork continues under the upstream **GNU GPL v3.0** license.

## Development branch

Modernization work for the 1.21.11–26.2 compatibility line is developed on `legacy-1.21.11-26.2` before merging into `master`.
