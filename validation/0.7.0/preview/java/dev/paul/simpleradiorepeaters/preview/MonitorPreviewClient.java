package dev.paul.simpleradiorepeaters.preview;

import com.google.gson.*;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.block.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Development-only native Minecraft model/BER capture, using pages produced by the real server test. */
public final class MonitorPreviewClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() == null && !(client.screen instanceof Preview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1600, 1000);
                client.options.guiScale().set(1); client.resizeDisplay(); client.setScreen(new Preview());
            }
        });
    }
    private static final class Preview extends Screen {
        private final Map<String, RelayMonitorBlockEntity> pages = new LinkedHashMap<>();
        private int frames;
        Preview() {
            super(Component.literal("Relay Monitor / native Minecraft capture"));
            try {
                var data = JsonParser.parseString(Files.readString(Path.of(System.getProperty("radiorepeaters.preview.pages")))).getAsJsonObject();
                for (String name : List.of("empty", "overview", "both", "rx", "tx")) {
                    var block = new RelayMonitorBlockEntity(BlockPos.ZERO, SimpleRadioRepeaters.RELAY_MONITOR.defaultBlockState().setValue(ChannelBankBlock.CONNECTED, true));
                    block.load(TagParser.parseTag(data.get(name).getAsString())); pages.put(name, block);
                }
            } catch (Exception e) { throw new IllegalStateException("Could not read server-generated monitor pages", e); }
        }
        private void block(GuiGraphics g, BlockState state, double x, double y, double z) {
            g.pose().pushPose(); g.pose().translate(x,y,z);
            minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            g.pose().popPose();
        }
        private void monitor(GuiGraphics g, String name, double x, double y, float scale, float tilt, float yaw, boolean mounted) {
            var pose=g.pose();pose.pushPose();pose.translate(x,y,250);pose.scale(scale,-scale,scale);
            pose.mulPose(Axis.XP.rotationDegrees(tilt));pose.mulPose(Axis.YP.rotationDegrees(180+yaw));pose.translate(-.5,-.5,-.25);
            Lighting.setupFor3DItems();
            if(mounted) block(g,SimpleRadioRepeaters.RELAY.defaultBlockState(),0,-1,0);
            var monitor=pages.get(name);block(g,monitor.getBlockState(),0,0,0);
            var renderer=minecraft.getBlockEntityRenderDispatcher().getRenderer(monitor);
            if(renderer==null)throw new IllegalStateException("Monitor BER was not registered");
            renderer.render(monitor,0,pose,g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            g.flush();pose.popPose();
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
            g.fill(0,0,width,height,0xff172024);
            g.drawString(font,"RELAY MONITOR / 0.7.0",45,30,0xffdfe2cb,false);
            g.drawString(font,"Native Minecraft models and block-entity text / server-generated diagnostic pages",45,52,0xffa8c0b3,false);
            if(frames<60) {
                monitor(g,"overview",430,400,330,10,-22,true);
                monitor(g,"overview",1180,450,390,0,0,false);
                g.drawCenteredString(font,"Monitor plugged into Relay",430,850,0xffc5d5c4);
                g.drawCenteredString(font,"Front / card and overview",1180,850,0xffc5d5c4);
            } else {
                String[] names={"empty","both","rx","tx"};String[] labels={"No tuned card","Heard tower / TX/RX","Heard tower / RX only","TX only / 16-point bearing"};
                for(int i=0;i<4;i++){
                    int column=i%2,row=i/2;
                    monitor(g,names[i],430+column*750,300+row*430,300,0,0,false);
                    g.drawCenteredString(font,labels[i],430+column*750,490+row*430,0xffc5d5c4);
                }
            }
            g.flush();frames++;
            if(frames==60) capture("Relay-Monitor-0.7.0-Native-Overview.png",false);
            if(frames==120) capture("Relay-Monitor-0.7.0-Native-Pages.png",true);
        }
        private void capture(String name,boolean stop) {
            Screenshot.grab(new File("."),name,minecraft.getMainRenderTarget(),message->{
                System.out.println("MONITOR_NATIVE_CAPTURE "+message.getString());if(stop)minecraft.stop();
            });
        }
    }
}
