Stage 10 - wall outlets, empty mount marks, carpet dents

New files: block/ChairStackBlock.java (stacked chairs as one block), WallDecor.java, block/DecalBlock.java, block/WallSignBlock.java, 3 blockstates, 3 block models, 3 item models, 6 textures.
Changed file: world/BackroomsChunkGenerator.java (replace the old one).
ModBlocks.java does not need to change: WallDecor registers its own blocks.

Optional - add these lines to assets/backroomsmod/lang/en_us.json (before the last }):
  "block.backroomsmod.wall_outlet": "Wall Outlet",
  "block.backroomsmod.wall_mount_mark": "Empty Mount Mark",
  "block.backroomsmod.carpet_dent": "Carpet Dent",
  "block.backroomsmod.wall_sign": "Wall Sign",
  "block.backroomsmod.chair_stack": "Stacked Chairs"

How sparse it is (tune in BackroomsChunkGenerator):
  OUTLET_CHANCE 0.07  - outlets (about 1 per 24x24 blocks)
  MOUNT_CHANCE  0.045 - empty mount marks
  DENT_CHANCE   0.06  - carpet dent footprints
  WALL_SIGN_CHANCE 0.03 - stop signs stuck in walls (crooked or half buried)
Lower the numbers for fewer, raise for more. Only plain solid walls get outlets/marks; never doorways,
arches, set-piece rooms or the exit-door cell.
