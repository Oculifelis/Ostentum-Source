package com.ostentum.compat.jei;

import com.ostentum.Ostentum;
import com.ostentum.weapon.Hearthblade;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** JEI category for rekindling: the Cold Hearthblade on one kind of lit campfire, and the blade that comes off it. */
final class RekindlingCategory implements IRecipeCategory<RekindlingCategory.Rekindling> {
    /** One campfire and what the Cold Hearthblade comes out as on it. */
    record Rekindling(Item campfire, Item result) {}

    static final IRecipeType<Rekindling> TYPE = IRecipeType.create(Ostentum.MODID, "rekindling", Rekindling.class);
    private static final int WIDTH = 96, HEIGHT = 44, TEXT = 0xFF404040;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    RekindlingCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemLike(Hearthblade.COLD.get());
        this.arrow = gui.getRecipeArrow();
    }

    @Override public IRecipeType<Rekindling> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.ostentum.rekindling"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Rekindling r, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.INPUT, 8, 4).setStandardSlotBackground().add(Hearthblade.COLD.get());
        b.addSlot(RecipeIngredientRole.CRAFTING_STATION, 8, 24).add(r.campfire());
        b.addSlot(RecipeIngredientRole.OUTPUT, 70, 4).setOutputSlotBackground().add(r.result());
    }

    @Override
    public void draw(Rekindling r, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mx, double my) {
        arrow.draw(g, 36, 4);
        Font f = Minecraft.getInstance().font;
        Component time = Component.translatable("jei.ostentum.rekindling.time");
        g.text(f, time, 30, 29, TEXT, false);
    }
}
