package com.qouteall.imm_ptl_peripheral;

import com.google.common.collect.Lists;
import com.qouteall.hiding_in_the_bushes.O_O;
import com.qouteall.imm_ptl_peripheral.alternate_dimension.AlternateDimensions;
import com.qouteall.imm_ptl_peripheral.alternate_dimension.FormulaGenerator;
import com.qouteall.imm_ptl_peripheral.altius_world.AltiusGameRule;
import com.qouteall.imm_ptl_peripheral.altius_world.AltiusManagement;
import com.qouteall.imm_ptl_peripheral.guide.IPGuide;
import com.qouteall.imm_ptl_peripheral.portal_generation.IntrinsicPortalGeneration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;

public class PeripheralModMain {
    
    public static Block portalHelperBlock;
    public static BlockItem portalHelperBlockItem;
    
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        IPGuide.initClient();
    }
    
    public static void init() {
        FormulaGenerator.init();
        
        IntrinsicPortalGeneration.init();
        
        AltiusGameRule.init();
        AltiusManagement.init();
        
        AlternateDimensions.init();
    }
    
    public static void registerCommandStickTypes() {
        registerPortalSubCommandStick("delete_portal");
        registerPortalSubCommandStick("remove_connected_portals");
        registerPortalSubCommandStick("eradicate_portal_cluster");
        registerPortalSubCommandStick("complete_bi_way_bi_faced_portal");
        registerPortalSubCommandStick("complete_bi_way_portal");
        registerPortalSubCommandStick("move_portal_front", "move_portal 0.5");
        registerPortalSubCommandStick("move_portal_back", "move_portal -0.5");
        registerPortalSubCommandStick(
            "move_portal_destination_front", "move_portal_destination 0.5"
        );
        registerPortalSubCommandStick(
            "move_portal_destination_back", "move_portal_destination -0.5"
        );
        registerPortalSubCommandStick(
            "rotate_x", "rotate_portal_rotation_along x 15"
        );
        registerPortalSubCommandStick(
            "rotate_y", "rotate_portal_rotation_along y 15"
        );
        registerPortalSubCommandStick(
            "rotate_z", "rotate_portal_rotation_along z 15"
        );
        registerPortalSubCommandStick(
            "make_unbreakable", "set_portal_nbt {unbreakable:true}"
        );
        registerPortalSubCommandStick(
            "make_fuse_view", "set_portal_nbt {fuseView:true}"
        );
        registerPortalSubCommandStick(
            "enable_isometric", "debug isometric_enable 100"
        );
        registerPortalSubCommandStick(
            "disable_isometric", "debug isometric_disable"
        );
        if (O_O.getIsPehkuiPresent()) {
            //PehkuiInterface.isPehkuiPresent may not be initialized in time
            CommandStickItem.registerType("imm_ptl:reset_scale", new CommandStickItem.Data(
                "/scale set pehkui:base 1",
                "imm_ptl.command.reset_scale",
                Lists.newArrayList("imm_ptl.command_desc.reset_scale")
            ));
            CommandStickItem.registerType("imm_ptl:long_reach", new CommandStickItem.Data(
                "/scale set pehkui:reach 5",
                "imm_ptl.command.long_reach",
                Lists.newArrayList("imm_ptl.command_desc.long_reach")
            ));
        }
        registerPortalSubCommandStick(
            "goback"
        );
        registerPortalSubCommandStick(
            "show_wiki", "wiki"
        );
    }
    
    private static void registerPortalSubCommandStick(String name) {
        registerPortalSubCommandStick(name, name);
    }
    
    private static void registerPortalSubCommandStick(String name, String subCommand) {
        CommandStickItem.registerType("imm_ptl:" + name, new CommandStickItem.Data(
            "/portal " + subCommand,
            "imm_ptl.command." + name,
            Lists.newArrayList("imm_ptl.command_desc." + name)
        ));
    }
}
