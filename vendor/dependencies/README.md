# Pinned ProtocolLib binary

ProtocolLib 5.5.0-SNAPSHOT, official Spigot distribution (also usable on Paper),
downloaded from the upstream `dev-build` release on 2026-11-15.
The Paper distribution in that release requires API 26.3 and cannot load on 1.21.11.

- Source: https://github.com/dmulloy2/ProtocolLib
- Download: https://github.com/dmulloy2/ProtocolLib/releases/download/dev-build/ProtocolLib-Spigot.jar
- GitHub release asset ID: `708097332`, updated `2026-11-04T03:38:12Z`
- SHA-256: `736a9d6054032871d593aa2c0a0bf65e117557aadc11830394e5e8b757ae05b2`
- License: GNU GPL v2, https://github.com/dmulloy2/ProtocolLib/blob/master/License.txt

The binary is committed to avoid depending on a changing snapshot URL. It is only
a compile/test dependency; install this same JAR as `plugins/ProtocolLib.jar` on
the target server. It is not included in ThePitGay-High.jar.
