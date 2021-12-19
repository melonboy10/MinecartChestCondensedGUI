package me.melonboy10.minecartchestcondensedgui.client.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;
import org.apache.logging.log4j.core.layout.CsvLogEventLayout;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.CallbackI;

import java.util.*;

public class CondensedItemScreen extends Screen {
    private static final Identifier GRID = new Identifier("minecartchestcondensedgui", "textures/gui/container/grid.png");

    private final int backgroundHeight = 172;
    private final int backgroundWidth = 193;

    private int guiX;
    private int guiY;

    private int rowCount;

    private float scrollPosition;
    private boolean scrolling;
    private TextFieldWidget searchBox;
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public List<VirtualItemStack> eyetems = new ArrayList<VirtualItemStack>();

    public List<ItemStack> items = new ArrayList<ItemStack>();
    public List<ItemStack> playerItems = new ArrayList<ItemStack>();
    private ItemStack touchDragStack = ItemStack.EMPTY;
    private ItemStack pickStack = ItemStack.EMPTY;

    public CondensedItemScreen() {
        super(new LiteralText("Condensed Minecart GUI"));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (items.size() >= 2) {
            pickStack = items.get(1);
        }
        renderBackground(matrices);
        drawGrid(matrices, delta, mouseX, mouseY);
        drawLabels(matrices, delta, mouseX, mouseY);
        drawSearchBox(matrices, delta, mouseX, mouseY);
        drawPlayerInventory(matrices, delta, mouseX, mouseY);
        drawMinecartItems(matrices, delta, mouseX, mouseY);
        drawTouchDragStack(matrices, delta, mouseX, mouseY);
        drawPickStack(matrices, delta, mouseX, mouseY);
        //renderTooltip(matrices, new LiteralText(":D"), mouseX, mouseY );
//        renderBackgroundTexture(0);
    }

    private void drawGrid(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, GRID);


        int numberOfAddedRows = rowCount - 3;
        this.guiY = (this.height - this.backgroundHeight - numberOfAddedRows * 18) / 2;
        this.guiX = (this.width - this.backgroundWidth) / 2;

