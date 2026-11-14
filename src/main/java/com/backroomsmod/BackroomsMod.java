package com.backroomsmod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(BackroomsMod.MODID)
public class BackroomsMod {
    public static final String MODID = "backroomsmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BackroomsMod() {
        LOGGER.info("Backrooms mod loaded");
    }
}
