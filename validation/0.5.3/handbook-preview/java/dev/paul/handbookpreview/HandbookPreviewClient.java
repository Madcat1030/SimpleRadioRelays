package dev.paul.handbookpreview;

import dev.paul.simpleradiorepeaters.SimpleRadioRepeaters;
import dev.paul.simpleradiorepeaters.client.HandbookBook;
import dev.paul.simpleradiorepeaters.handbook.HandbookContents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** QA only: native book/font/navigation checks and unmodified framebuffer captures. */
public final class HandbookPreviewClient implements ClientModInitializer {
    private record Capture(String name, HandbookBook book, int page) {}
    private final List<Capture> captures=new ArrayList<>();
    private int tick,step;
    private boolean started,requested;
    private static void check(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.getOverlay()!=null)return;
            if(!started){
                started=true;
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(),1000,800);
                client.options.guiScale().set(3);client.resizeDisplay();
                var model=client.getModelManager().getModel(new ModelResourceLocation(SimpleRadioRepeaters.ID,"handbook","inventory"));
                check(model!=client.getModelManager().getMissingModel(),"Handbook item model did not resolve");
                for(boolean foci:new boolean[]{false,true})for(boolean extenders:new boolean[]{false,true}){
                    var book=HandbookBook.create(client.font,HandbookContents.chapters(foci,extenders));
                    for(int page=0;page<book.getPageCount();page++){
                        var raw=book.getPageRaw(page);
                        int lines=client.font.split(raw,114).size();
                        check(lines<=14,"Page overflow foci="+foci+" extenders="+extenders+" page="+(page+1)+" lines="+lines+" text="+raw.getString());
                        check(!raw.getString().contains("handbook.simpleradio_repeaters."),"Missing handbook translation");
                        raw.visit((style,text)->{
                            var event=style.getClickEvent();
                            if(event!=null && event.getAction()==ClickEvent.Action.CHANGE_PAGE){
                                int target=Integer.parseInt(event.getValue());
                                check(target>=1 && target<=book.getPageCount(),"Invalid chapter link");
                            }
                            return Optional.empty();
                        },Style.EMPTY);
                    }
                    String toc=book.getPageRaw(1).getString();
                    check(toc.contains("Dimensional Foci")==foci && toc.contains("Extenders")==extenders,"Optional contents mismatch");
                    System.out.println("HANDBOOK_QA foci="+foci+" extenders="+extenders+" chapters="+HandbookContents.chapters(foci,extenders).size()+" pages="+book.getPageCount()+" fit=true links=true");
                }
                var core=HandbookBook.create(client.font,HandbookContents.chapters(false,false));
                var full=HandbookBook.create(client.font,HandbookContents.chapters(true,true));
                HandbookBook.open();check(client.screen instanceof BookViewScreen,"Runtime book opener failed");
                captures.add(new Capture("Relays-for-Dummies-Cover.png",full,0));
                captures.add(new Capture("Relays-for-Dummies-Core-Contents.png",core,1));
                captures.add(new Capture("Relays-for-Dummies-Contents.png",full,1));
                captures.add(new Capture("Relays-for-Dummies-Catalysts.png",full,chapter(full,"Catalysts")));
                captures.add(new Capture("Relays-for-Dummies-Foci.png",full,chapter(full,"Dimensional Foci")));
                captures.add(new Capture("Relays-for-Dummies-Extenders.png",full,containing(full,"Parabolic:")));
                show(client);return;
            }
            if(step>=captures.size()){
                check(captures.stream().allMatch(c->new java.io.File(client.gameDirectory,"screenshots/"+c.name()).isFile()),"Capture not saved");
                System.out.println("HANDBOOK_QA native book captures complete");client.stop();return;
            }
            tick++;
            if(tick==20 && !requested){
                requested=true;var capture=captures.get(step);
                Screenshot.grab(client.gameDirectory,capture.name(),client.getMainRenderTarget(),message->System.out.println("HANDBOOK_CAPTURE "+capture.name()+" "+message.getString()));
            }
            if(tick>=35){step++;if(step<captures.size())show(client);}
        });
    }
    private void show(Minecraft client){
        var capture=captures.get(step);var screen=new BookViewScreen(capture.book());client.setScreen(screen);
        screen.setPage(capture.page()==0?1:0);
        boolean navigated=screen.handleComponentClicked(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE,Integer.toString(capture.page()+1))));
        check(navigated,"Native page links must navigate");tick=0;requested=false;
    }
    private static int chapter(HandbookBook book,String title){
        int[] found={-1};
        book.getPageRaw(1).visit((style,text)->{
            if(text.equals(title) && style.getClickEvent()!=null)found[0]=Integer.parseInt(style.getClickEvent().getValue())-1;
            return Optional.empty();
        },Style.EMPTY);
        check(found[0]>=2,"Chapter missing: "+title);return found[0];
    }
    private static int containing(HandbookBook book,String text){
        for(int page=2;page<book.getPageCount();page++)if(book.getPageRaw(page).getString().contains(text))return page;
        throw new IllegalStateException("No page contains "+text);
    }
}
