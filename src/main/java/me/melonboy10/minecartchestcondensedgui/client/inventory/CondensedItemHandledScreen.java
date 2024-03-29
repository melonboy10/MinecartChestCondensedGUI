package me.melonboy10.minecartchestcondensedgui.client.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.ItemTags;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class CondensedItemHandledScreen extends HandledScreen<CondensedItemScreenHandler> {
    @Getter private static CondensedItemHandledScreen minecartScreen; // Static variable for class (Singleton)
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Identifier TEXTURE = new Identifier("minecartchestcondensedgui", "textures/gui/container/grid.png");

    enum SortDirection {
        ASCENDING, DESCENDING;

        public SortDirection other() {
            if (this.equals(ASCENDING))
                return DESCENDING;
            else
                return ASCENDING;
        }
    }
    enum SortFilter {
        QUANTITY, ALPHABETICALLY;

        public SortFilter other() {
            if (this.equals(QUANTITY))
                return ALPHABETICALLY;
            else
                return QUANTITY;
        }
    }

    public static SortDirection sortDirection = SortDirection.DESCENDING; // Sorting for the minecart items
    public static SortFilter sortFilter = SortFilter.QUANTITY;

    // int x; (defined in super) 0 is the left most of the background texture
    // int y; (defined in super) 0 is the top most of the background texture

    public static int rowCount; // The amount of rows in the minecart section

    public static List<VirtualItemStack> items = new ArrayList<>(); // the total items contained in the combined minecarts
    public static List<VirtualItemStack> visibleItems = new ArrayList<>(); // the visible items
    
    private float scrollPosition;
    private boolean scrolling = false;
    int rowsScrolled = 0;
    private TextFieldWidget searchBox;
    private ArrayList<SideButtonWidget> buttons = new ArrayList<>();

    private MinecartSlot hoveredSlot;

    /**
     * Static method for creating a new Screen
     * Used instead of a constructor because it needs to create a handler too
     *
     * @return new CondensedItemHandledScreen
     */
    public static CondensedItemHandledScreen create() {
        if (minecartScreen != null) {
            items.clear();
            visibleItems.clear();
            return minecartScreen;
        }
        return new CondensedItemHandledScreen(CondensedItemScreenHandler.create());
    }

    private CondensedItemHandledScreen(CondensedItemScreenHandler handler) {
        super(handler, MinecraftClient.getInstance().player.getInventory(), new LiteralText("Minecarts"));
        minecartScreen = this;
    }

    /**
     * Runs anytime the Screen is updated
     * i.e. resizing / creating
     */
    protected void init() {
        super.init();

        backgroundHeight = 229;
        backgroundWidth = 193;

        rowCount = (height - 220) / 18 + 3;
        handler.init();
        x = (width - backgroundWidth + 17) / 2;
        y = (height - (backgroundHeight - 56 + (rowCount - 3) * 18)) / 2;

        client.keyboard.setRepeatEvents(true);
        searchBox = new TextFieldWidget(textRenderer, x + 82, y + 7, 80, 9, new TranslatableText("itemGroup.search"));
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(false);
        searchBox.setVisible(true);
        searchBox.setEditableColor(16777215);
        searchBox.setZOffset(1000);
        addSelectableChild(searchBox);

        int i = 8;
        buttons.add(addDrawableChild(new SideButtonWidget(x - 18, y + 8, 0,
            button -> {
//                sortMinecarts();
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }, /*"Sort Nearby Minecarts"*/"Refresh Nearby Minecarts", this)));
        buttons.add(addDrawableChild(new SideButtonWidget(x - 18, y + (i += 18), 1, 2,
            button -> {
                sortDirection = sortDirection.other();
                ((SideButtonWidget) button).setToggled(sortDirection.equals(SortDirection.ASCENDING));
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (sortFilter == SortFilter.ALPHABETICALLY) {
                    items.sort(nameComparator);
                    visibleItems.sort(nameComparator);
                } else {
                    items.sort(quantityComparator);
                    visibleItems.sort(quantityComparator);
                }
                search();
            }, "Not sorting up", "Sorting Up", this)));
        buttons.add(addDrawableChild(new SideButtonWidget(x - 18, y + (i += 18), 3, 4,
            button -> {
                sortFilter = sortFilter.other();
                ((SideButtonWidget) button).setToggled(sortFilter.equals(SortFilter.ALPHABETICALLY));
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (sortFilter == SortFilter.ALPHABETICALLY) {
                    items.sort(nameComparator);
                    visibleItems.sort(nameComparator);
                } else {
                    items.sort(quantityComparator);
                    visibleItems.sort(quantityComparator);
                }
                search();
            }, "Filter", "Filtder", this)));
    }

    /**
     * Render the screen in this order
     * > Transparent Black Background > Gui Texture > Scroll Bar > Minecart Item Numbers > super.render(> Player slots > Minecart Slots > Search Bar > Buttons) > Hover Text
     */
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices); // draws rhe transparent black that covers the background
        this.drawGrid(matrices, delta, mouseX, mouseY);
        this.drawScrollBar(matrices, delta, mouseX, mouseY);
        this.drawMinecartItemNumbers(matrices, delta, mouseX, mouseY);
        super.render(matrices, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    /**
     * Draws the background texture to match the rows added
     */
    protected void drawGrid(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int numberOfAddedRows = rowCount - 3;

        // Draws the top portions of the background; Header & first 3 rows
        drawTexture(matrices, x, y, 0, 0, backgroundWidth, backgroundHeight - 150); // Top
        for (int i = 0; i < numberOfAddedRows; i++) {
            // Draws extra rows
            drawTexture(matrices, x, y + 72 + (18 * i), 0, 54, 193, 25); // Row Segment
        }
        // Draws the bottom half; Spacer & player inventory
        drawTexture(matrices, x, y + 78 + numberOfAddedRows * 18, 0, 135, backgroundWidth, backgroundHeight - 135); // Botton

        searchBox.render(matrices, mouseX, mouseY, delta);
    }

    /**
     * Required method for the ScreenHandler
     * @see HandledScreen
     */
    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {}

    /**
     * Draws "Minecarts" and "Inventory" text
     */
    @Override
    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        this.textRenderer.draw(matrices, new TranslatableText("minecartchestcondensedgui.defaultMinecartLabel"), 8, 6, 4210752);
        this.textRenderer.draw(matrices, new TranslatableText("container.inventory"), 8, rowCount * 18 + 24, 4210752);
    }

    /**
     * Draws hovertext for inventory items & buttons
     */
    protected void drawMouseoverTooltip(MatrixStack matrices, int x, int y) {
        super.drawMouseoverTooltip(matrices, x, y);
        for (SideButtonWidget button : buttons) {
            if (button.isHovered()) {
                renderTooltip(matrices, new LiteralText(button.toggled && button.toggledTooltip != null ? button.toggledTooltip : button.tooltip), x, y);
            }
        }
    }

    /**
     * Draws scroll bar to position based on rowsScrolled & scrollPosition
     */
    private void drawScrollBar(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        int scrollBarX = x + 174;
        int scrollBarY = y + 20 + (int) ((float)((rowCount * 18) - 17) * this.scrollPosition);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE
        );
        if (rowCount >= Math.ceil(visibleItems.size()/9F)) {
            this.drawTexture(matrices, scrollBarX, scrollBarY, 244, 0, 12, 15);
        } else {
            this.drawTexture(matrices, scrollBarX, scrollBarY, 232, 0, 12, 15);
        }
    }

    /*@Deprecated
    private void drawMinecartItems(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        hoveredSlot = null;
        for (MinecartSlot slot : handler.minecartSlots) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            this.drawMinecartSlot(matrices, slot);

            if (this.isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
//                this.focusedSlot = slot;
                hoveredSlot = slot;
                System.out.println(getZOffset());
                drawSlotHighlight(matrices, x + slot.x, y + slot.y, 1000);
            }
        }
        for (int i = 0; i < rowCount * 9; i++) {
            int slotX = x + 8 + 18 * (i % 9);
            int slotY = y + 20 + 18 * (i / 9);
            if ((i + rowsScrolled*9) < searchedVisibleItems.size()) {
                ItemStack inventoryItem = searchedVisibleItems.get(i + rowsScrolled*9).visualItemStack;
                itemRenderer.renderInGui(inventoryItem, slotX, slotY);
                itemRenderer.renderGuiItemOverlay(this.textRenderer, inventoryItem, slotX, slotY, "");
                String amountString = abbreviateAmount(searchedVisibleItems.get(i + rowsScrolled*9).amount);
                MatrixStack textMatrixStack = new MatrixStack();
                textMatrixStack.scale(0.5F, 0.5F, 1);
                textMatrixStack.translate(0, 0, itemRenderer.zOffset + 200.0F);
                if (searchedVisibleItems.get(i + rowsScrolled*9).amount == 0) {
                    textRenderer.drawWithShadow(textMatrixStack, amountString, slotX * 2 + 31 - textRenderer.getWidth(amountString), slotY * 2 + 23, Formatting.RED.getColorValue());
                } else {
                    textRenderer.drawWithShadow(textMatrixStack, amountString, slotX * 2 + 31 - textRenderer.getWidth(amountString), slotY * 2 + 23, Formatting.WHITE.getColorValue());
                }
            }
            if (mouseX >= slotX - 1 && mouseX <= slotX + 16 && mouseY >= slotY - 1 && mouseY <= slotY + 16) {
                fillGradient(matrices, slotX, slotY, slotX + 16, slotY + 16, -2130706433, -2130706433, 200);
                hoveredSlot = i;
                hoveredInventory = HoveredInventory.MINECARTS;
            }
        }
    }*/

    /**
     * Draws the numbers for the minecart items.
     * Special rendering because the numbers are smaller
     */
    private void drawMinecartItemNumbers(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        for (int i = 0; i < rowCount * 9; i++) {
            int slotX = this.x + 8 + 18 * (i % 9);
            int slotY = this.y + 20 + 18 * (i / 9);
            if ((i + rowsScrolled*9) < visibleItems.size()) {
                String amountString = abbreviateAmount(visibleItems.get(i + rowsScrolled*9).getAmount());
                MatrixStack textMatrixStack = new MatrixStack();
                textMatrixStack.scale(0.5F, 0.5F, 1);
                textMatrixStack.translate(0, 0, 300.0F);
                if (visibleItems.get(i + rowsScrolled*9).getAmount() == 0) {
                    textRenderer.drawWithShadow(textMatrixStack, amountString, slotX * 2 + 31 - textRenderer.getWidth(amountString), slotY * 2 + 23, Formatting.RED.getColorValue());
                } else {
                    textRenderer.drawWithShadow(textMatrixStack, amountString, slotX * 2 + 31 - textRenderer.getWidth(amountString), slotY * 2 + 23, Formatting.WHITE.getColorValue());
                }
            }
        }
    }

    /**
     * Draws a slot with small text
     */
    @Deprecated
    private void drawMinecartSlot(MatrixStack matrices, MinecartSlot slot) {
        VirtualItemStack itemStack = slot.getVirtualStack();
        if (itemStack == null) return;

        this.setZOffset(100);
        this.itemRenderer.zOffset = 100.0F;

        RenderSystem.enableDepthTest();
        this.itemRenderer.renderInGuiWithOverrides(client.player, itemStack.visualItemStack, x + slot.x, y + slot.y, slot.x + slot.y * this.backgroundWidth);
        this.itemRenderer.renderGuiItemOverlay(this.textRenderer, itemStack.visualItemStack, x + slot.x, y + slot.y, " ");

        String amountString = abbreviateAmount(itemStack.getAmount());
        MatrixStack textMatrixStack = new MatrixStack();
        textMatrixStack.scale(0.5F, 0.5F, 1);
        textMatrixStack.translate(0, 0, itemRenderer.zOffset + 200.0F);
        textRenderer.drawWithShadow(textMatrixStack, amountString, (x + slot.x) * 2 + 31 - textRenderer.getWidth(amountString), (y + slot.y) * 2 + 23, itemStack.getAmount() == 0 ? Formatting.RED.getColorValue() : Formatting.WHITE.getColorValue());

        this.itemRenderer.zOffset = 0.0F;
        this.setZOffset(0);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= (double)x1 && mouseY >= (double)y1 && mouseX < (double)x2 && mouseY < (double)y2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY, x + 174, y + 20, x + 186, y + 18 + rowCount * 18)) { // is mouse in scroll bar
            this.scrolling = shouldShowScrollbar();
            return true;
        } /*else if (hoveredSlot != null && hoveredSlot.hasStack()) {
            handler.slotClick(hoveredSlot, button);
            return true;
        }*/
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.scrolling) {
            int y1 = this.y + 20;
            int y2 = y1 + rowCount * 18;
            this.scrollPosition = ((float)mouseY - (float)y1 - 7.5F) / ((float)(y2 - y1) - 15.0F);
            this.scrollPosition = MathHelper.clamp(this.scrollPosition, 0.0F, 1.0F);
            scrollItems(this.scrollPosition);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (shouldShowScrollbar()) {
            int i = (items.size() + 9 - 1) / 9 - 5;
            this.scrollPosition = (float)((double)this.scrollPosition - amount / (double)i);
            this.scrollPosition = MathHelper.clamp(this.scrollPosition, 0.0F, 1.0F);
            scrollItems(this.scrollPosition);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
//        checkItems(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /*public void checkItems(double mouseX, double mouseY, int button) {
        if (hoveredSlot > -1) {
            if (isMouseOver(mouseX, mouseY, x, y, x + 172, y + 24 + rowCount * 18)) { // Handle Minecart Inventory
                if (hasShiftDown()) {
                    if (button == 0) {
                        //Quick Move hovered Item stack
                        client.player.sendMessage(new LiteralText("quick move"), false);
                        if (searchedVisibleItems.size() > hoveredSlot + rowsScrolled * 9 && searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount > 0) {
                            int decreasingItemIndex = getVirtualItemStackForItem(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack);
                            VirtualItemStack decreasingItem = visibleItems.get(decreasingItemIndex);
                            int itemsToTransfer = decreasingItem.visualItemStack.getMaxCount();
                            for (int i = 0; i < 36; i++) {
                                int playerInventorySlot = 35 - i;
                                if (visiblePlayerItems.get(playerInventorySlot).isEmpty()) {
                                    if (decreasingItem.amount > decreasingItem.visualItemStack.getMaxCount()) {
                                        ItemStack newItemStack = decreasingItem.visualItemStack.copy();
                                        newItemStack.setCount(itemsToTransfer);
                                        decreasingItem.amount = decreasingItem.amount - decreasingItem.visualItemStack.getMaxCount();
                                        visiblePlayerItems.set(playerInventorySlot, newItemStack);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = decreasingItem.amount;
                                        break;
                                    } else {
                                        ItemStack newItemStack = decreasingItem.visualItemStack.copy();
                                        newItemStack.setCount(decreasingItem.amount);
                                        System.out.println(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = 0;
                                        System.out.println(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount);
                                        visibleItems.remove(decreasingItemIndex);
                                        visiblePlayerItems.set(playerInventorySlot, newItemStack);
                                        break;
                                    }
                                } else if (ItemStack.canCombine(decreasingItem.visualItemStack, visiblePlayerItems.get(playerInventorySlot))) {
                                    if (decreasingItem.amount > Math.min(itemsToTransfer, visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount())) {
                                        decreasingItem.amount = decreasingItem.amount - (visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount());
                                        itemsToTransfer = itemsToTransfer - Math.min(itemsToTransfer, visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount());
                                        visiblePlayerItems.get(playerInventorySlot).setCount(visiblePlayerItems.get(playerInventorySlot).getMaxCount());
                                        System.out.println(itemsToTransfer);
                                    } else {
                                        visiblePlayerItems.get(playerInventorySlot).setCount(visiblePlayerItems.get(playerInventorySlot).getCount() + decreasingItem.amount);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = 0;
                                        visibleItems.remove(decreasingItemIndex);
                                        break;
                                    }
                                }
                                if (itemsToTransfer == 0) {
                                    searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = decreasingItem.amount;
                                    break;
                                }
                            }
                        }
                    } else if (button == 1) {
                        //Quick Move hovered Item stack
                        client.player.sendMessage(new LiteralText("quick move"), false);
                        if (searchedVisibleItems.size() > hoveredSlot + rowsScrolled * 9 && searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount > 0) {
                            int decreasingItemIndex = getVirtualItemStackForItem(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack);
                            VirtualItemStack decreasingItem = visibleItems.get(decreasingItemIndex);
                            int itemsToTransfer = decreasingItem.visualItemStack.getMaxCount();
                            for (int i = 0; i < 36; i++) {
                                int playerInventorySlot = 35 - i;
                                if (visiblePlayerItems.get(playerInventorySlot).isEmpty()) {
                                    if (decreasingItem.amount > decreasingItem.visualItemStack.getMaxCount()) {
                                        ItemStack newItemStack = decreasingItem.visualItemStack.copy();
                                        newItemStack.setCount(itemsToTransfer);
                                        decreasingItem.amount = decreasingItem.amount - decreasingItem.visualItemStack.getMaxCount();
                                        visiblePlayerItems.set(playerInventorySlot, newItemStack);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = decreasingItem.amount;
                                        break;
                                    } else {
                                        ItemStack newItemStack = decreasingItem.visualItemStack.copy();
                                        newItemStack.setCount(decreasingItem.amount);
                                        System.out.println(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = 0;
                                        System.out.println(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount);
                                        visibleItems.remove(decreasingItemIndex);
                                        visiblePlayerItems.set(playerInventorySlot, newItemStack);
                                        break;
                                    }
                                } else if (ItemStack.canCombine(decreasingItem.visualItemStack, visiblePlayerItems.get(playerInventorySlot))) {
                                    if (decreasingItem.amount > Math.min(itemsToTransfer, visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount())) {
                                        decreasingItem.amount = decreasingItem.amount - (visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount());
                                        itemsToTransfer = itemsToTransfer - Math.min(itemsToTransfer, visiblePlayerItems.get(playerInventorySlot).getMaxCount() - visiblePlayerItems.get(playerInventorySlot).getCount());
                                        visiblePlayerItems.get(playerInventorySlot).setCount(visiblePlayerItems.get(playerInventorySlot).getMaxCount());
                                        System.out.println(itemsToTransfer);
                                    } else {
                                        visiblePlayerItems.get(playerInventorySlot).setCount(visiblePlayerItems.get(playerInventorySlot).getCount() + decreasingItem.amount);
                                        searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = 0;
                                        visibleItems.remove(decreasingItemIndex);
                                        break;
                                    }
                                }
                                if (itemsToTransfer == 0) {
                                    searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).amount = decreasingItem.amount;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    if (mouseStack.isEmpty()) {
                        if (button == 0) {
                            //Pickup all
                            client.player.sendMessage(new LiteralText("pickup all"), false);
                            if (searchedVisibleItems.size() > hoveredSlot + rowsScrolled * 9) {
                                int decreasingItemIndex = getVirtualItemStackForItem(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack);
                                VirtualItemStack decreasingItem = visibleItems.get(decreasingItemIndex);
                                if (decreasingItem.amount > decreasingItem.visualItemStack.getMaxCount()) {
                                    ItemStack newMouseStack = decreasingItem.visualItemStack.copy();
                                    newMouseStack.setCount(newMouseStack.getMaxCount());
                                    mouseStack = newMouseStack;
                                    decreasingItem.amount = decreasingItem.amount - decreasingItem.visualItemStack.getMaxCount();
                                } else {
                                    ItemStack newMouseStack = searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack.copy();
                                    newMouseStack.setCount(decreasingItem.amount);
                                    mouseStack = newMouseStack;
                                    visibleItems.remove(decreasingItemIndex);
                                }
                                if (sortFilter == SortFilter.ALPHABETICALLY) {
                                    visibleItems.sort(nameComparator);
                                } else {
                                    visibleItems.sort(quantityComparator);
                                }
                                search();
                            }
                        } else if (button == 1) {
                            //pickup half
                            client.player.sendMessage(new LiteralText("pickup half"), false);
                            if (searchedVisibleItems.size() > hoveredSlot + rowsScrolled * 9) {
                                int decreasingItemIndex = getVirtualItemStackForItem(searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack);
                                VirtualItemStack decreasingItem = visibleItems.get(decreasingItemIndex);
                                if (decreasingItem.amount > decreasingItem.visualItemStack.getMaxCount()) {
                                    ItemStack newMouseStack = decreasingItem.visualItemStack.copy();
                                    newMouseStack.setCount((int) Math.ceil(newMouseStack.getMaxCount() / 2F));
                                    mouseStack = newMouseStack;
                                    decreasingItem.amount = decreasingItem.amount - decreasingItem.visualItemStack.getMaxCount() / 2;
                                } else {
                                    ItemStack newMouseStack = searchedVisibleItems.get(hoveredSlot + rowsScrolled * 9).visualItemStack.copy();
                                    newMouseStack.setCount((int) Math.ceil(decreasingItem.amount / 2F));
                                    mouseStack = newMouseStack;
                                    if (decreasingItem.amount > 1) {
                                        decreasingItem.amount = decreasingItem.amount / 2;
                                    } else {
                                        visibleItems.remove(decreasingItemIndex);
                                    }
                                }
                                if (sortFilter == SortFilter.ALPHABETICALLY) {
                                    visibleItems.sort(nameComparator);
                                } else {
                                    visibleItems.sort(quantityComparator);
                                }
                                search();
                            }
                        }
                    } else {
                        if (button == 0) {
                            if (isDoubleClicking) {
                                //move all towards
                                client.player.sendMessage(new LiteralText("move all towards"), false);
                                int increasingItemIndex = getVirtualItemStackForItem(mouseStack);
                                if (increasingItemIndex == -1) {
                                    visibleItems.add(new VirtualItemStack(mouseStack.copy(), 0, new ArrayList<VirtualItemStack.ItemMinecart>()));
                                    increasingItemIndex = visibleItems.size() - 1;
                                }
                                VirtualItemStack increasingItem = visibleItems.get(increasingItemIndex);
                                for (int i = 0; i < 36; i++) {
                                    if (ItemStack.canCombine(mouseStack, visiblePlayerItems.get(i))) {
                                        if (mouseStack.getMaxCount() <= mouseStack.getCount() + visiblePlayerItems.get(i).getCount()) {
                                            increasingItem.amount = increasingItem.amount + visiblePlayerItems.get(i).getCount() - (mouseStack.getMaxCount() - mouseStack.getCount());
                                            mouseStack.setCount(mouseStack.getMaxCount());
                                            visiblePlayerItems.set(i, ItemStack.EMPTY);
                                        }
                                        mouseStack.setCount(mouseStack.getCount() + visiblePlayerItems.get(i).getCount());
                                        visiblePlayerItems.set(i, ItemStack.EMPTY);
                                    }
                                }
                                if (increasingItem.amount == 0) {
                                    visibleItems.remove(increasingItemIndex);
                                } else {
                                    if (sortFilter == SortFilter.ALPHABETICALLY) {
                                        visibleItems.sort(nameComparator);
                                    } else {
                                        visibleItems.sort(quantityComparator);
                                    }
                                    search();
                                }
                            } else {
                                //place all
                                client.player.sendMessage(new LiteralText("place all"), false);
                                int increasingItemIndex = getVirtualItemStackForItem(mouseStack);
                                if (increasingItemIndex == -1) {
                                    visibleItems.add(new VirtualItemStack(mouseStack.copy(), 0, new ArrayList<VirtualItemStack.ItemMinecart>()));
                                    increasingItemIndex = visibleItems.size() - 1;
                                }
                                VirtualItemStack increasingItem = visibleItems.get(increasingItemIndex);
                                increasingItem.amount = increasingItem.amount + mouseStack.getCount();
                                mouseStack = ItemStack.EMPTY;
                                if (sortFilter == SortFilter.ALPHABETICALLY) {
                                    visibleItems.sort(nameComparator);
                                } else {
                                    visibleItems.sort(quantityComparator);
                                }
                                search();
                            }
                        } else if (button == 1) {
                            //place one
                            client.player.sendMessage(new LiteralText("place one"), false);
                            int increasingItemIndex = getVirtualItemStackForItem(mouseStack);
                            if (increasingItemIndex == -1) {
                                visibleItems.add(new VirtualItemStack(mouseStack.copy(), 0, new ArrayList<VirtualItemStack.ItemMinecart>()));
                                increasingItemIndex = visibleItems.size() - 1;
                            }
                            VirtualItemStack increasingItem = visibleItems.get(increasingItemIndex);
                            increasingItem.amount = increasingItem.amount + 1;
                            mouseStack.decrement(1);
                            if (mouseStack.getCount() == 0) {
                                mouseStack = ItemStack.EMPTY;
                            }
                            if (sortFilter == SortFilter.ALPHABETICALLY) {
                                visibleItems.sort(nameComparator);
                            } else {
                                visibleItems.sort(quantityComparator);
                            }
                            search();
                        }
                    }
                }
            }
        }
    }*/

    @Override
    public boolean charTyped(char chr, int modifiers) {
        String string = this.searchBox.getText();
        if(this.searchBox.charTyped(chr, modifiers)) {
            if (!Objects.equals(string, this.searchBox.getText())) {
                search();
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        String string = this.searchBox.getText();
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            if (!Objects.equals(string, this.searchBox.getText())) {
                this.search();
            }

            return true;
        } else {
            return this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != GLFW.GLFW_KEY_ESCAPE || super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (sortFilter == SortFilter.ALPHABETICALLY) {
                visibleItems.sort(nameComparator);
            } else {
                visibleItems.sort(quantityComparator);
            }
            search();
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private String abbreviateAmount(int amount) {
        if (amount > 999999999) {
            return Math.round(amount/100000000F)/10F + "B";
        } else if (amount > 99999999) {
            return Math.round(amount/1000000F) + "M";
        } else if (amount > 999999) {
            return Math.round(amount/100000F)/10F + "M";
        } else if (amount > 99999) {
            return Math.round(amount/1000F)+ "K";
        } else if (amount > 9999) {
            return Math.round(amount/100F)/10F + "K";
        } else {
            return amount == 1 ? "" : Integer.toString(amount);
        }
    }

    /**
     * Searches the items by checking all items in "items" that matches the search.
     * Then sets the contents of "visibleItems" to the search result.
     */
    private void search() { // this is only displaying the first item no clue why
        String searchText = searchBox.getText();
        if (searchText.isEmpty()) {
            visibleItems.clear();
            visibleItems.addAll(items);
        } else {
            ArrayList<VirtualItemStack> searchedItems = new ArrayList<>();
            if (searchText.startsWith("#")) {
                searchText = searchText.substring(1);

                for (VirtualItemStack virtualItemStack : items) {
                    Collection<Identifier> itemTags = ItemTags.getTagGroup().getTagsFor(virtualItemStack.visualItemStack.getItem());
                    for (Identifier tag : itemTags) {
                        if (tag.getPath().contains(searchText)) {
                            searchedItems.add(virtualItemStack);
                            break;
                        }
                    }
                }
            } else {
                for (VirtualItemStack virtualItemStack : items) {
                    ItemStack visualItemStack = virtualItemStack.visualItemStack;
                    if (visualItemStack.getName().getString().contains(searchText) ||
                        Registry.ITEM.getId(visualItemStack.getItem()).toString().contains(searchText)) {
                        searchedItems.add(virtualItemStack);
                    }
                }
            }
            visibleItems.clear();
            visibleItems.addAll(searchedItems);
        }

        scrollItems(0);
    }

    /**
     * Increments the indexes of the Minecart Slots to change which items are visible.
     */
    public void scrollItems(float position) {
        scrollPosition = position;
        rowsScrolled = Math.round(position * (float) (Math.ceil(visibleItems.size() / 9F) - rowCount));
        DefaultedList<MinecartSlot> minecartSlots = handler.minecartSlots;
        for (int i = 0; i < minecartSlots.size(); i++) {
            MinecartSlot minecartSlot = minecartSlots.get(i);
            minecartSlot.index = i + rowsScrolled * 9;
        }
    }

    // ! THIS IS INCORRECT ! i think
    public boolean shouldShowScrollbar() {
        return items.size() > 45;
    }

    private int getVirtualItemStackForItem(ItemStack itemstack) {
        for (int i = 0; i < visibleItems.size(); i++) {
            if (ItemStack.canCombine(visibleItems.get(i).visualItemStack, itemstack)) {
                return i;
            }
        }
        return -1;
    }

    public void addItem(ChestMinecartEntity minecart, ItemStack itemstack, int slot) {
        boolean newItem = true;
        for (VirtualItemStack virtualItemStack : items) {
            if (ItemStack.canCombine(virtualItemStack.visualItemStack, itemstack)) {
                newItem = false;
                virtualItemStack.addItem(minecart, slot, itemstack);
            }
        }
        if (newItem) {
            items.add(new VirtualItemStack(itemstack, minecart, slot, itemstack.getCount()));
        }

        if (sortFilter == SortFilter.ALPHABETICALLY) {
            items.sort(nameComparator);
        } else {
            items.sort(quantityComparator);
        }

        visibleItems.clear();
        visibleItems.addAll(items);
    }

    private final Comparator<VirtualItemStack> quantityComparator = (virtualItemStack1, virtualItemStack2) -> {
        int difference = virtualItemStack2.getAmount() - virtualItemStack1.getAmount();
        if (difference > 0) {
            return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
        } else if (difference < 0) {
            return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
        } else {
            difference = virtualItemStack1.visualItemStack.getName().getString().compareToIgnoreCase(virtualItemStack2.visualItemStack.getName().getString());
            if (difference > 0) {
                return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
            } else if (difference < 0) {
                return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
            } else {
                assert virtualItemStack1.visualItemStack.getNbt() != null;
                assert virtualItemStack2.visualItemStack.getNbt() != null;
                difference = virtualItemStack1.visualItemStack.getNbt().toString().compareToIgnoreCase(virtualItemStack2.visualItemStack.getNbt().toString());
                if (difference > 0) {
                    return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
                } else if (difference < 0) {
                    return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
                } else {
                    return 0;
                }
            }
        }
    };

    private final Comparator<VirtualItemStack> nameComparator = (virtualItemStack1, virtualItemStack2) -> {
        int difference = virtualItemStack1.visualItemStack.getName().getString().compareToIgnoreCase(virtualItemStack2.visualItemStack.getName().getString());
        if (difference > 0) {
            return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
        } else if (difference < 0) {
            return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
        } else {
            difference = virtualItemStack2.getAmount() - virtualItemStack1.getAmount();
            if (difference > 0) {
                return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
            } else if (difference < 0) {
                return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
            } else {
                assert virtualItemStack1.visualItemStack.getNbt() != null;
                assert virtualItemStack2.visualItemStack.getNbt() != null;
                difference = virtualItemStack1.visualItemStack.getNbt().toString().compareToIgnoreCase(virtualItemStack2.visualItemStack.getNbt().toString());
                if (difference > 0) {
                    return sortDirection.equals(SortDirection.DESCENDING) ? 1 : -1;
                } else if (difference < 0) {
                    return sortDirection.equals(SortDirection.DESCENDING) ? -1 : 1;
                } else {
                    return 0;
                }
            }
        }
    };
}
