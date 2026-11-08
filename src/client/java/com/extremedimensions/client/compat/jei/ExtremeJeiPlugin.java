package com.extremedimensions.client.compat.jei;

import java.util.List;

import com.extremedimensions.ExtremeDimensions;
import com.extremedimensions.client.compat.JeiStatus;
import com.extremedimensions.client.compat.ViewerContent;
import com.extremedimensions.client.compat.ViewerContent.Fuel;
import com.extremedimensions.client.compat.ViewerContent.Spin;
import com.extremedimensions.client.compat.ViewerContent.Spoils;
import com.extremedimensions.client.compat.ViewerContent.Trade;
import com.extremedimensions.registry.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ITextWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * The mod in JEI (3.35): its recipes JEI finds by itself; this adds what no recipe says. Four categories (an anchor's
 * fuel, the young's barter and gifts, the Spindle, what each creature and find leaves) and a page for every item no
 * recipe makes. All of it comes from {@link ViewerContent}. Loaded by JEI through the {@code jei_mod_plugin} entrypoint,
 * so it is never touched without JEI.
 */
public class ExtremeJeiPlugin implements IModPlugin {
	public static final IRecipeType<Fuel> FUEL = IRecipeType.create(ExtremeDimensions.MOD_ID, "anchor_fuel", Fuel.class);
	public static final IRecipeType<Trade> BARTER = IRecipeType.create(ExtremeDimensions.MOD_ID, "hollowling_barter", Trade.class);
	public static final IRecipeType<Spin> SPINDLE = IRecipeType.create(ExtremeDimensions.MOD_ID, "spindle", Spin.class);
	public static final IRecipeType<Spoils> SPOILS = IRecipeType.create(ExtremeDimensions.MOD_ID, "spoils", Spoils.class);

	/** Text on JEI's light page: dark grey, without a shadow, as JEI's own. */
	private static final int TEXT = 0xFF404040;

	@Override
	public Identifier getPluginUid() {
		return Identifier.fromNamespaceAndPath(ExtremeDimensions.MOD_ID, "jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new FuelCategory(gui), new BarterCategory(gui), new SpindleCategory(gui), new SpoilsCategory(gui));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<Fuel> fuels = ViewerContent.fuels();
		registration.addRecipes(FUEL, fuels);
		List<Trade> trades = ViewerContent.trades();
		registration.addRecipes(BARTER, trades);
		registration.addRecipes(SPINDLE, List.of(ViewerContent.spin()));
		List<Spoils> spoils = ViewerContent.spoils();
		registration.addRecipes(SPOILS, spoils);
		int infos = 0;
		for (ViewerContent.Info info : ViewerContent.infos()) {
			registration.addItemStackInfo(info.stack(), info.text());
			infos++;
		}
		JeiStatus.fuels = fuels.size();
		JeiStatus.trades = trades.size();
		JeiStatus.spins = 1;
		JeiStatus.spoils = spoils.size();
		JeiStatus.infos = infos;
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(FUEL, Items.LODESTONE);
		registration.addCraftingStation(SPINDLE, ModItems.SPINDLE);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		JeiStatus.show = () -> runtime.getRecipesGui().showTypes(List.<IRecipeType<?>>of(FUEL, BARTER, SPINDLE, SPOILS));
	}

	@Override
	public void onRuntimeUnavailable() {
		JeiStatus.show = null;
	}

	/** A line of text on a recipe, as JEI writes its own. */
	private static ITextWidget text(IRecipeExtrasBuilder extras, Component text, int x, int y, int width) {
		return extras.addText(text, width, 10).setPosition(x, y).setColor(TEXT).setShadow(false);
	}

	/** A fuel: the item, and what it gives a Nether or an End anchor. */
	private static final class FuelCategory extends AbstractRecipeCategory<Fuel> {
		FuelCategory(IGuiHelper gui) {
			super(FUEL, Component.translatable("viewer.extremedimensions.category.fuel"), gui.createDrawableItemLike(Items.LODESTONE), 150, 20);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Fuel recipe, IFocusGroup focuses) {
			builder.addInputSlot(1, 2).setStandardSlotBackground().addItemStack(recipe.fuel());
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder extras, Fuel recipe, IFocusGroup focuses) {
			text(extras, recipe.line(), 24, 6, 126).setTooltip(Component.translatable("viewer.extremedimensions.fuel.desc"));
		}
	}

	/** A ware of the young (its price, what it gives, when they offer it) or a gift they take. */
	private static final class BarterCategory extends AbstractRecipeCategory<Trade> {
		BarterCategory(IGuiHelper gui) {
			super(BARTER, Component.translatable("viewer.extremedimensions.category.barter"), gui.createDrawableItemLike(ModItems.TETHER_FRAGMENT), 150, 36);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Trade recipe, IFocusGroup focuses) {
			builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(recipe.want());
			if (!recipe.gift()) {
				builder.addOutputSlot(61, 1).setStandardSlotBackground().addItemStacks(recipe.give());
			}
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder extras, Trade recipe, IFocusGroup focuses) {
			if (!recipe.gift()) {
				extras.addRecipeArrow().setPosition(28, 1);
			}
			text(extras, recipe.when(), 0, 24, 150).setTooltip(recipe.more());
		}
	}

	/** The Spindle: fragments and silk, turned into a Tether Cord. */
	private static final class SpindleCategory extends AbstractRecipeCategory<Spin> {
		SpindleCategory(IGuiHelper gui) {
			super(SPINDLE, Component.translatable("viewer.extremedimensions.category.spindle"), gui.createDrawableItemLike(ModItems.SPINDLE), 150, 36);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Spin recipe, IFocusGroup focuses) {
			for (int i = 0; i < recipe.inputs().size(); i++) {
				builder.addInputSlot(1 + i * 18, 1).setStandardSlotBackground().addItemStack(recipe.inputs().get(i));
			}
			builder.addOutputSlot(79, 1).setStandardSlotBackground().addItemStack(recipe.cord());
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder extras, Spin recipe, IFocusGroup focuses) {
			extras.addRecipeArrow().setPosition(46, 1);
			text(extras, Component.translatable("viewer.extremedimensions.spindle.turns", recipe.turns()), 0, 24, 150)
					.setTooltip(Component.translatable("viewer.extremedimensions.spindle.desc"));
		}
	}

	/** What a creature or a find leaves: its name, and the things, nine to a row. */
	private static final class SpoilsCategory extends AbstractRecipeCategory<Spoils> {
		SpoilsCategory(IGuiHelper gui) {
			super(SPOILS, Component.translatable("viewer.extremedimensions.category.spoils"), gui.createDrawableItemLike(Items.CHEST), 162, 50);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Spoils recipe, IFocusGroup focuses) {
			for (int i = 0; i < recipe.slots().size() && i < 18; i++) {
				builder.addOutputSlot(1 + (i % 9) * 18, 13 + (i / 9) * 18).setStandardSlotBackground().addItemStacks(recipe.slots().get(i));
			}
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder extras, Spoils recipe, IFocusGroup focuses) {
			text(extras, recipe.source(), 0, 1, 162);
		}
	}
}
