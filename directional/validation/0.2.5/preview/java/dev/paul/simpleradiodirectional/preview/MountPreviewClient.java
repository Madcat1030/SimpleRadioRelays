package dev.paul.simpleradiodirectional.preview;

import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.paul.simpleradiodirectional.DirectionalAntenna;
import dev.paul.simpleradiodirectional.block.ParabolicReceiverBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** QA only: native baked block states, actual pack reloads, unretouched framebuffer PNGs. */
public final class MountPreviewClient implements ClientModInitializer {
    private static ModelResourceLocation id(String name) { return new ModelResourceLocation(DirectionalAntenna.ID,name,"inventory"); }
    private static final ModelResourceLocation[] STYLES={id("mesh_preview"),id("octagonal_preview"),id("refined_preview")};
    private static final ModelResourceLocation CLAMPS=id("clamps_preview"), SUPPORT=id("support_preview");
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context->context.addModels(STYLES[0],STYLES[1],STYLES[2],CLAMPS,SUPPORT));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.getOverlay()==null && !(client.screen instanceof Preview)) {
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(),1600,1000);
                client.options.guiScale().set(1);client.resizeDisplay();client.setScreen(new Preview());
            }
        });
    }
    private static final class Preview extends Screen {
        private int frames,style=-1;
        private boolean checked,waiting;
        private List<String> savedPacks;
        Preview(){super(Component.literal("Receiver surface and shared mount verification"));}
        private BlockState receiver(boolean array,int aim,boolean brackets,Direction facing){
            return (array?DirectionalAntenna.ARRAY:DirectionalAntenna.DISH).defaultBlockState()
                    .setValue(ParabolicReceiverBlock.AIM,aim).setValue(ParabolicReceiverBlock.BRACKETS,brackets)
                    .setValue(ParabolicReceiverBlock.FACING,facing);
        }
        private int count(BakedModel model,BlockState state){return model.getQuads(state,null,RandomSource.create(0)).size();}
        private BakedModel model(ModelResourceLocation id){return minecraft.getModelManager().getModel(id);}
        private void verify(){
            int rings=count(model(CLAMPS),null);
            if(rings<=0)throw new IllegalStateException("Shared ring model is missing");
            for(boolean array:new boolean[]{false,true})for(int aim=0;aim<7;aim++){
                var full=receiver(array,aim,true,Direction.NORTH);var shared=full.setValue(ParabolicReceiverBlock.BRACKETS,false);
                var a=minecraft.getBlockRenderer().getBlockModel(full);var b=minecraft.getBlockRenderer().getBlockModel(shared);
                if(count(a,full)-count(b,shared)!=rings)throw new IllegalStateException("Shared state did not remove exactly the two mast rings");
                for(var q:a.getQuads(full,null,RandomSource.create(0)))if(q.getSprite().contents().name().getPath().contains("missingno"))
                    throw new IllegalStateException("A receiver material did not resolve");
            }
            System.out.println("MOUNT_NATIVE_PREVIEW verified seven aims for both receiver types and exact shared ring suppression");
        }
        private void block(GuiGraphics g,BlockState state,double x,double y,double z){
            g.pose().pushPose();g.pose().translate(x,y,z);
            minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            g.pose().popPose();
        }
        private void baked(GuiGraphics g,ModelResourceLocation id){
            var state=receiver(false,3,true,Direction.NORTH);
            minecraft.getBlockRenderer().getModelRenderer().renderModel(g.pose().last(),
                    g.bufferSource().getBuffer(ItemBlockRenderTypes.getRenderType(state,false)),state,model(id),1,1,1,
                    LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        }
        private void mast(GuiGraphics g,boolean full){
            if(full)block(g,Blocks.IRON_BLOCK.defaultBlockState(),0,-3,1);
            for(int y=full?-2:-1;y<=(full?2:1);y++){
                var s=SimpleRadioBlocks.ANTENNA.defaultBlockState().setValue(Antennal.UP,y<2).setValue(Antennal.DOWN,true)
                        .setValue(Antennal.ATTACHED,y==-2).setValue(AntennaBlock.COLUMN,true).setValue(AntennaBlock.AXIS,Direction.Axis.Y);
                block(g,s,0,y,1);
            }
        }
        private void pose(GuiGraphics g,double x,double y,float scale,float tilt,float yaw){
            g.pose().pushPose();g.pose().translate(x,y,250);g.pose().scale(scale,-scale,scale);
            g.pose().mulPose(Axis.XP.rotationDegrees(tilt));g.pose().mulPose(Axis.YP.rotationDegrees(180+yaw));
            g.pose().translate(-.5,-.5,-.5);Lighting.setupFor3DItems();
        }
        private void view(GuiGraphics g,double x,double y,float scale,float tilt,float yaw,boolean array,int aim,boolean mounted){
            pose(g,x,y,scale,tilt,yaw);if(mounted)mast(g,true);
            block(g,receiver(array,aim,true,Direction.NORTH),0,0,0);g.flush();g.pose().popPose();
        }
        private void details(GuiGraphics g,boolean array){
            view(g,width*.29,height*.47,133,12,-24,array,3,true);
            view(g,width*.76,height*.36,118,12,-5,array,3,false);
            view(g,width*.77,height*.77,108,20,150,array,3,false);
            g.drawCenteredString(font,"Mounted on native mast",(int)(width*.29),(int)(height*.96),0xffb4c8bd);
            g.drawCenteredString(font,"Front / clean joins and 1:1 pixel mapping",(int)(width*.76),(int)(height*.57),0xffb4c8bd);
            g.drawCenteredString(font,"Rear / trimmed braces and inset shared clamps",(int)(width*.77),(int)(height*.96),0xffb4c8bd);
        }
        private void pair(GuiGraphics g,double x,double y,boolean array,boolean close){
            pose(g,x,y,close?240:108,20,close?42:63);mast(g, !close);
            if(close){
                baked(g,SUPPORT);baked(g,CLAMPS);
                g.pose().pushPose();g.pose().translate(0,0,2);g.pose().translate(.5,.5,.5);
                g.pose().mulPose(Axis.YP.rotationDegrees(180));g.pose().translate(-.5,-.5,-.5);baked(g,SUPPORT);g.pose().popPose();
            }else{
                block(g,receiver(false,3,true,Direction.NORTH),0,0,0);
                block(g,receiver(array,3,false,Direction.SOUTH),0,0,2);
            }
            g.flush();g.pose().popPose();
        }
        private void enable(int index){
            String[] names={"mesh_parabolic","octagonal_parabolic","refined_parabolic"};
            var repo=minecraft.getResourcePackRepository();if(savedPacks==null)savedPacks=new ArrayList<>(repo.getSelectedIds());
            String id=repo.getAvailableIds().stream().filter(s->s.contains(names[index])).findFirst().orElseThrow();
            var selected=new ArrayList<>(savedPacks);selected.add(id);repo.setSelected(selected);waiting=true;
            minecraft.reloadResourcePacks().thenRun(()->minecraft.execute(()->{
                var s=receiver(false,3,true,Direction.NORTH);
                if(count(minecraft.getBlockRenderer().getBlockModel(s),s)!=count(model(STYLES[index]),s))
                    throw new IllegalStateException("Alternate pack did not replace all model layers");
                verify();System.out.println("ALTERNATE_PACK verified native resource-pack reload: "+names[index]);
                style=index;frames=200+40*index;waiting=false;
            }));
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
            if(!checked){verify();checked=true;}
            g.fill(0,0,width,height,0xff161e21);
            String title=style>=0?new String[]{"MESH ALTERNATE","OCTAGONAL ALTERNATE","REFINED ALTERNATE"}[style]
                    :frames<50?"SOLID STEPPED PARABOLIC":frames<100?"WIDE-ANGLE ARRAY":frames<150?"SHARED MAST BRACKETS":"ALL SEVEN AIM POSITIONS";
            g.drawString(font,title+" / 0.2.5",40,26,0xffd8d2b6,false);
            g.drawString(font,"Native Minecraft capture / 16 texture pixels per block",40,46,0xffa9b8ae,false);
            if(waiting)return;
            if(style>=0 || frames<100)details(g,style<0 && frames>=50);
            else if(frames<150){
                pair(g,width*.28,height*.47,true,false);pair(g,width*.76,height*.47,false,true);
                g.drawCenteredString(font,"Opposite dish and array / shared rings",(int)(width*.28),(int)(height*.96),0xffb4c8bd);
                g.drawCenteredString(font,"Close-up / two arms, one ring pair",(int)(width*.76),(int)(height*.96),0xffb4c8bd);
            }else for(int row=0;row<2;row++)for(int i=0;i<7;i++){
                double x=width*(.075+i*.142),y=height*(.32+row*.45);
                view(g,x,y,67,14,-22,row==1,i,false);
                g.drawCenteredString(font,String.format("%+d degrees",-45+15*i),(int)x,(int)(y+135),0xffb4c8bd);
            }
            g.flush();frames++;
            if(frames==50)capture("Parabolic-Receiver-0.2.5-Native-Preview.png",false);
            if(frames==100)capture("Wide-Angle-Receiver-0.2.5-Native-Preview.png",false);
            if(frames==150)capture("Directional-Receivers-0.2.5-Shared-Mounts.png",false);
            if(frames==200 && style<0){capture("Directional-Receivers-0.2.5-Aiming.png",false);enable(0);}
            if(frames==240 && style==0){capture("Parabolic-Receiver-0.2.5-Mesh-Alternate.png",false);enable(1);}
            if(frames==280 && style==1){capture("Parabolic-Receiver-0.2.5-Octagonal-Alternate.png",false);enable(2);}
            if(frames==320 && style==2)capture("Parabolic-Receiver-0.2.5-Refined-Alternate.png",true);
        }
        private void capture(String name,boolean stop){
            Screenshot.grab(new File("."),name,minecraft.getMainRenderTarget(),message->{
                System.out.println("MOUNT_NATIVE_PREVIEW "+message.getString());
                if(stop){if(savedPacks!=null)minecraft.getResourcePackRepository().setSelected(savedPacks);minecraft.stop();}
            });
        }
    }
}
