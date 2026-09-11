package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamAirIntakeHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamExhaustHatchMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamSupplyHatchPartMachine;
import com.hoshino.gregsteamexpansion.registry.GSEPatternBufferCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared, runtime-only view of the functional parts in a steam multiblock.
 *
 * <p>The collector owns classification and stable ordering. Engine families
 * retain their own formation rules through {@link InterfaceRules}; unusual
 * consumers such as the heat-storage furnace can use the categorized lists
 * directly without weakening the common rules used by processors.</p>
 */
public final class SteamPartCollector {

    private final List<SteamSupplyHatchPartMachine> supplyHatches = new ArrayList<>();
    private final List<FluidHatchPartMachine> physicalSteamHatches = new ArrayList<>();
    private final List<IMultiPart> inputParts = new ArrayList<>();
    private final List<ItemBusPartMachine> outputBuses = new ArrayList<>();
    private final List<FluidHatchPartMachine> fluidInputHatches = new ArrayList<>();
    private final List<FluidHatchPartMachine> fluidOutputHatches = new ArrayList<>();
    private final List<FluidHatchPartMachine> steamFluidHatches = new ArrayList<>();
    private final List<FluidHatchPartMachine> meFluidInputHatches = new ArrayList<>();
    private final List<SteamExhaustHatchMachine> exhaustHatches = new ArrayList<>();
    private final List<SteamAirIntakeHatchPartMachine> airIntakeHatches = new ArrayList<>();
    private final List<HandlerBinding> recipeHandlers = new ArrayList<>();

    public void collect(MultiblockControllerMachine controller) {
        clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO> ioMap = controller.getMultiblockState().getMatchContext()
                .getOrCreate("ioMap", it.unimi.dsi.fastutil.longs.Long2ObjectMaps::emptyMap);
        for (IMultiPart part : controller.getParts()) {
            IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
            if (io == IO.NONE) {
                continue;
            }
            boolean patternBuffer = GSEPatternBufferCompat.isPatternBuffer(part);
            if (patternBuffer) {
                io = IO.IN;
            }
            for (RecipeHandlerList handlerList : part.getRecipeHandlers()) {
                if (handlerList.isValid(io)) {
                    recipeHandlers.add(new HandlerBinding(part, handlerList));
                }
            }

            if (part instanceof SteamSupplyHatchPartMachine supplyHatch) {
                supplyHatches.add(supplyHatch);
                physicalSteamHatches.add(supplyHatch);
            } else if (part instanceof SteamHatchPartMachine steamHatch) {
                physicalSteamHatches.add(steamHatch);
            } else if (part instanceof SteamExhaustHatchMachine exhaustHatch) {
                exhaustHatches.add(exhaustHatch);
            } else if (part instanceof SteamAirIntakeHatchPartMachine airIntakeHatch) {
                airIntakeHatches.add(airIntakeHatch);
            } else if (patternBuffer) {
                inputParts.add(part);
            } else if (part instanceof ItemBusPartMachine bus) {
                if (bus.getInventory().getHandlerIO() == IO.OUT) {
                    outputBuses.add(bus);
                } else {
                    inputParts.add(bus);
                }
            } else if (part instanceof SteamFluidHatchPartMachine steamFluidHatch) {
                steamFluidHatches.add(steamFluidHatch);
                addDirectionalFluidHatch(steamFluidHatch);
            } else if (part instanceof FluidHatchPartMachine fluidHatch) {
                if (isMeFluidInputHatch(part)) {
                    meFluidInputHatches.add(fluidHatch);
                }
                addDirectionalFluidHatch(fluidHatch);
            }
        }
        sortParts();
    }

    public void clear() {
        supplyHatches.clear();
        physicalSteamHatches.clear();
        inputParts.clear();
        outputBuses.clear();
        fluidInputHatches.clear();
        fluidOutputHatches.clear();
        steamFluidHatches.clear();
        meFluidInputHatches.clear();
        exhaustHatches.clear();
        airIntakeHatches.clear();
        recipeHandlers.clear();
    }

