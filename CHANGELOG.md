# What Changed? – changelog

## 1.3.1
- Fixed: a mod updated to a version with a suffix such as `1.2.3-hotfix`, `-fix`, `-patch1` or `-r2` was listed as
  **downgraded**. Fix suffixes now count as newer than the plain version.
- Loader labels in a version (`-fabric`, `-forge`, `-neoforge`, …) no longer decide the order.
- Versions that can't be ordered (an unknown word, like `1.2.3-ultimate`) are shown as updated, not downgraded.
  A change is only listed as a downgrade when the order is certain.
- Pre-releases still count as older than the release: `-SNAPSHOT` < `-alpha` < `-beta` < `-pre` < `-rc` (also when
  glued to the number, like `1.0.0beta3` or `1.0b2`).

## 1.3.0
- The list scrollbar can be dragged with the mouse.

## 1.2.2
- Fixed: a mod whose version contains the Minecraft version (e.g. `1.21.1-0.0.5` → `1.3.0`) was listed as downgraded.
