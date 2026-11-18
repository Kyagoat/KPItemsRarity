package net.Kyap.ItemsRarity.screen;

import net.Kyap.ItemsRarity.ItemsRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    private ModMenuTypes() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ItemsRarity.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<EnhancedAnvilBlockMenu>> ENHANCED_ANVIL_MENU =
            MENUS.register("enhanced_anvil_menu", () -> IMenuTypeExtension.create(EnhancedAnvilBlockMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