    private void addDirectionalFluidHatch(FluidHatchPartMachine hatch) {
        if (hatch.tank.handlerIO == IO.OUT) {
            fluidOutputHatches.add(hatch);
        } else {
            fluidInputHatches.add(hatch);
        }
    }

    private void sortParts() {
        supplyHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        physicalSteamHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        inputParts.sort(Comparator.comparing(part -> part.self().getPos()));
        outputBuses.sort(Comparator
                .comparing((ItemBusPartMachine bus) -> !isMePart(bus))
                .thenComparing(bus -> bus.self().getPos()));
        fluidInputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        fluidOutputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        steamFluidHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        meFluidInputHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        exhaustHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
        airIntakeHatches.sort(Comparator.comparing(hatch -> hatch.self().getPos()));
    }

    /** Validates only functional-interface counts; ordinary casings have no quota. */
    public boolean validate(InterfaceRules rules) {
        return within(inputParts.size(), rules.minimumInputParts(), rules.maximumInputParts())
                && outputBuses.size() >= rules.minimumOutputBuses()
                && supplyHatches.size() >= rules.minimumSupplyHatches()
                && (rules.exactExhaustHatches() < 0 || exhaustHatches.size() == rules.exactExhaustHatches())
                && fluidInputHatches.size() >= rules.minimumFluidInputs()
                && fluidOutputHatches.size() >= rules.minimumFluidOutputs()
                && (rules.allowSteamFluidHatches() || steamFluidHatches.isEmpty())
                && within(airIntakeHatches.size(), rules.minimumAirIntakes(), rules.maximumAirIntakes());
    }

    private static boolean within(int value, int minimum, int maximum) {
        return value >= minimum && (maximum < 0 || value <= maximum);
    }

    /** ME parts are detected by definition id so AE2 classes are never loaded. */
    public static boolean isMePart(IMultiPart part) {
        return part.self().getDefinition().getId().getPath().startsWith("me_");
    }

    private static boolean isMeFluidInputHatch(IMultiPart part) {
        String path = part.self().getDefinition().getId().getPath();
        return path.equals("me_input_hatch") || path.equals("me_stocking_input_hatch");
    }

    public List<SteamSupplyHatchPartMachine> supplyHatches() {
        return supplyHatches;
    }

    public List<FluidHatchPartMachine> physicalSteamHatches() {
        return physicalSteamHatches;
    }

    public List<IMultiPart> inputParts() {
        return inputParts;
    }

    public List<ItemBusPartMachine> outputBuses() {
        return outputBuses;
    }

    public List<FluidHatchPartMachine> fluidInputHatches() {
        return fluidInputHatches;
    }

    public List<FluidHatchPartMachine> fluidOutputHatches() {
        return fluidOutputHatches;
    }

    public List<FluidHatchPartMachine> steamFluidHatches() {
        return steamFluidHatches;
    }

    public List<FluidHatchPartMachine> meFluidInputHatches() {
        return meFluidInputHatches;
    }

    public List<SteamExhaustHatchMachine> exhaustHatches() {
        return exhaustHatches;
    }

    public List<SteamAirIntakeHatchPartMachine> airIntakeHatches() {
        return airIntakeHatches;
    }

    public List<HandlerBinding> recipeHandlers() {
        return recipeHandlers;
    }

    public record HandlerBinding(IMultiPart part, RecipeHandlerList handlers) {}

    /** A negative maximum or exact count disables that upper/exact check. */
    public record InterfaceRules(
            int minimumInputParts,
            int maximumInputParts,
            int minimumOutputBuses,
            int minimumSupplyHatches,
            int exactExhaustHatches,
            int minimumFluidInputs,
            int minimumFluidOutputs,
            boolean allowSteamFluidHatches,
            int minimumAirIntakes,
            int maximumAirIntakes) {}
}
