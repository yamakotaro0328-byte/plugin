# Third-party jar

`quickshop-bukkit-6.3.0.1.jar` is the official QuickShop-Hikari 6.3.0.1 plugin jar, downloaded
from https://github.com/QuickShop-Community/QuickShop-Hikari/releases/tag/6.3.0.1
(dual-licensed GPLv3/AGPLv3 by the QuickShop-Community project - unmodified, redistributed as
permitted by that license).

It's committed here only because QuickShop-Hikari isn't actually published to any public Maven
repository despite what its own README's "Developer API" section says (its CI publishes to
Hangar/Modrinth, never to Maven) - see the comment on the `com.ghostchu:quickshop-bukkit`
dependency in `../pom.xml`. It's used as a `system`-scope, compile-only dependency: this addon
never bundles or redistributes any of its classes in its own built jar.

If QuickShop-Hikari ever does publish real Maven artifacts, or a newer version's API needs to be
targeted, replace this file and bump the version in `../pom.xml` to match.
