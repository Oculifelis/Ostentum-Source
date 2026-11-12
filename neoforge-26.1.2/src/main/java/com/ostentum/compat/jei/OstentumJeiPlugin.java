package com.ostentum.compat.jei;

import com.ostentum.Ostentum;
import com.ostentum.weapon.Hearthblade;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Optional JEI integration: the Hearthblade's rekindling. Vanilla's campfire category lists every campfire for every
 * recipe, so the Cold Hearthblade's campfire recipe is hidden there and shown in {@link RekindlingCategory} instead,
 * once per campfire: the campfire gives the Hearthblade, the soul campfire the Soul Hearthblade.
 */
@JeiPlugin
public final class OstentumJeiPlugin implements IModPlugin {
    static final Identifier REKINDLING = Identifier.fromNamespaceAndPath(Ostentum.MODID, "hearthblade_rekindling");

    @Override
    public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath(Ostentum.MODID, "jei"); }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        r.addRecipeCategories(new RekindlingCategory(r.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        r.addRecipes(RekindlingCategory.TYPE, List.of(
            new RekindlingCategory.Rekindling(Items.CAMPFIRE, Hearthblade.HEARTHBLADE.get()),
            new RekindlingCategory.Rekindling(Items.SOUL_CAMPFIRE, Hearthblade.SOUL.get())));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var hidden = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CAMPFIRE_COOKING).includeHidden().get()
            .filter(holder -> holder.id().identifier().equals(REKINDLING)).toList();
        if (!hidden.isEmpty()) runtime.getRecipeManager().hideRecipes(RecipeTypes.CAMPFIRE_COOKING, hidden);
    }
}