        this.drawTexture(matrices, this.guiX, this.guiY, 0, 0, this.backgroundWidth, this.backgroundHeight);
        for (int i = 0; i < numberOfAddedRows; i++) {
            this.drawTexture(matrices, this.guiX, this.guiY + 72 + (18 * i), 0, 54, 193, 18);
        }
        this.drawTexture(matrices, this.guiX, this.guiY + 72 + numberOfAddedRows * 18, 0, 72, this.backgroundWidth, this.backgroundHeight - 72);
    }

    private void drawLabels(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        textRenderer.draw(matrices, "Minecarts", (this.guiX + 8), (this.guiY + 9), 0);
        textRenderer.draw(matrices, "Inventory", (this.guiX + 8), (this.guiY + rowCount*18 + 25), 0);
    }

    private void drawSearchBox(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        searchBox.render(matrices, mouseX, mouseY, delta);
    }

    private void drawPlayerInventory(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        for (int i = 0; i < 27; i++) {
            ItemStack inventoryItem = playerItems.get(i);
            int slotX = this.guiX + 8 + 18*(i % 9);
            int slotY = this.guiY + (rowCount * 18) + 36 + (18*(i / 9));
            itemRenderer.renderInGuiWithOverrides(inventoryItem, slotX, slotY);
            itemRenderer.renderGuiItemOverlay(this.textRenderer, inventoryItem, slotX, slotY, inventoryItem.getCount() == 1 ? "" : Integer.toString(inventoryItem.getCount()));
            if (mouseX >= slotX - 1 && mouseX <= slotX + 16 && mouseY >= slotY - 1 && mouseY <=  slotY + 16) {
                fillGradient(matrices, slotX, slotY, slotX + 16, slotY + 16, -2130706433, -2130706433, 200);
                if (pickStack == ItemStack.EMPTY && inventoryItem != ItemStack.EMPTY) {
                    renderTooltip(matrices, inventoryItem, mouseX, mouseY);
                }
            }
        }
        for (int i = 27; i < 36; i++) {
            ItemStack inventoryItem = playerItems.get(i);
            int slotX = this.guiX + 8 + 18*(i%9);
            int slotY = this.guiY + rowCount * 18 + 94;
            itemRenderer.renderInGuiWithOverrides(inventoryItem, slotX, slotY);
            itemRenderer.renderGuiItemOverlay(this.textRenderer, inventoryItem, slotX, slotY, inventoryItem.getCount() == 1 ? "" : Integer.toString(inventoryItem.getCount()));
            if (mouseX >= slotX - 1 && mouseX <= slotX + 16 && mouseY >= slotY - 1 && mouseY <=  slotY + 16) {
                fillGradient(matrices, slotX, slotY, slotX + 16, slotY + 16, -2130706433, -2130706433, 200);
                if (pickStack == ItemStack.EMPTY && inventoryItem != ItemStack.EMPTY) {
                    renderTooltip(matrices, inventoryItem, mouseX, mouseY);
                }
            }
        }
    }

    private void drawMinecartItems(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        for (int i = 0; i < items.size() && i < rowCount * 9; i++) {
            ItemStack inventoryItem = items.get(i);
            int slotX = this.guiX + 8 + 18*(i%9);
            int slotY = this.guiY + 20 + 18*(i/9);
            itemRenderer.renderInGuiWithOverrides(inventoryItem, slotX, slotY);
//            itemRenderer.renderGuiItemOverlay(this.textRenderer, inventoryItem, slotX, slotY, Integer.toString(eyetems.get(i).amount));
            if (mouseX >= slotX - 1 && mouseX <= slotX + 16 && mouseY >= slotY - 1 && mouseY <=  slotY + 16) {
                fillGradient(matrices, slotX, slotY, slotX + 16, slotY + 16, -2130706433, -2130706433, 200);
                if (pickStack == ItemStack.EMPTY && inventoryItem != ItemStack.EMPTY) {
                    renderTooltip(matrices, inventoryItem, mouseX, mouseY);
                }
            }
        }
    }

    private void drawTouchDragStack(MatrixStack matrices, float delta, int mouseX, int mouseY) {

    }

    private void drawPickStack(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        this.itemRenderer.zOffset = 200.0F;
        itemRenderer.renderInGuiWithOverrides(pickStack, (mouseX - 8), (mouseY - 8));
        itemRenderer.renderGuiItemOverlay(this.textRenderer, pickStack, (mouseX - 8), (mouseY - 8), pickStack.getCount() == 1 ? "" : Integer.toString(pickStack.getCount()));
        this.itemRenderer.zOffset = 0.0F;
    }

    protected void init() {
        super.init();
        rowCount = (this.height - 220) / 18 + 3;
        this.guiY = (this.height - this.backgroundHeight - (rowCount - 3) * 18) / 2;
        this.guiX = (this.width - this.backgroundWidth) / 2;
        for (int i = 0; i < 36; i++) {
            playerItems.add(ItemStack.EMPTY);
        }
        client.keyboard.setRepeatEvents(true);
        TextRenderer textRenderer = this.textRenderer;
        this.searchBox = new TextFieldWidget(textRenderer, this.guiX + 82, this.guiY + 7, 80, 9, new TranslatableText("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setDrawsBackground(false);
        this.searchBox.setVisible(true);
        this.searchBox.setEditableColor(16777215);
        this.addSelectableChild(this.searchBox);
    }

    public void addItems(ChestMinecartEntity minecart, ItemStack itemstack, int slot) {
        Boolean newItem = true;
        for (int i = 0; i < eyetems.size(); i++) {
            VirtualItemStack virtualItemStack = eyetems.get(i);
            if (virtualItemStack.visualItemStack.getNbt() == itemstack.getNbt()) {
                newItem = false;
                virtualItemStack.setItems(minecart, slot, itemstack.getCount());
            }
        }
        if (newItem) {
            eyetems.add(new VirtualItemStack(itemstack, minecart, slot, itemstack.getCount()));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}