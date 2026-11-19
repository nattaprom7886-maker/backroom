Stage 12 - "Waiting Room" zone in Level 0

New file only (nothing existing is changed):
  src/main/java/com/backroomsmod/world/WaitingRoomZone.java

What it is: a rare 16x16 area (about 1 chunk in 65) - open plaza with a 10x10 wallpaper room inside.
Inside: row of wooden/office chairs (one fallen), reception desk, flickering/dead light panels,
wet carpet patches, and a chest in the corner with a written book.
It is built when a player comes within 4 chunks, so BackroomsChunkGenerator is not touched.
Skipped if the chunk touches a set piece (house/McDonald's/etc.) or an exit door.

Tweak: CHANCE, MIN_CHUNK_DIST, SCAN_RADIUS at the top of the file.
Note: in a world you already explored, an old chunk that is picked will also get the zone (it replaces what was there).
I could not compile this here (no JDK), so build it and send me any error.
