package baritone.qwenui.ui;

import baritone.qwenui.QwenUI;
import baritone.qwenui.ai.GeminiClient;
import baritone.qwenui.ai.PlanExecutor;
import baritone.qwenui.core.BaritoneBridge;
import baritone.qwenui.core.LitematicaBridge;
import baritone.qwenui.core.SettingGuide;
import net.minecraft.client.gui.components.AbstractSliderButton;
import baritone.qwenui.core.QwenConfig;
import baritone.qwenui.core.CommandForms;
import baritone.qwenui.core.CommandHotkeys;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Native screen: widgets are built only on changes, never during rendering. */
public final class QwenScreen extends Screen {
    private enum Tab { HOME, SETTINGS, COMMANDS, BUILD, MACROS, HOTKEYS, AI, PREFERENCES }
    // Two-tone charcoal / ivory. States use luminance, never unrelated accent hues.
    private static final int DARK=0xFF181818, LIGHT=0xFFF1EDE4;
    private static final int TEXT=LIGHT, MUTED=0xFFB5B2AC, ACCENT=LIGHT, GREEN=LIGHT, RED=LIGHT;
    private static final Identifier LOGO=Identifier.fromNamespaceAndPath("qwenui","textures/gui/qwen_logo.png");
    private final Screen parent;
    private Tab tab;
    private int px,py,pw,ph,cx,cy,cw,ch,page;
    private Boolean renderedBlur;
    private final baritone.qwenui.ai.RequestGeneration requests=new baritone.qwenui.ai.RequestGeneration();
    private boolean confirmDelete;
    private int buildMode;
    private java.io.File selectedSchematic;
    private int controlsPage;
    private List<GeminiClient.Control> aiControls=List.of();
    private float uiScale=1;
    private boolean rebuild,restoreSearch,closed;
    private String query="",category="",notice="",settingName="",settingValue="",command="";
    private String apiKey="",prompt="",modelQuery="",macroName="",macroCommands="",macroDelay="2",macroRepeat="1";
    private boolean resizeMode;
    private int windowX=-1,windowY=-1,dragEdges,oldWidth,oldHeight;
    private boolean oldCustom;
    private double dragX,dragY;
    private WindowResize.Rect dragStart;
    private final List<ChatBubble> chatBubbles=new ArrayList<>();
    private record ChatBubble(int x,int y,int width,int height,boolean user,Component heading,List<FormattedCharSequence> body) { }
    private int chatX,chatY,chatWidth,chatHeight,chatOverflow;

    private String hotkeyCommand="";
    private int hotkeyKey=-1,hotkeyModifiers,hotkeyIndex=-1;
    private boolean hotkeyEditor,capturingHotkey;
    private int macroIndex=-1,reviewIndex,detailPage,chatScroll;
    private boolean macroEditor,aiConnection,reviewing,busy,aiQueueTracking;
    private EditBox searchBox,promptBox,commandBox;
    private BaritoneBridge.CommandInfo selectedCommand;
    private CommandForms.Field pickerField;
    private int formVariant,formPage;
    private boolean advancedCommands,macroPicking,macroStepList,macroWasRunning;
    private String pickerQuery="";
    private List<String> pickerValues=List.of();
    private final Map<String,String> formValues=new LinkedHashMap<>();
    private final Map<EditBox,CommandForms.Field> formInputs=new LinkedHashMap<>();
    private final Map<AbstractWidget,Component> widgetTooltips=new LinkedHashMap<>();
    private AbstractWidget tooltipTarget;
    private long tooltipSince;
    private List<BaritoneBridge.SettingInfo> settings=List.of();
    private List<BaritoneBridge.CommandInfo> commands=List.of();
    private List<GeminiClient.ModelInfo> models=List.of();
    private final List<GeminiClient.ChatMessage> history=new ArrayList<>();
    private List<GeminiClient.Action> actions=List.of();
    private CompletableFuture<?> request;
    private final List<Line> lines=new ArrayList<>();
    private final List<Box> boxes=new ArrayList<>();
    private final List<FormattedCharSequence> chatLines=new ArrayList<>();
    private record Line(Component text,int x,int y,int color) { }
    private record Box(int x,int y,int w,int h,int color) { }

    public QwenScreen(Screen parent,boolean configOnly) {
        super(Component.literal("Baritone QwenUI"));this.parent=parent;tab=configOnly?Tab.PREFERENCES:Tab.HOME;
        apiKey=cfg().apiKey;
    }
    private QwenConfig cfg(){return QwenUI.config();}
    private boolean id(){return cfg().language.startsWith("id");}
    private String t(String en,String in){return id()?in:en;}
    private Style textStyle(){return Style.EMPTY;}
    private Component component(String s){return Component.literal(s==null?"":s).withStyle(textStyle());}
    @Override protected void init(){closed=false;build();}
    @Override public boolean isPauseScreen(){return false;}
    private void layout(){
        var window=minecraft.getWindow();
        int vanilla=Math.max(1,window.getGuiScale());
        float pixels=UiPreferences.renderPixels(window.getWidth(),window.getHeight(),vanilla,cfg().uiScalePx);
        uiScale=pixels/(float)vanilla;Gfx.pixelsPerUnit=Math.max(1,Math.round(pixels));
        var fit=ResponsiveLayout.fitAutomatic(logicalWidth(),logicalHeight(),cfg().uiWidth,cfg().uiHeight,cfg().customWindowSize);
        px=fit.px();py=fit.py();pw=fit.pw();ph=fit.ph();
        cx=fit.cx();cy=fit.cy();cw=fit.cw();ch=fit.ch();
        if(windowX>=0){int x=Math.max(8,Math.min(windowX,logicalWidth()-pw-8));int y=Math.max(8,Math.min(windowY,logicalHeight()-ph-8));cx+=x-px;cy+=y-py;px=x;py=y;}
    }
    private int logicalWidth(){return Math.max(1,(int)Math.floor(width/(double)uiScale));}
    private int logicalHeight(){return Math.max(1,(int)Math.floor(height/(double)uiScale));}
    private void enterResize(){oldWidth=cfg().uiWidth;oldHeight=cfg().uiHeight;oldCustom=cfg().customWindowSize;cfg().uiWidth=pw;cfg().uiHeight=ph;cfg().customWindowSize=true;windowX=px;windowY=py;resizeMode=true;build();}
    private void finishResize(boolean save){resizeMode=false;dragEdges=0;if(save){persist();notice=t("Window size saved","Ukuran jendela tersimpan");}else{cfg().uiWidth=oldWidth;cfg().uiHeight=oldHeight;cfg().customWindowSize=oldCustom;windowX=-1;windowY=-1;}build();}
    private void resizeControls(){
        line(t("Window size · drag edges or corners","Ukuran panel · tarik tepi atau sudut"),cx,cy,TEXT);
        wrap(t("Drag anywhere inside to move. The preview reflows as you resize. Enter saves, Esc cancels.","Tarik bagian dalam untuk memindahkan. Isi menyesuaikan ukuran saat ditarik. Enter menyimpan, Esc membatalkan."),cx,cy+22,cw,MUTED,4);
        line(pw+" × "+ph+t(" GUI units"," unit GUI"),cx,cy+84,ACCENT);
        button(t("Done · save","Selesai · simpan"),cx,cy+ch-30,cw,28,()->finishResize(true),t("Save this window size","Simpan ukuran jendela ini"));
    }
    private void build(){
        clearWidgets();chatBubbles.clear();chatHeight=0;chatOverflow=0;searchBox=null;promptBox=null;commandBox=null;formInputs.clear();widgetTooltips.clear();tooltipTarget=null;lines.clear();boxes.clear();chatLines.clear();layout();
        String[] labels={t("Home","Beranda"),t("Settings","Pengaturan"),t("Commands","Perintah"),t("Build","Bangun"),t("Macros","Makro"),t("Keybinds","Keybind"),t("AI assistant","Asisten AI"),t("Preferences","Preferensi")};
        int navH=Math.min(30,Math.max(12,(ph-100)/Tab.values().length-3));
        if(resizeMode){resizeControls();rebuild=false;return;}
        for(Tab dest:Tab.values()){
            int yy=py+60+dest.ordinal()*(navH+3);
            QButton b=button(labels[dest.ordinal()],px+10,yy,cx-px-24,navH,()->switchTo(dest),t("Open ","Buka ")+labels[dest.ordinal()]);
            b.selected=tab==dest;b.icon=switch(dest){case HOME->0;case SETTINGS->1;case COMMANDS->2;case BUILD->18;case MACROS->4;case HOTKEYS->17;case AI->5;case PREFERENCES->6;};
        }
        button("×",px+pw-30,py+12,20,20,this::onClose,t("Close · Esc","Tutup · Esc"));
        button(t("STOP","STOP"),px+12,py+ph-31,cx-px-28,21,this::stopAll,t("Stop all Baritone tasks and macros. Hotkey: End","Hentikan semua tugas Baritone dan makro. Hotkey: End"));
        line(clip(title(),cw-28),cx,py+16,TEXT);line(clip(subtitle(),cw),cx,py+33,MUTED);
        try {
            switch(tab){
                case HOME->home();case SETTINGS->settings();case COMMANDS->commands();case BUILD->buildTab();
                case MACROS->macros();case HOTKEYS->hotkeys();case AI->ai();case PREFERENCES->preferences();
            }
        }catch(RuntimeException failure){wrap(t("Unable to load this page: ","Halaman gagal dimuat: ")+error(failure),cx,cy,cw,RED,Math.max(1,ch/12));}
        if(restoreSearch&&searchBox!=null){setFocused(searchBox);searchBox.setFocused(true);searchBox.setCursorPosition(searchBox.getValue().length());}
        restoreSearch=false;rebuild=false;
        if(Boolean.getBoolean("qwenui.layoutAudit"))auditLayout();
    }
    /** Opt-in client test instrumentation; does not run in normal play. */
    private void auditLayout(){
        var widgets=children().stream().filter(e->e instanceof AbstractWidget).map(e->(AbstractWidget)e).toList();
        int problems=0;
        for(int i=0;i<widgets.size();i++){
            var a=widgets.get(i);
            if(a.getX()<px||a.getY()<py||a.getRight()>px+pw||a.getBottom()>py+ph){problems++;System.err.println("QWEN_LAYOUT outside: "+a.getMessage().getString());}
            for(int j=i+1;j<widgets.size();j++){
                var b=widgets.get(j);
                if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY()){
                    problems++;System.err.println("QWEN_LAYOUT overlap: "+a.getMessage().getString()+" / "+b.getMessage().getString());
                }
            }
            for(var l:lines)if(l.x<a.getRight()&&l.x+font.width(l.text)>a.getX()&&l.y<a.getBottom()&&l.y+9>a.getY()){
                problems++;System.err.println("QWEN_LAYOUT text overlap: "+l.text.getString()+" / "+a.getMessage().getString());
            }
        }
        int previousBottom=Integer.MIN_VALUE;
        for(var bubble:chatBubbles){
            if(bubble.x()<chatX||bubble.x()+bubble.width()>chatX+chatWidth||bubble.y()<previousBottom)problems++;
            for(var text:bubble.body())if(font.width(text)>bubble.width()-20)problems++;
            if(27+bubble.body().size()*13>bubble.height()-7)problems++;
            previousBottom=bubble.y()+bubble.height();
        }
        System.out.println("QWEN_CHAT bubbles="+chatBubbles.size()+" problems="+problems);
        System.out.println("QWEN_LAYOUT tab="+tab+" viewport="+width+"x"+height+" requestedScale="+cfg().uiScalePx+" effective="+uiScale+" widgets="+widgets.size()+" problems="+problems);
    }
    private void switchTo(Tab dest){reviewing=false;aiConnection=false;hotkeyEditor=false;macroEditor=false;macroStepList=false;capturingHotkey=false;selectedCommand=null;pickerField=null;macroPicking=false;tab=dest;page=0;query="";category="";settingName="";notice="";confirmDelete=false;build();}
    private String title(){return switch(tab){
        case HOME->t("Home","Beranda");
        case SETTINGS->t("Baritone settings","Pengaturan Baritone");
        case COMMANDS->t("Commands","Perintah");
        case BUILD->t("Build","Bangun");
        case HOTKEYS->t("Keybinds","Keybind");
        case MACROS->t("Macros","Makro");
        case AI->t("AI assistant","Asisten AI");
        case PREFERENCES->t("Preferences","Preferensi");};}
    private String subtitle(){return switch(tab){
        case HOME->t("Status and one-click shortcuts","Status dan pintasan satu klik");
        case SETTINGS->t("Tap a setting to see what it does and how to fill it","Pilih setting untuk melihat fungsi dan cara mengisinya");
        case COMMANDS->t("Every Baritone command as a form · no typing needed","Semua perintah Baritone sebagai form · tanpa mengetik");
        case BUILD->t("Build a Litematica placement or a schematic file","Bangun placement Litematica atau berkas skematik");
        case MACROS->t("Save a list of commands and run them in order","Simpan daftar perintah dan jalankan berurutan");
        case AI->t("Ask in plain language · actions run directly","Tanya dengan bahasa biasa · aksi langsung dijalankan");
        case HOTKEYS->t("Press a key (with Ctrl/Shift/Alt) to run a command","Tekan tombol (dengan Ctrl/Shift/Alt) untuk menjalankan perintah");
        case PREFERENCES->t("Language · UI scale · blur · window size","Bahasa · skala UI · blur · ukuran jendela");};}
    private String statusText(){
        try{
            if(QwenUI.macros().isRunning())return t("Macro running: ","Makro berjalan: ")+QwenUI.macros().currentMacroName()+(QwenUI.macros().isPaused()?t(" (paused)"," (dijeda)"):"");
            var b=baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
            var control=b.getPathingControlManager().mostRecentInControl();
            if(control.isPresent()&&control.get().isActive())return t("Busy: ","Sibuk: ")+control.get().displayName();
            if(b.getPathingBehavior().isPathing())return t("Walking a path","Sedang berjalan di jalur");
        }catch(RuntimeException|LinkageError ignored){}
        return t("Ready · no task running","Siap · tidak ada tugas berjalan");
    }
    private void openCommandByName(String name){
        refreshCatalog();
        var found=commands.stream().filter(c->c.name().equalsIgnoreCase(name)||c.aliases().stream().anyMatch(a->a.equalsIgnoreCase(name))).findFirst();
        if(found.isEmpty()){notice="! "+t("Command not available: ","Perintah tidak tersedia: ")+name;build();return;}
        switchTo(Tab.COMMANDS);openCommand(found.get());
    }
    private void home(){
        card(cx,cy,cw,40);
        line(t("Status","Status"),cx+10,cy+7,MUTED);line(clip(statusText(),cw-20),cx+10,cy+21,TEXT);
        int gap=6,colW=(cw-gap)/2,bh=36,y=cy+48;
        Object[][] actions={
            {t("Go to…","Pergi ke…"),t("Walk to coordinates","Jalan ke koordinat"),2,(Runnable)()->openCommandByName("goto")},
            {t("Mine…","Tambang…"),t("Collect a block type","Kumpulkan satu jenis blok"),4,(Runnable)()->openCommandByName("mine")},
            {t("Follow…","Ikuti…"),t("Follow a player or mob","Ikuti pemain atau mob"),7,(Runnable)()->openCommandByName("follow")},
            {t("Explore…","Jelajah…"),t("Auto-explore new land","Jelajahi wilayah baru"),9,(Runnable)()->openCommandByName("explore")},
            {t("Build","Bangun"),t("Litematica or schematic file","Litematica atau berkas skematik"),18,(Runnable)()->{buildMode=0;switchTo(Tab.BUILD);}},
            {t("Settings","Pengaturan"),t("Tune how Baritone behaves","Atur perilaku Baritone"),1,(Runnable)()->switchTo(Tab.SETTINGS)}};
        int rowsFit=Math.max(1,Math.min(3,(ch-48-34)/(bh+gap)));
        for(int i=0;i<actions.length&&i<rowsFit*2;i++){
            Object[] a=actions[i];int xx=cx+(i%2)*(colW+gap),yy=y+(i/2)*(bh+gap);
            QButton b=button((String)a[0],xx,yy,colW,bh,(Runnable)a[3],(String)a[1]);b.icon=(Integer)a[2];b.detail=(String)a[1];
        }
        int controlsY=y+rowsFit*(bh+gap);int q=(cw-2*gap)/3;
        button(t("Pause","Jeda"),cx,controlsY,q,24,()->run(()->{QwenUI.bridge().execute("pause");notice=t("Paused","Dijeda");build();}),t("Pause the current task","Jeda tugas saat ini"));
        button(t("Resume","Lanjut"),cx+q+gap,controlsY,q,24,()->run(()->{QwenUI.bridge().execute("resume");notice=t("Resumed","Dilanjutkan");build();}),t("Continue a paused task","Lanjutkan tugas yang dijeda"));
        button(t("Stop all","Hentikan"),cx+2*(q+gap),controlsY,q,24,this::stopAll,t("Stop every task and macro (also the End key)","Hentikan semua tugas dan makro (juga tombol End)"));
        int hintY=controlsY+30;
        if(ch-(hintY-cy)>=24)wrap(QwenUI.openKeyLabel()+t(" opens this panel · End stops everything · keys can be changed in Minecraft Controls."," membuka panel ini · End menghentikan semuanya · tombol bisa diubah di Controls Minecraft."),cx,hintY,cw,MUTED,Math.max(1,(ch-(hintY-cy))/12));
    }
    private void refreshCatalog(){settings=QwenUI.bridge().allSettings(cfg().language);commands=QwenUI.bridge().allCommands(cfg().language);}
    private String catLabel(String s){return switch(s){case ""->t("All","Semua");case "basic"->t("Essential","Penting");case "navigation"->t("Navigation","Navigasi");case "mining-building"->t("Mining / building","Menambang / membangun");case "visual"->t("Rendering","Tampilan");case "world-cache"->t("World / cache","Dunia / cache");case "commands"->t("Chat / commands","Chat / perintah");case "general"->t("General","Umum");case "automation"->t("Automation","Otomasi");case "world"->t("World","Dunia");case "utility"->t("Utility","Utilitas");default->SettingGuide.areaKeys().contains(s)?SettingGuide.areaLabel(s,id()):t("Other","Lainnya");};}
    private void searchAndCategory(boolean isSetting){
        refreshCatalog();int arrow=22,catW=Math.min(130,cw/3),searchW=Math.max(40,cw-catW-2*arrow-18);
        searchBox=input(cx,cy,searchW,24,t("Search…","Cari…"),query,s->{query=s;page=0;restoreSearch=true;rebuild=true;});
        List<String> cats=new ArrayList<>();cats.add("");
        if(isSetting){cats.add("basic");settings.stream().map(BaritoneBridge.SettingInfo::category).distinct().sorted().forEach(cats::add);}
        else commands.stream().map(BaritoneBridge.CommandInfo::category).distinct().sorted().forEach(cats::add);
        int at=Math.max(0,cats.indexOf(category));
        button("‹",cx+searchW+6,cy,arrow,24,()->{category=cats.get(Math.floorMod(at-1,cats.size()));page=0;build();},t("Previous category","Kategori sebelumnya"));
        button(catLabel(category),cx+searchW+6+arrow+6,cy,catW,24,()->{category=cats.get((at+1)%cats.size());page=0;build();},t("Filter by category (click for next)","Filter kategori (klik untuk berikutnya)"));
        button("›",cx+cw-arrow,cy,arrow,24,()->{category=cats.get((at+1)%cats.size());page=0;build();},t("Next category","Kategori berikutnya"));
    }
    private void settings(){
        if(!settingName.isEmpty()){settingEditor();return;}
        searchAndCategory(true);
        List<BaritoneBridge.SettingInfo> filtered=settings.stream().filter(s->category.isEmpty()||("basic".equals(category)?isBasicSetting(s.name()):s.category().equals(category))).filter(s->(s.name()+s.description()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(ch-57)/43);int from=pageStart(filtered.size(),rows);
        for(int i=from;i<Math.min(filtered.size(),from+rows);i++){
            var s=filtered.get(i);int yy=cy+33+(i-from)*43;card(cx,yy,cw,37);
            QButton row=button(settingLabel(s.name()),cx,yy,cw-70,37,()->openSetting(s),s.name()+"\n"+s.description()+"\n"+s.type()+" = "+s.value());
            row.detail=s.description();row.flat=true;
            String label=s.booleanValue()!=null?(s.booleanValue()?"ON":"OFF"):clip(s.value().isEmpty()?t("Edit","Ubah"):s.value(),50);
            QButton edit=button(s.editable()?label:t("View","Lihat"),cx+cw-65,yy+5,59,27,()->{
                if(s.booleanValue()!=null&&s.editable())run(()->{QwenUI.bridge().setSetting(s.name(),Boolean.toString(!s.booleanValue()));QwenUI.bridge().saveSettings();notice=s.name()+" · "+(!s.booleanValue()?"ON":"OFF");build();});
                else openSetting(s);
            },s.description()+"\n"+s.type()+" = "+s.value());
            edit.selected=s.booleanValue()!=null&&s.booleanValue();
        }
        pager(filtered.size(),rows);if(filtered.isEmpty())line(t("No matching settings","Tidak ada pengaturan cocok"),cx,cy+40,MUTED);
    }
    private boolean isBasicSetting(String name){var m=SettingGuide.meta(name);return m!=null&&m.level()==0;}
    private String settingLabel(String name){var m=SettingGuide.meta(name);return m==null?name:(id()?m.labelId():SettingGuide.humanizeName(name));}
    private void openSetting(BaritoneBridge.SettingInfo s){settingName=s.name();settingValue=s.value();page=0;detailPage=0;build();}
    private EditBox settingBox;
    private void settingEditor(){
        var s=QwenUI.bridge().allSettings(cfg().language).stream().filter(v->v.name().equals(settingName)).findFirst().orElse(null);
        if(s==null){settingName="";settings();return;}
        boolean ind=id();
        String def=QwenUI.bridge().defaultValue(s.name());
        var guide=SettingGuide.of(s.name(),s.type(),def,s.value());
        var kind=guide.kind();
        boolean numeric=kind==SettingGuide.Kind.INT||kind==SettingGuide.Kind.DECIMAL;
        boolean editable=s.editable();
        button(t("‹ Back","‹ Kembali"),cx,cy,80,22,()->{settingName="";page=0;build();},t("Return to the settings list","Kembali ke daftar pengaturan"));
        String tag=clip((ind?guide.areaId():guide.areaEn())+" · "+SettingGuide.levelLabel(guide.level(),ind),cw-92);
        text0(tag,cx+cw-font.width(component(tag)),cy+7,MUTED);
        // ---- controls are laid out bottom-up so the text above can use whatever room is left
        int yApply=cy+ch-26;int y=yApply;
        int bw=(cw-8)/2;
        button(t("Apply + save","Terapkan + simpan"),cx,yApply,bw,26,()->run(()->{
            if(numeric&&!SettingGuide.isValidNumber(kind,settingValue))throw new IllegalArgumentException(t("Not a valid number: '","Bukan angka yang valid: '")+settingValue+"'");
            QwenUI.bridge().setSetting(s.name(),settingValue);QwenUI.bridge().saveSettings();
            settingValue=QwenUI.bridge().allSettings().stream().filter(v->v.name().equals(s.name())).findFirst().map(BaritoneBridge.SettingInfo::value).orElse(settingValue);
            notice=s.name()+" = "+clip(settingValue,200)+" · "+t("saved","tersimpan");build();}),
            t("Apply the value and write it to Baritone's settings file","Terapkan nilai dan simpan ke berkas pengaturan Baritone")).active=editable;
        button(t("Restore default","Pulihkan bawaan"),cx+bw+8,yApply,bw,26,()->run(()->{QwenUI.bridge().resetSetting(s.name());QwenUI.bridge().saveSettings();settingValue=QwenUI.bridge().allSettings().stream().filter(v->v.name().equals(s.name())).findFirst().orElseThrow().value();notice=t("Default restored","Bawaan dipulihkan");build();}),
            (def==null?"":t("Default: ","Bawaan: ")+clip(def,260)+"\n")+t("Reset only this setting","Reset pengaturan ini saja")).active=editable;
        if(kind==SettingGuide.Kind.BOOL){
            y-=30;
            QButton on=button("ON",cx,y,bw,26,()->{settingValue="true";build();},t("Enable","Aktifkan"));on.selected=settingValue.equalsIgnoreCase("true");on.active=editable;
            QButton off=button("OFF",cx+bw+8,y,bw,26,()->{settingValue="false";build();},t("Disable","Matikan"));off.selected=settingValue.equalsIgnoreCase("false");off.active=editable;
            settingBox=null;
        }else{
            y-=30;
            boolean bad=numeric&&!SettingGuide.isValidNumber(kind,settingValue);
            settingBox=input(cx,y,cw,25,t("Type a value manually","Ketik nilai manual"),settingValue,v->settingValue=v);settingBox.setEditable(editable);
            if(bad)text0("! "+t("not a valid number","bukan angka yang valid"),cx+cw-font.width(component("! "+t("not a valid number","bukan angka yang valid")))-8,y-12,RED);
        }
        // preset chips (value buttons)
        var presets=guide.presets();
        if(editable&&!presets.isEmpty()&&kind!=SettingGuide.Kind.BOOL){
            List<List<SettingGuide.Preset>> rowsOfChips=new ArrayList<>();List<SettingGuide.Preset> row=new ArrayList<>();int used=0;
            for(var p:presets){int w=Math.max(34,font.width(component(p.label()))+18);if(!row.isEmpty()&&used+w+4>cw){rowsOfChips.add(row);row=new ArrayList<>();used=0;}row.add(p);used+=w+4;}
            if(!row.isEmpty())rowsOfChips.add(row);
            int shown=Math.min(3,rowsOfChips.size());y-=shown*26;
            for(int r=0;r<shown;r++){int xx=cx;for(var p:rowsOfChips.get(r)){int w=Math.max(34,font.width(component(p.label()))+18);if(xx+w>cx+cw)w=cx+cw-xx;
                final var preset=p;
                QButton chip=button(p.label(),xx,y+r*26,w,22,()->{settingValue=guide.appendMode()&&!preset.value().isEmpty()?SettingGuide.listAdd(settingValue,preset.value()):preset.value();build();},t("Use value: ","Pakai nilai: ")+(preset.value().isEmpty()?t("(empty)","(kosong)"):preset.value()));
                chip.selected=!guide.appendMode()&&preset.value().equals(settingValue);xx+=w+4;}}
        }
        if(numeric&&editable){
            y-=28;int q=(cw-18)/4;String st=SettingGuide.formatNumber(guide.step(),kind==SettingGuide.Kind.INT);
            button("÷ 2",cx,y,q,24,()->stepValue(guide,0,0.5),t("Halve the value","Bagi dua nilai"));
            button("− "+st,cx+q+6,y,q,24,()->stepValue(guide,-guide.step(),1),t("Decrease by ","Kurangi "+"")+st);
            button("+ "+st,cx+2*(q+6),y,q,24,()->stepValue(guide,guide.step(),1),t("Increase by ","Tambah ")+st);
            button("× 2",cx+3*(q+6),y,q,24,()->stepValue(guide,0,2),t("Double the value","Gandakan nilai"));
            if(guide.hasRange()){y-=28;addRenderableWidget(new ValueSlider(cx,y,cw,24,guide));}
        }
        // ---- explanatory text above the controls
        int ty=cy+28,maxY=y-4;
        text0(clip(ind?guide.labelId():guide.labelEn(),cw),cx,ty,ACCENT);ty+=14;
        text0(clip(s.name()+" · "+s.type()+(def==null?"":" · "+t("default ","bawaan ")+def)+(editable?"":t(" · read only"," · hanya baca")),cw),cx,ty,MUTED);ty+=15;
        String how=SettingGuide.howTo(guide,ind),example=t("Chat: ","Chat: ")+SettingGuide.chatExample(guide);
        String desc=ind&&!guide.descId().isBlank()?guide.descId():s.description();
        int howN=Math.max(1,Math.min(3,font.split(component(how),cw).size()));
        int room=Math.max(0,(maxY-ty)/12);
        int descN=Math.max(0,Math.min(3,Math.min(font.split(component(desc),cw).size(),room-howN-1)));
        if(descN>0){wrap(desc,cx,ty,cw,MUTED,descN);ty+=descN*12+4;}
        int howShown=Math.min(howN,Math.max(1,(maxY-ty)/12));
        wrap(how,cx,ty,cw,TEXT,howShown);ty+=howShown*12+2;
        if(ty+12<=maxY+4)text0(clip(example,cw),cx,ty,MUTED);
    }
    private void text0(String s,int x,int y,int color){lines.add(new Line(component(s),x,y,color));}
    private void stepValue(SettingGuide.Guide guide,double delta,double factor){
        String next=SettingGuide.adjust(guide,settingValue,delta,factor);
        if(next==null){notice="! "+t("Type a valid number first","Ketik angka yang valid dulu");return;}
        settingValue=next;build();
    }
    private final class ValueSlider extends AbstractSliderButton {
        private final SettingGuide.Guide guide;
        ValueSlider(int x,int y,int w,int h,SettingGuide.Guide guide){
            super(x,y,w,h,Component.empty(),initialFraction(guide,QwenScreen.this.settingValue));this.guide=guide;updateMessage();
        }
        private double span(){return Math.max(1e-9,guide.max()-guide.min());}
        private String current(){
            double raw=guide.min()+value*span();double snapped=Math.round((raw-guide.min())/guide.step())*guide.step()+guide.min();
            snapped=Math.max(guide.min(),Math.min(guide.max(),snapped));
            return SettingGuide.formatNumber(snapped,guide.kind()==SettingGuide.Kind.INT);
        }
        @Override protected void updateMessage(){setMessage(component(current()));}
        @Override protected void applyValue(){settingValue=current();if(settingBox!=null&&!settingBox.getValue().equals(settingValue))settingBox.setValue(settingValue);}
        @Override protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor g,int x,int y){ }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            round(g,getX(),getY(),getWidth(),getHeight(),6,isHoveredOrFocused()?0xFF3A3A3A:0xFF303030);
            boolean whole=guide.kind()==SettingGuide.Kind.INT;
            String lo=SettingGuide.formatNumber(guide.min(),whole),hi=SettingGuide.formatNumber(guide.max(),whole);
            int loW=font.width(component(lo)),hiW=font.width(component(hi));
            int ty=getY()+(getHeight()-9)/2+1,left=getX()+10+loW+8,right=getRight()-10-hiW-8,yy=getY()+getHeight()/2,knob=left+(int)Math.round((right-left)*value);
            drawText(g,component(lo),getX()+10,ty,MUTED);drawText(g,component(hi),getRight()-10-hiW,ty,MUTED);
            round(g,left,yy-2,right-left,4,2,0xFF555555);round(g,left,yy-2,Math.max(3,knob-left),4,2,LIGHT);round(g,knob-4,yy-6,8,12,4,LIGHT);
        }
    }
    private static double initialFraction(SettingGuide.Guide guide,String text){
        try{double v=Double.parseDouble(text.strip());return Math.max(0,Math.min(1,(v-guide.min())/Math.max(1e-9,guide.max()-guide.min())));}
        catch(RuntimeException e){return 0;}
    }
    private void commands(){
        if(selectedCommand!=null){if(pickerField!=null)parameterPicker();else commandForm();return;}
        searchAndCategory(false);
        if(macroPicking)button(t("‹ Return to macro","‹ Kembali ke makro"),cx,cy+32,cw,24,()->{tab=Tab.MACROS;macroPicking=false;macroEditor=true;page=0;build();},t("Keep the current macro draft","Simpan draf makro saat ini"));
        else if(advancedCommands){commandBox=input(cx,cy+32,cw-100,24,t("Optional advanced command input","Input perintah lanjutan opsional"),command,s->command=s);button(t("Run","Jalankan"),cx+cw-94,cy+32,55,24,()->execute(command),t("Run manually entered command","Jalankan perintah manual"));button("×",cx+cw-33,cy+32,33,24,()->{advancedCommands=false;build();},t("Hide advanced input","Sembunyikan input lanjutan"));}
        else button(t("Advanced input (optional)","Input lanjutan (opsional)"),cx,cy+32,cw,24,()->{advancedCommands=true;build();},t("All features below have buttons and parameter forms. You do not need to type # commands.","Semua fitur di bawah memiliki tombol dan form parameter. Tidak perlu mengetik perintah #."));
        List<BaritoneBridge.CommandInfo> filtered=commands.stream().filter(s->category.isEmpty()||s.category().equals(category)).filter(s->(s.name()+s.aliases()+s.description()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(ch-91)/43),from=pageStart(filtered.size(),rows);
        for(int i=from;i<Math.min(filtered.size(),from+rows);i++){
            var c=filtered.get(i);int yy=cy+65+(i-from)*43;card(cx,yy,cw,37);line(clip(c.name()+" "+String.join(", ",c.aliases()),cw-75),cx+8,yy+5,ACCENT);line(clip(c.description(),cw-75),cx+8,yy+20,MUTED);
            button(t("Open","Buka"),cx+cw-59,yy+5,53,26,()->openCommand(c),c.description()+"\n"+c.usage());
        }
        pager(filtered.size(),rows);
    }
    private void openCommand(BaritoneBridge.CommandInfo c){selectedCommand=c;formVariant=0;formPage=0;formValues.clear();pickerField=null;notice="";build();}
    private CommandForms.Variant currentVariant(){var variants=CommandForms.forCommand(selectedCommand.name(),selectedCommand.usage());formVariant=Math.floorMod(formVariant,variants.size());return variants.get(formVariant);}
    private void commandForm(){
        var variants=CommandForms.forCommand(selectedCommand.name(),selectedCommand.usage());var variant=currentVariant();
        button(t("‹ Features","‹ Fitur"),cx,cy,85,22,()->{selectedCommand=null;page=0;build();},t("Return to feature buttons","Kembali ke tombol fitur"));
        line(clip("#"+selectedCommand.name(),cw-197),cx+96,cy+6,ACCENT);
        button(t("Keybind","Keybind"),cx+cw-90,cy,90,22,this::bindCurrentCommand,t("Save a shortcut for the configured parameters","Simpan shortcut untuk parameter yang diatur"));
        button(t("Mode: ","Mode: ")+t(variant.en(),variant.id())+(variants.size()>1?" · "+(formVariant+1)+"/"+variants.size():""),cx,cy+31,cw,25,()->{formVariant=(formVariant+1)%variants.size();formPage=0;formValues.clear();build();},t("Click to choose the feature's operation","Klik untuk memilih operasi fitur"));
        boolean coordinates=variant.fields().stream().anyMatch(f->f.key().matches("[xyz][12]?"));
        if(coordinates&&minecraft.player!=null)button(t("Use my position","Pakai posisi saya"),cx,cy+62,cw,20,()->{var pos=minecraft.player.blockPosition();for(var f:variant.fields())if(f.key().matches("[xyz][12]?"))formValues.put(f.key(),Integer.toString(f.key().charAt(0)=='x'?pos.getX():f.key().charAt(0)=='y'?pos.getY():pos.getZ()));build();},t("Fill coordinates from your current player position","Isi koordinat dari posisi pemain saat ini"));
        else button(t("Bind this # command","Pasang keybind perintah #"),cx,cy+62,cw,20,this::bindCurrentCommand,t("Configure a shortcut without running this command","Atur shortcut tanpa menjalankan perintah ini"));
        int columns=cw>=230?2:1,rows=Math.max(1,(ch-143)/43),perPage=rows*columns,total=variant.fields().size();
        int pages=Math.max(1,(total+perPage-1)/perPage);formPage=Math.max(0,Math.min(formPage,pages-1));int start=formPage*perPage,fw=(cw-(columns-1)*8)/columns;
        for(int i=start;i<Math.min(total,start+perPage);i++){
            var field=variant.fields().get(i);int index=i-start,xx=cx+(index%columns)*(fw+8),yy=cy+87+(index/columns)*43;
            line(clip(t(field.en(),field.id())+(field.required()?" *":""),fw),xx,yy,MUTED);
            EditBox box=input(xx,yy+13,fw-48,25,fieldHint(field),formValues.getOrDefault(field.key(),""),v->formValues.put(field.key(),v));formInputs.put(box,field);
            button(t("Pick","Pilih"),xx+fw-44,yy+13,44,25,()->openParameterPicker(field),t("Choose a native Baritone suggestion","Pilih saran asli Baritone"));
        }
        if(total==0)wrap(t("No parameters needed. Click Run to activate this feature.","Tidak membutuhkan parameter. Klik Jalankan untuk memakai fitur ini."),cx,cy+99,cw,MUTED,3);
        int foot=cy+ch-25;
        if(pages>1)button(t("Parameter page ","Halaman parameter ")+(formPage+1)+"/"+pages,cx,foot-29,cw,21,()->{formPage=(formPage+1)%pages;build();},t("View every parameter","Lihat seluruh parameter"));
        else line(clip(t("Ready when required fields (*) are filled.","Siap setelah parameter wajib (*) terisi."),cw),cx,foot-21,MUTED);
        int half=(cw-8)/2;
        button(t("Run feature","Jalankan fitur"),cx,foot,half,25,()->run(()->execute(CommandForms.compose(selectedCommand.name(),currentVariant(),formValues))),selectedCommand.description()).active=!macroPicking;
        button(t("+ Add to macro","+ Tambah ke makro"),cx+half+8,foot,half,25,()->run(()->{String step=CommandForms.compose(selectedCommand.name(),currentVariant(),formValues);if(!macroEditor){macroIndex=-1;macroName="";macroCommands="";macroDelay="2";macroRepeat="1";}macroCommands=macroCommands.isBlank()?step:macroCommands+"; "+step;macroEditor=true;macroStepList=false;macroPicking=false;selectedCommand=null;tab=Tab.MACROS;page=0;notice=t("Action added to draft","Aksi ditambahkan ke draf");build();}),t("Add this configured feature without running it","Tambahkan fitur yang disetel ini tanpa menjalankannya"));
    }
    private String fieldHint(CommandForms.Field field){
        if(!id())return field.hint();
        if(field.key().matches("[xyz][12]?"))return "Angka atau koordinat relatif: ~, ~10";
        return switch(field.key()){
            case "block","blocks","with"->"ID blok, contoh minecraft:stone";
            case "entity","entities"->"ID entitas, contoh minecraft:pig";
            case "item","items"->"ID item Minecraft";
            case "filename","file"->"Nama file yang sudah tersedia";
            case "players"->"Nama pemain yang akan diikuti";
            case "setting"->"Nama pengaturan Baritone";
            case "value"->"Nilai baru sesuai tipe pengaturan";
            case "count","radius","height","width","depth","distance","index"->"Isi angka untuk "+field.id().toLowerCase(Locale.ROOT);
            default->field.id()+(field.required()?" (wajib)":" (opsional)");
        };
    }
    private String completionPrefix(CommandForms.Field current){
        var variant=currentVariant();StringBuilder prefix=new StringBuilder(selectedCommand.name());for(String token:variant.prefix())prefix.append(' ').append(token);
        for(var f:variant.fields()){prefix.append(' ');if(f.key().equals(current.key())){prefix.append(formValues.getOrDefault(f.key(),""));break;}String value=formValues.getOrDefault(f.key(),"").trim();if(value.isEmpty())break;prefix.append(value);}
        return prefix.toString();
    }
    private void openParameterPicker(CommandForms.Field field){run(()->{LinkedHashSet<String> values=new LinkedHashSet<>(field.choices());if(minecraft.level!=null)values.addAll(QwenUI.bridge().suggestions(completionPrefix(field)));pickerValues=List.copyOf(values);pickerField=field;pickerQuery="";page=0;build();});}
    private void parameterPicker(){
        button(t("‹ Parameters","‹ Parameter"),cx,cy,100,22,()->{pickerField=null;page=0;build();},t("Return without changing","Kembali tanpa mengubah"));
        line(clip(t(pickerField.en(),pickerField.id()),cw-110),cx+110,cy+6,ACCENT);
        searchBox=input(cx,cy+32,cw,24,t("Filter available values…","Saring nilai tersedia…"),pickerQuery,s->{pickerQuery=s;page=0;restoreSearch=true;rebuild=true;});
        var filtered=pickerValues.stream().filter(v->v.toLowerCase(Locale.ROOT).contains(pickerQuery.toLowerCase(Locale.ROOT))).toList();int rows=Math.max(1,(ch-91)/29),start=pageStart(filtered.size(),rows);
        for(int i=start;i<Math.min(filtered.size(),start+rows);i++){String value=filtered.get(i);int yy=cy+65+(i-start)*29;button(value,cx,yy,cw,24,()->{String old=formValues.getOrDefault(pickerField.key(),"");boolean multi=Set.of("blocks","players","entities","items").contains(pickerField.key());formValues.put(pickerField.key(),multi&&!old.isBlank()?old+" "+value:value);pickerField=null;page=0;build();},value);}
        if(filtered.isEmpty())wrap(t("No current suggestions. Enter a coordinate, name or file in the parameter field, or change the mode. Native suggestions require entering a world.","Tidak ada saran saat ini. Isi koordinat, nama atau file di kolom parameter, atau ubah mode. Saran asli membutuhkan dunia aktif."),cx,cy+70,cw,MUTED,5);
        pager(filtered.size(),rows);
    }
    private void execute(String text){run(()->{if(minecraft.level==null)throw new IllegalStateException(t("Enter a world first","Masuk dunia terlebih dahulu"));if(!QwenUI.bridge().execute(text))throw new IllegalArgumentException(t("Unknown command","Perintah tidak dikenal"));notice=t("Dispatched; check Baritone chat for result","Dikirim; periksa hasil di chat Baritone");build();});}
    private void buildTab(){
        int half=(cw-8)/2;
        button(t("From Litematica","Dari Litematica"),cx,cy,half,24,()->{buildMode=0;page=0;build();},t("Build the placement you loaded in Litematica","Bangun placement yang dimuat di Litematica")).selected=buildMode==0;
        button(t("From file","Dari berkas"),cx+half+8,cy,half,24,()->{buildMode=1;page=0;build();},t("Pick a .litematic / .schem / .schematic from the schematics folder","Pilih .litematic / .schem / .schematic dari folder schematics")).selected=buildMode==1;
        if(buildMode==1){schematicFiles();return;}
        placementsPage();
    }
    private void placementsPage(){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("litematica")){
            wrap(t("Litematica is not installed. Install Litematica + MaLiLib for Minecraft 26.2, or use the From file tab to build a schematic file directly.","Litematica belum terpasang. Pasang Litematica + MaLiLib untuk Minecraft 26.2, atau pakai tab Dari berkas untuk membangun berkas skematik langsung."),cx,cy+34,cw,MUTED,6);return;
        }
        List<LitematicaBridge.Placement> loaded;
        try{loaded=LitematicaBridge.placements();}catch(RuntimeException failure){loaded=List.of();notice="! "+error(failure);}
        final int count=loaded.size();
        button(t("Build selected placement","Bangun placement terpilih"),cx,cy+32,cw,30,()->run(()->{buildPlacement(-1);}),t("Builds the placement selected in Litematica (or the first enabled one) from its file, wherever you stand","Membangun placement yang dipilih di Litematica (atau yang pertama aktif) dari berkasnya, di mana pun Anda berdiri")).active=count>0;
        int textY=cy+68;
        if(count==0){wrap(t("No placement loaded. In Litematica: press M → Load schematics → load a file → place it. Then come back here.","Belum ada placement. Di Litematica: tekan M → Load schematics → muat berkas → letakkan. Lalu kembali ke sini."),cx,textY,cw,MUTED,4);return;}
        line(t("Or pick one of the loaded placements:","Atau pilih salah satu placement yang dimuat:"),cx,textY,MUTED);
        int rows=Math.max(1,(ch-textY+cy-21-4)/40);int from=pageStart(count,rows);
        for(int i=from;i<Math.min(count,from+rows);i++){var placement=loaded.get(i);int yy=textY+14+(i-from)*40;
            QButton b=button(placement.index()+" · "+placement.name(),cx,yy,cw,34,()->run(()->buildPlacement(placement.index()-1)),placement.detail()+"\n"+t("Click to build this placement","Klik untuk membangun placement ini"));b.icon=18;b.detail=placement.detail();}
        pager(count,rows);
    }
    private void buildPlacement(int index)throws Exception{
        if(minecraft.level==null)throw new IllegalStateException(t("Enter a world first","Masuk dunia terlebih dahulu"));
        QwenUI.bridge().buildLitematica(index);
        notice=t("Building started · see chat for details","Pembangunan dimulai · lihat chat untuk detail");build();
    }
    private static final java.util.Set<String> SCHEMATIC_EXT=java.util.Set.of("litematic","schem","schematic");
    private List<java.io.File> listSchematics(){
        java.io.File dir=new java.io.File(minecraft.gameDirectory,"schematics");
        List<java.io.File> out=new ArrayList<>();
        try(var walk=java.nio.file.Files.walk(dir.toPath(),3)){
            walk.filter(java.nio.file.Files::isRegularFile).map(java.nio.file.Path::toFile).filter(f->{String n=f.getName().toLowerCase(Locale.ROOT);int dot=n.lastIndexOf('.');return dot>0&&SCHEMATIC_EXT.contains(n.substring(dot+1));}).forEach(out::add);
        }catch(java.io.IOException|RuntimeException ignored){}
        out.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));
        return out;
    }
    private String schematicLabel(java.io.File f){
        java.nio.file.Path root=new java.io.File(minecraft.gameDirectory,"schematics").toPath();
        try{return root.relativize(f.toPath()).toString().replace('\\','/');}catch(RuntimeException e){return f.getName();}
    }
    private void schematicFiles(){
        searchBox=input(cx,cy+32,cw,23,t("Search schematic files…","Cari berkas skematik…"),query,v->{query=v;page=0;restoreSearch=true;rebuild=true;});
        var files=listSchematics().stream().filter(f->schematicLabel(f).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        boolean picked=selectedSchematic!=null&&files.contains(selectedSchematic);
        int footer=picked?34:0;
        int rows=Math.max(1,(ch-87-footer)/40);int from=pageStart(files.size(),rows);
        for(int i=from;i<Math.min(files.size(),from+rows);i++){var f=files.get(i);int yy=cy+63+(i-from)*40;
            String ext=f.getName().substring(f.getName().lastIndexOf('.')+1).toUpperCase(Locale.ROOT);
            String detail=ext+" · "+(f.length()<1024?f.length()+" B":f.length()<1048576?f.length()/1024+" KB":String.format(Locale.ROOT,"%.1f MB",f.length()/1048576.0))+" · "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(f.lastModified()));
            QButton b=button(schematicLabel(f),cx,yy,cw,34,()->{selectedSchematic=f;build();},detail+"\n"+t("Click to select, then press Build","Klik untuk memilih, lalu tekan Bangun"));
            b.icon=13;b.detail=detail;b.selected=f.equals(selectedSchematic);
        }
        if(files.isEmpty())wrap(t("No schematics found in .minecraft/schematics. Save a .litematic from Litematica or copy a file there, then reopen this tab.","Tidak ada skematik di .minecraft/schematics. Simpan .litematic dari Litematica atau salin berkas ke sana, lalu buka tab ini lagi."),cx,cy+67,cw,MUTED,4);
        pager(files.size(),rows);
        if(picked){
            int yy=cy+ch-21-34;int bw=(cw-8)/2;
            var f=selectedSchematic;
            button(t("Build here","Bangun di sini"),cx,yy,bw,28,()->run(()->{
                if(minecraft.level==null)throw new IllegalStateException(t("Enter a world first","Masuk dunia terlebih dahulu"));
                var warnings=QwenUI.bridge().buildSchematic(f);
                for(String w:warnings)minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[QwenUI] "+w));
                notice=t("Building ","Membangun ")+f.getName()+(warnings.isEmpty()?"":" · "+warnings.size()+t(" warning(s) in chat"," peringatan di chat"));build();}),
                t("Loads the file with its origin at your feet and starts building","Memuat berkas dengan titik asal di kaki Anda lalu mulai membangun"));
            button(t("Stop build","Hentikan build"),cx+bw+8,yy,bw,28,()->run(()->{QwenUI.bridge().execute("stop");notice=t("Stopped","Dihentikan");build();}),t("Cancels the current Baritone task","Membatalkan tugas Baritone saat ini"));
        }
    }
    private void reviewSingle(GeminiClient.Action action){
        if(!actions.isEmpty()){tab=Tab.AI;aiConnection=false;reviewing=true;notice=t("Review or reject the pending plan first","Tinjau atau tolak rencana yang masih tertunda");build();return;}
        run(()->{PlanExecutor.validate(List.of(action),QwenUI.bridge().catalog());actions=List.of(action);tab=Tab.AI;aiConnection=false;reviewing=true;reviewIndex=0;detailPage=0;build();});
    }
    private void macros(){
        if(macroEditor){if(macroStepList)macroStepEditor();else macroEditor();return;}
        button(t("+ New macro","+ Makro baru"),cx,cy,cw,25,()->{macroIndex=-1;macroName="";macroCommands="";macroDelay="2";macroRepeat="1";macroEditor=true;macroStepList=false;build();},t("A saved sequence of native Baritone commands","Rangkaian perintah asli Baritone yang disimpan"));
        int rows=Math.max(1,(ch-61)/37),from=pageStart(cfg().macros.size(),rows);
        for(int i=from;i<Math.min(cfg().macros.size(),from+rows);i++){final int idx=i;var m=cfg().macros.get(i);button(m.name+" · "+m.commands.size()+t(" commands"," perintah"),cx,cy+36+(i-from)*37,cw,30,()->{macroIndex=idx;macroName=m.name;macroCommands=String.join("; ",m.commands);macroDelay=""+m.delayTicks;macroRepeat=""+m.repeatCount;macroEditor=true;macroStepList=false;build();},String.join("\n",m.commands));}
        pager(cfg().macros.size(),rows);
    }
    private void macroEditor(){
        button(t("‹ Macros","‹ Makro"),cx,cy,85,22,()->{macroEditor=false;page=0;build();},t("Back to saved macros","Kembali ke makro tersimpan"));
        int yy=cy+33;input(cx,yy,cw,24,t("Macro name","Nama makro"),macroName,s->macroName=s);
        int mh=(cw-8)/2;
        button(t("+ Add feature","+ Tambah fitur"),cx,yy+33,mh,24,this::pickMacroFeature,t("Build macro actions from feature buttons and parameter forms","Buat aksi makro dari tombol fitur dan form parameter"));
        button(t("Steps: ","Langkah: ")+draftSteps().size(),cx+mh+8,yy+33,mh,24,()->{macroStepList=true;page=0;build();},String.join("\n",draftSteps()));
        line(t("Delay (ticks)","Jeda (tick)"),cx,yy+59,MUTED);line(t("Repeats","Pengulangan"),cx+(cw+8)/2,yy+59,MUTED);
        int half=(cw-8)/2;input(cx,yy+72,half,24,t("Delay ticks","Jeda tick"),macroDelay,s->macroDelay=s);input(cx+half+8,yy+72,half,24,t("Repeats (1–100)","Ulang (1–100)"),macroRepeat,s->macroRepeat=s);
        button(t("Save","Simpan"),cx,yy+100,half,25,()->run(()->{saveMacro();notice=t("Macro saved","Makro tersimpan");build();}),t("Save only; does not run","Hanya simpan; tidak menjalankan"));
        button(t("Save + run","Simpan + mulai"),cx+half+8,yy+100,half,25,()->run(()->{if(minecraft.level==null)throw new IllegalStateException(t("Enter a world first","Masuk dunia terlebih dahulu"));saveMacro();QwenUI.macros().start(cfg().macros.get(macroIndex));notice=t("Macro started","Makro dimulai");build();}),t("Run commands sequentially, waiting for tasks to finish","Jalankan berurutan sambil menunggu tugas selesai"));
        if(ch>195){button(QwenUI.macros().isPaused()?t("Resume","Lanjutkan"):t("Pause queue","Jeda antrean"),cx,yy+132,half,23,()->{if(QwenUI.macros().isPaused())QwenUI.macros().resume();else QwenUI.macros().pause();build();},t("Pauses future commands, not an already running path","Menjeda perintah berikutnya, bukan path yang sudah aktif"));button(t("Delete","Hapus"),cx+half+8,yy+132,half,23,()->run(()->{if(macroIndex>=0)cfg().macros.remove(macroIndex);cfg().save();macroEditor=false;page=0;build();}),t("Delete this saved macro","Hapus makro tersimpan ini"));}
    }
    private List<String> draftSteps(){return Arrays.stream(macroCommands.split("[;\\n]+" )).map(String::trim).filter(s->!s.isEmpty()).toList();}
    private void pickMacroFeature(){macroPicking=true;selectedCommand=null;pickerField=null;tab=Tab.COMMANDS;page=0;query="";category="";build();}
    private void macroStepEditor(){
        button(t("‹ Macro","‹ Makro"),cx,cy,85,22,()->{macroStepList=false;page=0;build();},t("Return to macro settings","Kembali ke pengaturan makro"));
        button(t("+ Add feature","+ Tambah fitur"),cx+93,cy,cw-93,22,this::pickMacroFeature,t("Choose a feature and configure its parameters","Pilih fitur dan atur parameternya"));
        var steps=draftSteps();int rows=Math.max(1,(ch-60)/35),start=pageStart(steps.size(),rows);
        for(int i=start;i<Math.min(steps.size(),start+rows);i++){int index=i,yy=cy+34+(i-start)*35;String step=steps.get(i);card(cx,yy,cw,29);line(clip((i+1)+" · "+step,cw-102),cx+7,yy+10,TEXT);
            button("↑",cx+cw-91,yy+3,25,23,()->{var copy=new ArrayList<>(draftSteps());if(index>0)Collections.swap(copy,index,index-1);macroCommands=String.join("; ",copy);build();},t("Move up","Naikkan")).active=i>0;
            button("↓",cx+cw-62,yy+3,25,23,()->{var copy=new ArrayList<>(draftSteps());if(index+1<copy.size())Collections.swap(copy,index,index+1);macroCommands=String.join("; ",copy);build();},t("Move down","Turunkan")).active=i+1<steps.size();
            button("×",cx+cw-33,yy+3,26,23,()->{var copy=new ArrayList<>(draftSteps());copy.remove(index);macroCommands=String.join("; ",copy);build();},t("Remove this step","Hapus langkah ini"));
        }
        if(steps.isEmpty())wrap(t("Your macro is empty. Add actions using feature buttons.","Makro masih kosong. Tambahkan aksi melalui tombol fitur."),cx,cy+42,cw,MUTED,3);
        pager(steps.size(),rows);
    }
    private void saveMacro() throws Exception{
        List<String> list=Arrays.stream(macroCommands.split("[;\\n]+" )).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(macroName.isBlank()||list.isEmpty()||list.size()>100)throw new IllegalArgumentException(t("Name and 1–100 commands required","Nama dan 1–100 perintah wajib diisi"));
        PlanExecutor.validate(List.of(new GeminiClient.Action("macro",macroName,"",list)),QwenUI.bridge().catalog());
        var m=new QwenConfig.Macro(macroName,list,Integer.parseInt(macroDelay),Integer.parseInt(macroRepeat));
        if(macroIndex<0){if(cfg().macros.size()>=128)throw new IllegalStateException(t("Macro limit reached","Batas makro tercapai"));cfg().macros.add(m);macroIndex=cfg().macros.size()-1;}else cfg().macros.set(macroIndex,m);
        cfg().save();
    }
    private void bindCurrentCommand(){run(()->{
        hotkeyCommand="#"+CommandForms.compose(selectedCommand.name(),currentVariant(),formValues);
        hotkeyKey=-1;hotkeyModifiers=0;hotkeyIndex=-1;hotkeyEditor=true;capturingHotkey=false;
        tab=Tab.HOTKEYS;selectedCommand=null;page=0;notice="";build();
    });}
    private void hotkeys(){
        if(hotkeyEditor){hotkeyEditor();return;}
        button(t("+ New command keybind","+ Keybind perintah baru"),cx,cy,cw,25,()->{
            hotkeyIndex=-1;hotkeyCommand="";hotkeyKey=-1;hotkeyModifiers=0;hotkeyEditor=true;capturingHotkey=false;build();
        },t("Bind any registered Baritone # command with parameters","Pasang keybind untuk perintah # Baritone beserta parameter"));
        int rows=Math.max(1,(ch-70)/43),from=pageStart(cfg().commandHotkeys.size(),rows);
        for(int i=from;i<Math.min(cfg().commandHotkeys.size(),from+rows);i++){
            final int index=i;var binding=cfg().commandHotkeys.get(i);int yy=cy+35+(i-from)*43;
            card(cx,yy,cw,37);line(clip(chordLabel(binding.key,binding.modifiers),cw-77),cx+8,yy+5,ACCENT);
            line(clip("#"+binding.command.replaceFirst("^#", ""),cw-77),cx+8,yy+20,MUTED);
            button(t("Edit","Ubah"),cx+cw-63,yy+5,57,26,()->{
                hotkeyIndex=index;hotkeyCommand=binding.command;hotkeyKey=binding.key;hotkeyModifiers=binding.modifiers;hotkeyEditor=true;capturingHotkey=false;build();
            },binding.command);
        }
        if(cfg().commandHotkeys.isEmpty())wrap(t("No shortcuts yet. Add one here, or open a # command form and choose Keybind. Commands run on a new key press, never while typing in a screen.","Belum ada shortcut. Tambahkan di sini, atau buka form perintah # lalu pilih Keybind. Perintah hanya berjalan pada penekanan baru, tidak saat mengetik di layar."),cx,cy+43,cw,MUTED,5);
        pager(cfg().commandHotkeys.size(),rows);
    }
    private void hotkeyEditor(){
        button(t("‹ Keybinds","‹ Keybind"),cx,cy,100,22,()->{hotkeyEditor=false;capturingHotkey=false;page=0;build();},t("Back without saving draft","Kembali tanpa menyimpan draf"));
        line(t("Baritone command (including parameters)","Perintah Baritone (beserta parameter)"),cx,cy+34,MUTED);
        input(cx,cy+49,cw,26,t("Example: #mine minecraft:diamond_ore","Contoh: #mine minecraft:diamond_ore"),hotkeyCommand,v->hotkeyCommand=v);
        button(capturingHotkey?t("Press a key combination… Esc cancels","Tekan kombinasi tombol… Esc membatalkan"):chordLabel(hotkeyKey,hotkeyModifiers),cx,cy+86,cw,29,()->{capturingHotkey=true;setFocused(null);build();},t("Hold Ctrl, Shift and/or Alt, then press the main key","Tahan Ctrl, Shift dan/atau Alt, lalu tekan tombol utama"));
        wrap(t("Click the button above to record. Modifiers must match exactly. Duplicate shortcuts and reserved open/stop keys are rejected. Nothing runs while this screen is open.","Klik tombol di atas untuk merekam. Modifier harus cocok persis. Shortcut duplikat dan tombol buka/stop yang dicadangkan ditolak. Tidak ada perintah berjalan saat layar ini terbuka."),cx,cy+128,cw,MUTED,5);
        int half=(cw-8)/2,yy=cy+ch-27;
        button(t("Save keybind","Simpan keybind"),cx,yy,half,27,()->run(()->{
            saveHotkey();notice=t("Keybind saved","Keybind tersimpan");hotkeyEditor=false;capturingHotkey=false;page=0;build();
        }),t("Save only; does not execute the command","Hanya simpan; tidak menjalankan perintah"));
        button(t("Delete","Hapus"),cx+half+8,yy,half,27,()->run(()->{
            if(hotkeyIndex>=0)cfg().commandHotkeys.remove(hotkeyIndex);cfg().save();hotkeyEditor=false;capturingHotkey=false;page=0;build();
        }),t("Remove this saved shortcut","Hapus shortcut tersimpan ini")).active=hotkeyIndex>=0;
    }
    private String chordLabel(int key,int modifiers){
        if(key<0)return t("Click to record a key combination","Klik untuk merekam kombinasi tombol");
        StringBuilder label=new StringBuilder();
        if((modifiers&GLFW.GLFW_MOD_CONTROL)!=0)label.append("Ctrl + ");
        if((modifiers&GLFW.GLFW_MOD_SHIFT)!=0)label.append("Shift + ");
        if((modifiers&GLFW.GLFW_MOD_ALT)!=0)label.append("Alt + ");
        return label.append(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString()).toString();
    }
    private void saveHotkey() throws Exception {
        // Validation is shared with JSON loading and runtime dispatch.
        var binding=new CommandHotkeys.Binding(hotkeyCommand,hotkeyKey,hotkeyModifiers);
        CommandHotkeys.validateForSave(binding,cfg().commandHotkeys,hotkeyIndex);
        if(QwenUI.isReservedCommandKey(binding.key))throw new IllegalArgumentException(t("This key is reserved for opening QwenUI or stopping tasks","Tombol ini dicadangkan untuk membuka QwenUI atau menghentikan tugas"));
        String head=binding.command.strip().replaceFirst("^#", "").strip().split("\\s+",2)[0];
        if(QwenUI.bridge().allCommands().stream().noneMatch(c->c.name().equalsIgnoreCase(head)||c.aliases().stream().anyMatch(a->a.equalsIgnoreCase(head))))throw new IllegalArgumentException(t("Unknown Baritone command","Perintah Baritone tidak dikenal"));
        if(hotkeyIndex<0)cfg().commandHotkeys.add(binding);else cfg().commandHotkeys.set(hotkeyIndex,binding);
        cfg().save();
    }
    private void preferences(){
        int gap=6,h=24,y=cy,half=(cw-gap)/2;
        button(t("Language: English","Bahasa: Indonesia"),cx,y,cw,h,()->{cfg().language=id()?"en_us":"id_id";persist();build();},t("Switch between English and Indonesian","Ganti antara Inggris dan Indonesia"));y+=h+gap;
        // UI scale: whole-number steps only (crisp text). 0 = automatic.
        var window=minecraft.getWindow();int maxFit=UiPreferences.maxRenderScale(window.getWidth(),window.getHeight());
        int current=Gfx.pixelsPerUnit,requested=Math.min(cfg().uiScalePx,maxFit);
        line(t("UI scale · whole numbers keep text sharp","Skala UI · bilangan bulat agar teks tajam"),cx,y,MUTED);y+=12;
        button("−",cx,y,30,h,()->{cfg().uiScalePx=Math.max(0,Math.min(cfg().uiScalePx,maxFit)-1);windowX=-1;windowY=-1;persist();build();},t("Smaller UI","UI lebih kecil")).active=requested>0;
        QButton label=button(requested==0?t("Automatic","Otomatis")+" ("+current+"×)":requested+"×",cx+30+gap,y,cw-60-2*gap,h,()->{cfg().uiScalePx=0;windowX=-1;windowY=-1;persist();build();},t("Click to return to automatic scale","Klik untuk kembali ke skala otomatis"));label.selected=requested==0;
        button("+",cx+cw-30,y,30,h,()->{cfg().uiScalePx=Math.min(maxFit,Math.max(cfg().uiScalePx,0)+1);windowX=-1;windowY=-1;persist();build();},t("Bigger UI (limited by window size)","UI lebih besar (dibatasi ukuran jendela)")).active=requested<maxFit;
        y+=h+4;
        line(t("Panel area: ","Area panel: ")+logicalWidth()+" × "+logicalHeight()+t(" units · max scale "," unit · skala maks ")+maxFit+"×",cx,y,MUTED);y+=14;
        button(t("Blur: ","Blur: ")+(cfg().blurBackground?"ON":"OFF"),cx,y,half,h,()->{cfg().blurBackground=!cfg().blurBackground;persist();build();},t("Blur the game behind the panel","Blur dunia game di belakang panel"));
        button(t("Animation: ","Animasi: ")+(cfg().animation?"ON":"OFF"),cx+half+gap,y,half,h,()->{cfg().animation=!cfg().animation;persist();build();},t("Smooth hover effects","Efek hover halus"));y+=h+gap;
        button(t("Resize panel","Ukuran panel"),cx,y,half,h,this::enterResize,t("Drag the panel edges to resize it","Tarik tepi panel untuk mengubah ukuran"));
        button(t("Reset layout","Reset tampilan"),cx+half+gap,y,half,h,()->{cfg().customWindowSize=false;cfg().uiScalePx=0;windowX=-1;windowY=-1;persist();build();},t("Automatic panel size and UI scale","Ukuran panel dan skala UI otomatis"));y+=h+gap;
        int room=(cy+ch-y)/12;
        if(room>0)wrap(t("AI actions run directly. End / Stop stops tasks; changes already made are not undone.","Aksi AI langsung berjalan. End / Hentikan menghentikan tugas; perubahan yang sudah terjadi tidak dibatalkan."),cx,y,cw,MUTED,room);
    }
    private void ai(){
        if(reviewing&&!actions.isEmpty()){review();return;}
        if(aiConnection){connection();return;}
        int apiW=Math.min(94,cw/4),testW=42,reviewW=Math.min(90,cw/4);
        button(t("API / models","API / model"),cx,cy,apiW,23,()->{aiConnection=true;page=0;build();},t("Enter your own key and discover available models","Masukkan key sendiri dan temukan model tersedia")).icon=6;
        button("Test",cx+apiW+5,cy,testW,23,this::testAi,t("Real connectivity test; uses API quota, executes no action","Tes koneksi nyata; memakai kuota API, tanpa menjalankan aksi")).active=!busy;
        QButton delete=button("",cx+apiW+testW+10,cy,27,23,()->{confirmDelete=!confirmDelete;build();},t("Delete conversation, proposed actions and interactive controls","Hapus percakapan, usulan aksi dan tombol interaktif"));delete.icon=8;delete.setMessage(component(t("Delete chat","Hapus chat")));delete.iconOnly=true;
        if(!actions.isEmpty())button(t("Review ","Tinjau ")+actions.size(),cx+cw-reviewW,cy,reviewW,23,()->{reviewing=true;reviewIndex=0;detailPage=0;build();},t("Read all proposed actions","Baca semua usulan aksi"));
        if(confirmDelete){
            wrap(t("Delete this conversation? Pending requests will be cancelled. Your API key and model stay unchanged.","Hapus percakapan ini? Permintaan tertunda akan dibatalkan. API key dan model tetap tersimpan."),cx,cy+42,cw,TEXT,5);
            int half=(cw-8)/2;button(t("Delete chat","Hapus chat"),cx,cy+112,half,27,this::clearConversation,t("Cannot undo chat deletion; does not stop world tasks","Tidak dapat mengurungkan penghapusan chat; tidak menghentikan tugas dunia")).icon=8;
            button(t("Cancel","Batal"),cx+half+8,cy+112,half,27,()->{confirmDelete=false;build();},t("Keep conversation","Pertahankan percakapan"));return;
        }
        String model=cfg().model.replace("models/","");line(clip(model.isBlank()?t("No model selected","Belum memilih model"):model,cw),cx,cy+31,MUTED);
        List<GeminiClient.Control> controls=aiControls.isEmpty()?List.of(new GeminiClient.Control("page","commands",t("Command features","Fitur perintah")),new GeminiClient.Control("page","build",t("Build","Bangun"))):aiControls;
        int controlsH=controls.size()>2?49:29;
        int chatTop=cy+48,chatH=Math.max(25,ch-97-controlsH);boxes.add(new Box(cx,chatTop,cw,chatH,0xFF1C1C1C));
        chatX=cx;chatY=chatTop;chatWidth=cw;chatHeight=chatH;
        if(history.isEmpty()){
            wrap(t("How can I help?","Apa yang bisa saya bantu?"),cx+14,chatTop+13,cw-28,TEXT,2);
            if(chatH>70)wrap(t("Chat about anything, ask for feature buttons, or request mod actions. Actions run directly. Chat and live mod context go to Google.","Tanya apa saja, minta tombol fitur, atau perintah mod. Aksi langsung dijalankan. Chat dan konteks mod aktif dikirim ke Google."),cx+14,chatTop+42,cw-28,MUTED,Math.max(1,(chatH-50)/12));
        }else{
            int maxBubble=Math.max(50,cw-28),total=8;
            List<ChatBubble> measured=new ArrayList<>();
            for(var message:history){
                boolean user=message.role().equals("user");Component heading=component(user?t("YOU","ANDA"):"QWEN AI");
                List<FormattedCharSequence> body=font.split(component(message.text()),Math.max(1,maxBubble-20));
                if(body.isEmpty())body=List.of(FormattedCharSequence.EMPTY);
                var size=ChatBubbleLayout.measure(maxBubble,font.width(heading)+24,body.stream().map(font::width).toList());
                int xx=user?cx+cw-8-size.width():cx+8;
                measured.add(new ChatBubble(xx,total,size.width(),size.height(),user,heading,body));
                total+=size.height()+10;
            }
            chatOverflow=Math.max(0,total-chatH);chatScroll=Math.max(0,Math.min(chatScroll,chatOverflow));
            int offset=chatTop-Math.max(0,total-chatH)+chatScroll;
            for(var bubble:measured)chatBubbles.add(new ChatBubble(bubble.x(),bubble.y()+offset,bubble.width(),bubble.height(),bubble.user(),bubble.heading(),bubble.body()));
        }
        int controlY=chatTop+chatH+5,half=(cw-6)/2,pages=Math.max(1,(controls.size()+1)/2);controlsPage=Math.max(0,Math.min(controlsPage,pages-1));
        for(int i=controlsPage*2;i<Math.min(controls.size(),controlsPage*2+2);i++){var control=controls.get(i);QButton b=button(control.label(),cx+(i%2)*(half+6),controlY,half,23,()->activateControl(control),control.type()+" · "+control.name());b.icon=control.type().equals("setting")?6:control.type().equals("litematica")?3:2;b.active=controlAvailable(control);}
        if(pages>1){button("‹",cx,controlY+26,25,18,()->{controlsPage=Math.max(0,controlsPage-1);build();},t("Previous features","Fitur sebelumnya")).active=controlsPage>0;line(t("Features ","Fitur ")+(controlsPage+1)+" / "+pages,cx+33,controlY+31,MUTED);button("›",cx+cw-25,controlY+26,25,18,()->{controlsPage=Math.min(pages-1,controlsPage+1);build();},t("More features","Fitur lainnya")).active=controlsPage+1<pages;}
        promptBox=input(cx,cy+ch-36,cw-46,29,t("Message Qwen AI…","Pesan untuk Qwen AI…"),prompt,s->prompt=s);promptBox.setMaxLength(20000);
        QButton sendButton=button(busy?"…":"↑",cx+cw-38,cy+ch-36,38,29,this::send,t("Send message · Enter · may use Google API quota","Kirim pesan · Enter · dapat memakai kuota API Google"));sendButton.active=!busy;sendButton.selected=true;
    }
    private void clearConversation(){
        requests.invalidate();var pending=request;request=null;if(pending!=null)pending.cancel(true);
        busy=false;history.clear();actions=List.of();aiControls=List.of();reviewing=false;confirmDelete=false;prompt="";chatScroll=0;controlsPage=0;notice=t("Conversation deleted","Percakapan dihapus");build();
    }
    private boolean controlAvailable(GeminiClient.Control control){
        return switch(control.type()){
            case "command"->QwenUI.bridge().allCommands().stream().anyMatch(c->c.name().equalsIgnoreCase(control.name())||c.aliases().stream().anyMatch(v->v.equalsIgnoreCase(control.name())));
            case "setting"->QwenUI.bridge().allSettings().stream().anyMatch(c->c.name().equalsIgnoreCase(control.name()));
            case "page"->Arrays.stream(Tab.values()).anyMatch(v->v.name().equalsIgnoreCase(control.name().equalsIgnoreCase("litematica")?"build":control.name()));
            case "litematica"->LitematicaBridge.isAvailable(control.name());default->false;
        };
    }
    private void activateControl(GeminiClient.Control control){run(()->{
        if(!controlAvailable(control))throw new IllegalArgumentException(t("Feature unavailable","Fitur tidak tersedia"));
        switch(control.type()){
            case "page"->switchTo(Tab.valueOf((control.name().equalsIgnoreCase("litematica")?"build":control.name()).toUpperCase(Locale.ROOT)));
            case "command"->{switchTo(Tab.COMMANDS);selectedCommand=QwenUI.bridge().allCommands(cfg().language).stream().filter(c->c.name().equalsIgnoreCase(control.name())||c.aliases().stream().anyMatch(v->v.equalsIgnoreCase(control.name()))).findFirst().orElseThrow();formVariant=0;formPage=0;formValues.clear();build();}
            case "setting"->{switchTo(Tab.SETTINGS);openSetting(QwenUI.bridge().allSettings(cfg().language).stream().filter(v->v.name().equalsIgnoreCase(control.name())).findFirst().orElseThrow());}
            case "litematica"->{acceptReply(new GeminiClient.Reply(t("Running requested Litematica feature.","Menjalankan fitur Litematica yang diminta."),List.of(new GeminiClient.Action("litematica",control.name(),"",List.of())),aiControls));build();}
            default->throw new IllegalArgumentException("Unsupported control");
        }
    });}
    private void testAi(){
        if(busy)return;
        if(apiKey.isBlank()||cfg().model.isBlank()){aiConnection=true;notice=t("Enter your key and select a model before testing","Masukkan key sendiri dan pilih model sebelum tes");build();return;}
        busy=true;notice=t("Testing AI connection…","Menguji koneksi AI…");build();
        final long epoch=requests.begin();
        run(()->{var future=new GeminiClient(apiKey.trim()).testConnection(cfg().model,id()?"Bahasa Indonesia":"English");request=future;
            future.whenComplete((reply,failure)->minecraft.execute(()->{if(closed||!requests.accepts(epoch))return;busy=false;
                if(failure!=null)notice=t("! AI test failed: ","! Tes AI gagal: ")+error(failure);
                else{notice=t("AI test successful · no actions executed","Tes AI berhasil · tidak ada aksi dijalankan");}
                build();}));});
    }
    private void connection(){
        button(t("‹ Chat","‹ Chat"),cx,cy,70,22,()->{aiConnection=false;build();},t("Return to conversation","Kembali ke percakapan"));
        button(busy?"…":"Test",cx+cw-60,cy,60,22,this::testAi,t("Test the selected model with your own key; uses API quota","Tes model terpilih dengan key sendiri; memakai kuota API")).active=!busy;
        EditBox key=input(cx,cy+31,cw,24,t("Your Gemini API key","API key Gemini Anda"),apiKey,s->{apiKey=s;cfg().apiKey=s;});
        key.addFormatter((s,pos)->FormattedCharSequence.forward("•".repeat(s.length()),textStyle()));
        button(cfg().rememberApiKey?t("Remember key: ON (plaintext)","Ingat key: ON (teks biasa)"):t("Remember key: OFF","Ingat key: OFF"),cx,cy+62,cw,24,()->{cfg().rememberApiKey=!cfg().rememberApiKey;cfg().apiKey=apiKey;persist();build();},t("Default OFF: key stays in memory. ON saves plaintext in config/qwenui.json. Never share this file.","Bawaan OFF: key hanya di memori. ON menyimpan teks biasa di config/qwenui.json. Jangan bagikan file ini."));
        int bw=Math.min(85,cw/3);searchBox=input(cx,cy+95,cw-bw-6,24,t("Filter all models…","Cari semua model…"),modelQuery,s->{modelQuery=s;page=0;restoreSearch=true;rebuild=true;});
        button(busy?"…":t("Refresh","Muat ulang"),cx+cw-bw,cy+95,bw,24,this::loadModels,t("Load every page from the official Gemini Models API","Muat semua halaman dari Gemini Models API resmi")).active=!busy;
        List<GeminiClient.ModelInfo> filtered=models.stream().filter(m->(m.name()+m.displayName()).toLowerCase(Locale.ROOT).contains(modelQuery.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(ch-150)/31),from=pageStart(filtered.size(),rows);
        for(int i=from;i<Math.min(filtered.size(),from+rows);i++){var m=filtered.get(i);QButton b=button(m.name().replace("models/","")+(m.supportsGenerateContent()?"":" · API only"),cx,cy+128+(i-from)*31,cw,25,()->{cfg().model=m.name();persist();notice=t("Model selected","Model dipilih");build();},m.displayName()+"\n"+m.description()+"\n"+String.join(", ",m.methods()));b.active=m.supportsGenerateContent();b.selected=cfg().model.equals(m.name());}
        pager(filtered.size(),rows);
    }
    private void loadModels(){
        if(busy)return;if(apiKey.isBlank()){notice=t("! Enter your API key first","! Masukkan API key dahulu");return;}
        busy=true;cfg().apiKey=apiKey;notice=t("Loading every model page…","Memuat seluruh halaman model…");build();
        final long epoch=requests.begin();
        run(()->{var future=new GeminiClient(apiKey.trim()).listModels();request=future;future.whenComplete((result,failure)->minecraft.execute(()->{if(closed||!requests.accepts(epoch))return;busy=false;if(failure!=null)notice="! "+error(failure);else{models=List.copyOf(result);page=0;notice=models.size()+t(" models discovered; availability depends on your account"," model ditemukan; akses mengikuti akun Anda");}build();}));});
    }
    /** Single client-thread reply path, also exercised by the separate offline UI harness. */
    private void acceptReply(GeminiClient.Reply reply){
        history.add(new GeminiClient.ChatMessage("assistant",reply.message()));
        aiControls=reply.controls();controlsPage=0;actions=List.of();reviewing=false;chatScroll=0;
        if(reply.actions().isEmpty()){notice=t("Response ready","Jawaban siap");return;}
        try{
            String result=PlanExecutor.execute(List.copyOf(reply.actions()));notice=result;
            aiQueueTracking=reply.actions().stream().anyMatch(a->a.type().equals("command"))&&QwenUI.macros().isRunning();
            if(Boolean.getBoolean("qwenui.layoutAudit"))System.out.println("QWEN_AI_EXECUTION direct=true actions="+reply.actions().size()+" review=false queued="+aiQueueTracking);
            history.add(new GeminiClient.ChatMessage("assistant",t("Execution status: ","Status eksekusi: ")+result));
        }catch(Exception failure){
            notice="! "+error(failure);
            history.add(new GeminiClient.ChatMessage("assistant",t("Execution failed: ","Eksekusi gagal: ")+error(failure)+t(". Earlier changes, if any, may remain; check the world and Baritone log.",". Perubahan sebelumnya, jika ada, dapat tetap berlaku; periksa dunia dan log Baritone.")));
        }
    }
    private void send(){
        if(busy)return;if(apiKey.isBlank()||cfg().model.isBlank()){aiConnection=true;notice=t("Enter a key and select a model","Masukkan key dan pilih model");build();return;}
        if(prompt.isBlank())return;String ask=prompt;prompt="";List<GeminiClient.ChatMessage> previous=List.copyOf(history);history.add(new GeminiClient.ChatMessage("user",ask));
        if(history.size()>60){history.remove(0);history.remove(0);previous=List.copyOf(history.subList(0,history.size()-1));}
        busy=true;chatScroll=0;build();
        final var context=previous;final long epoch=requests.begin();
        run(()->{String catalog=new Gson().toJson(QwenUI.bridge().catalog(cfg().language));var future=new GeminiClient(apiKey.trim()).ask(cfg().model,id()?"Bahasa Indonesia":"English",catalog,context,ask);request=future;future.whenComplete((reply,failure)->minecraft.execute(()->{if(closed||!requests.accepts(epoch))return;busy=false;if(failure!=null){notice="! "+error(failure);history.add(new GeminiClient.ChatMessage("assistant",t("Request failed; no action was executed.","Permintaan gagal; tidak ada aksi dijalankan.")));}else{acceptReply(reply);}build();}));});
    }
    private void review(){
        reviewIndex=Math.max(0,Math.min(reviewIndex,actions.size()-1));var action=actions.get(reviewIndex);
        button(t("‹ Chat","‹ Chat"),cx,cy,65,22,()->{reviewing=false;build();},t("Return without applying","Kembali tanpa menerapkan"));
        button("‹",cx+cw-65,cy,28,22,()->{reviewIndex=Math.max(0,reviewIndex-1);detailPage=0;build();},t("Previous action","Tindakan sebelumnya"));button("›",cx+cw-30,cy,28,22,()->{reviewIndex=Math.min(actions.size()-1,reviewIndex+1);detailPage=0;build();},t("Next action","Tindakan berikutnya"));
        line(t("Action ","Tindakan ")+(reviewIndex+1)+" / "+actions.size()+" · "+action.type(),cx,cy+32,ACCENT);
        String detail=switch(action.type()){case "command"->PlanExecutor.commandText(action);case "setting"->action.name()+" = "+action.value();case "macro"->action.name()+"\n"+String.join("\n",action.commands());case "litematica"->QwenUI.bridge().catalog().litematicaFeatures().stream().filter(f->f.id().equals(action.name())).map(f->f.label()+"\n"+f.description()+"\n"+f.id()).findFirst().orElse(action.name());default->action.toString();};
        List<FormattedCharSequence> full=font.split(component(detail),cw-16);int visible=Math.max(1,(ch-110)/12),pages=Math.max(1,(full.size()+visible-1)/visible);detailPage=Math.max(0,Math.min(detailPage,pages-1));card(cx,cy+50,cw,Math.max(30,ch-103));
        int start=detailPage*visible;for(int i=start;i<Math.min(full.size(),start+visible);i++)formatted(full.get(i),cx+8,cy+57+(i-start)*12,TEXT);
        if(pages>1)button(t("Text page ","Halaman teks ")+(detailPage+1)+"/"+pages,cx,cy+ch-49,cw,20,()->{detailPage=(detailPage+1)%pages;build();},t("Read the complete action text; no lines are hidden permanently","Baca teks tindakan lengkap; semua baris dapat dibuka"));
        int half=(cw-8)/2;button(t("Apply all actions","Terapkan semua aksi"),cx,cy+ch-23,half,23,()->run(()->{try{notice=PlanExecutor.execute(List.copyOf(actions));}finally{actions=List.of();reviewing=false;build();}}),t("Approve all proposed settings and command queue. Can change your world.","Setujui seluruh usulan pengaturan dan antrean perintah. Dapat mengubah dunia Anda."));
        button(t("Reject plan","Tolak rencana"),cx+half+8,cy+ch-23,half,23,()->{actions=List.of();reviewing=false;notice=t("Plan rejected","Rencana ditolak");build();},t("Discard every proposed action","Buang seluruh usulan aksi"));
    }
    private void stopAll(){run(()->{aiQueueTracking=false;QwenUI.macros().cancel();if(minecraft.level!=null)QwenUI.bridge().execute("stop");notice=t("All tasks stopped","Seluruh tugas dihentikan");build();});}
    private int pageStart(int total,int rows){int pages=Math.max(1,(total+rows-1)/rows);page=Math.max(0,Math.min(page,pages-1));return page*rows;}
    private void pager(int total,int rows){int pages=Math.max(1,(total+rows-1)/rows);int yy=cy+ch-21;button("‹",cx,yy,30,21,()->{page=Math.max(0,page-1);build();},t("Previous page","Halaman sebelumnya")).active=page>0;line((page+1)+" / "+pages+" · "+total,cx+40,yy+6,MUTED);button("›",cx+cw-30,yy,30,21,()->{page=Math.min(pages-1,page+1);build();},t("Next page","Halaman berikutnya")).active=page+1<pages;}
    private void persist(){run(()->cfg().save());}
    @FunctionalInterface private interface Task {void run() throws Exception;}
    private void run(Task action){try{action.run();}catch(Exception failure){busy=false;notice="! "+error(failure);rebuild=true;}}
    private String error(Throwable failure){while(failure.getCause()!=null&&(failure instanceof java.util.concurrent.CompletionException||failure instanceof java.lang.reflect.InvocationTargetException))failure=failure.getCause();String value=failure.getMessage();if(value==null)value=failure.getClass().getSimpleName();if(!apiKey.isBlank())value=value.replace(apiKey,"[redacted]");return value;}
    private String clip(String s,int maxWidth){if(s==null)return "";if(maxWidth<2)return "";int end=s.length();while(end>0&&font.width(component(s.substring(0,end)+(end<s.length()?"…":"")))>maxWidth)end--;return s.substring(0,end)+(end<s.length()?"…":"");}
    private void line(String s,int x,int y,int color){lines.add(new Line(component(clip(s,Math.max(0,cx+cw-x))),x,y,color));}
    private void formatted(FormattedCharSequence s,int x,int y,int color){lines.add(new Line(Component.literal("").append(Component.literal(toPlain(s))).withStyle(textStyle()),x,y,color));}
    private static String toPlain(FormattedCharSequence seq){StringBuilder out=new StringBuilder();seq.accept((index,style,code)->{out.appendCodePoint(code);return true;});return out.toString();}
    private void wrap(String s,int x,int y,int w,int color,int maxLines){List<FormattedCharSequence> split=font.split(component(s),Math.max(20,w));for(int i=0;i<Math.min(split.size(),maxLines);i++)formatted(split.get(i),x,y+i*12,color);}
    private void card(int x,int y,int w,int h){boxes.add(new Box(x,y,w,h,0xFF242424));}
    private EditBox input(int x,int y,int w,int h,String hint,String initial,Consumer<String> change){
        boxes.add(new Box(x,y,w,h,0xFF303030));EditBox box=new EditBox(font,x+7,y+6,Math.max(20,w-14),h-8,component(hint)){
            @Override protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor g,int mx,int my){ /* scheduled in viewport coordinates by QwenScreen */ }
        };box.setBordered(false);box.setMaxLength(4096);box.setValue(initial);box.setHint(component(hint));box.setTextColor(TEXT);box.addFormatter((s,pos)->FormattedCharSequence.forward(s,textStyle()));box.setResponder(change);box.setTooltip(Tooltip.create(component(hint)));widgetTooltips.put(box,component(hint));return addRenderableWidget(box);
    }
    private QButton button(String label,int x,int y,int w,int h,Runnable action,String tooltip){QButton button=new QButton(x,y,Math.max(18,w),h,component(label),action);button.setTooltip(Tooltip.create(component(tooltip)));widgetTooltips.put(button,component(tooltip));return addRenderableWidget(button);}
    @Override public void tick(){
        super.tick();boolean running=QwenUI.macros().isRunning();
        if(aiQueueTracking&&!running){
            aiQueueTracking=false;String failure=QwenUI.macros().lastFailure();
            String status=failure.isBlank()?t("Command queue finished; the last Baritone task may still be active.","Antrean perintah selesai; tugas Baritone terakhir mungkin masih berjalan."):t("Command queue failed: ","Antrean perintah gagal: ")+failure;
            history.add(new GeminiClient.ChatMessage("assistant",status));chatScroll=0;if(tab==Tab.AI){notice=status;rebuild=true;}
        }
        if(tab==Tab.MACROS){if(running)notice=(QwenUI.macros().isPaused()?t("Queue paused: ","Antrean dijeda: "):t("Macro active: ","Makro aktif: "))+QwenUI.macros().currentMacroName();else if(macroWasRunning)notice=QwenUI.macros().lastFailure().isBlank()?t("Queue finished; the final task may still be active.","Antrean selesai; tugas terakhir mungkin masih aktif."):"! "+QwenUI.macros().lastFailure();}
        macroWasRunning=running;if(rebuild)build();
    }
    @Override public boolean keyPressed(KeyEvent key){if(resizeMode){if(key.key()==GLFW.GLFW_KEY_ESCAPE){finishResize(false);return true;}if(key.key()==GLFW.GLFW_KEY_ENTER){finishResize(true);return true;}}if(capturingHotkey){
            if(key.key()==GLFW.GLFW_KEY_ESCAPE){capturingHotkey=false;build();return true;}
            if(key.key()>=GLFW.GLFW_KEY_LEFT_SHIFT&&key.key()<=GLFW.GLFW_KEY_RIGHT_SUPER)return true;
            hotkeyKey=key.key();hotkeyModifiers=key.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT|GLFW.GLFW_MOD_ALT);capturingHotkey=false;build();return true;
        }if(QwenUI.isStopKey(key)){stopAll();return true;}if(key.key()==GLFW.GLFW_KEY_ESCAPE||QwenUI.isOpenKey(key)){onClose();return true;}if(tab==Tab.AI&&!aiConnection&&!reviewing&&key.key()==GLFW.GLFW_KEY_ENTER&&promptBox!=null&&promptBox.isFocused()){send();return true;}if(tab==Tab.COMMANDS&&selectedCommand!=null&&pickerField==null&&key.key()==GLFW.GLFW_KEY_TAB){for(var entry:formInputs.entrySet())if(entry.getKey().isFocused()){openParameterPicker(entry.getValue());return true;}}if(tab==Tab.COMMANDS&&commandBox!=null&&commandBox.isFocused()&&key.key()==GLFW.GLFW_KEY_TAB){run(()->{var suggestions=QwenUI.bridge().suggestions(command);if(!suggestions.isEmpty()){command=command.substring(0,command.lastIndexOf(' ')+1)+suggestions.get(0);build();}});return true;}return super.keyPressed(key);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(tab==Tab.COMMANDS&&selectedCommand!=null&&pickerField==null){formPage=Math.max(0,formPage+(vertical>0?-1:1));build();return true;}if(tab==Tab.AI&&!aiConnection&&!reviewing){chatScroll=Math.max(0,chatScroll+(vertical>0?39:-39));build();return true;}if(tab==Tab.AI&&reviewing){detailPage=Math.max(0,detailPage+(vertical>0?-1:1));build();return true;}page=Math.max(0,page+(vertical>0?-1:1));build();return true;}
    private MouseButtonEvent scaled(MouseButtonEvent e){return new MouseButtonEvent(e.x()/uiScale,e.y()/uiScale,e.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        var point=scaled(e);
        if(resizeMode&&e.buttonInfo().button()==0){
            if(super.mouseClicked(point,twice))return true;
            dragStart=new WindowResize.Rect(px,py,pw,ph);dragEdges=WindowResize.hit(dragStart,point.x(),point.y(),7);dragX=point.x();dragY=point.y();return dragEdges!=0;
        }
        return super.mouseClicked(point,twice);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){if(resizeMode&&dragEdges!=0){dragEdges=0;return true;}return super.mouseReleased(scaled(e));}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(resizeMode&&dragEdges!=0){var point=scaled(e);var rect=WindowResize.drag(dragStart,dragEdges,(int)Math.round(point.x()-dragX),(int)Math.round(point.y()-dragY),logicalWidth(),logicalHeight());cfg().uiWidth=rect.width();cfg().uiHeight=rect.height();windowX=rect.x();windowY=rect.y();build();return true;}
        return super.mouseDragged(scaled(e),dx/uiScale,dy/uiScale);
    }
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/uiScale,y/uiScale);}
    @Override public void onClose(){if(resizeMode)finishResize(false);closed=true;requests.invalidate();if(request!=null)request.cancel(true);cfg().apiKey=apiKey;persist();minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(Boolean.getBoolean("qwenui.layoutAudit")&&(renderedBlur==null||renderedBlur!=cfg().blurBackground)){renderedBlur=cfg().blurBackground;System.out.println("QWEN_BACKDROP blur="+renderedBlur+" alpha="+(UiPreferences.BACKDROP_COLOR>>>24));}
        if(minecraft.level==null)extractPanorama(g,delta);
        if(cfg().blurBackground)extractBlurredBackground(g);
        g.fill(0,0,width,height,UiPreferences.BACKDROP_COLOR);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.pose().pushMatrix();g.pose().scale(uiScale,uiScale);
        round(g,px,py,pw,ph,12,DARK);round(g,px,py,cx-px-10,ph,12,0xFF202020);
        g.blit(LOGO,px+12,py+15,px+40,py+43,0,1,0,1);
        drawText(g,component("QWEN"),px+47,py+17,ACCENT);drawText(g,component("BARITONE"),px+47,py+32,MUTED);
        for(Box b:boxes)round(g,b.x,b.y,b.w,b.h,6,b.color);
        if(chatHeight>0){
            g.enableScissor(chatX,chatY,chatX+chatWidth,chatY+chatHeight);
            for(var bubble:chatBubbles){
                if(bubble.y()+bubble.height()<=chatY||bubble.y()>=chatY+chatHeight)continue;
                round(g,bubble.x(),bubble.y(),bubble.width(),bubble.height(),9,bubble.user()?0xFF363636:0xFF272727);
                if(bubble.user())drawIcon(g,bubble.x()+10,bubble.y()+9,7,MUTED);
                else g.blit(LOGO,bubble.x()+8,bubble.y()+6,bubble.x()+26,bubble.y()+24,0,1,0,1);
                drawText(g,bubble.heading(),bubble.x()+32,bubble.y()+10,MUTED);
                int yy=bubble.y()+27;
                for(var text:bubble.body()){drawText(g,text,bubble.x()+10,yy,TEXT);yy+=13;}
            }
            if(chatOverflow>0){
                int track=Math.max(1,chatHeight-8),thumb=Math.max(12,(int)((long)track*chatHeight/(chatHeight+chatOverflow)));
                int top=chatY+4+(int)((long)(track-thumb)*(chatOverflow-chatScroll)/chatOverflow);
                round(g,chatX+chatWidth-4,chatY+4,2,track,1,0xFF303030);
                round(g,chatX+chatWidth-4,top,2,thumb,1,0xFF8C8C8C);
            }
            g.disableScissor();
        }
        if(resizeMode){
            g.fill(px,py,px+pw,py+2,LIGHT);g.fill(px,py+ph-2,px+pw,py+ph,LIGHT);g.fill(px,py,px+2,py+ph,LIGHT);g.fill(px+pw-2,py,px+pw,py+ph,LIGHT);
            for(int i=0;i<3;i++)g.fill(px+pw-5-i*4,py+ph-6-i*4,px+pw-3-i*4,py+ph-4-i*4,LIGHT);
        }
        g.enableScissor(cx-1,py+10,px+pw-8,py+ph-29);for(Line l:lines)drawText(g,l.text,l.x,l.y,l.color);g.disableScissor();
        super.extractRenderState(g,(int)(mx/uiScale),(int)(my/uiScale),delta);
        if(busy){int shift=cfg().animation?(int)((System.currentTimeMillis()/160)%4):0;for(int i=0;i<4;i++)round(g,cx+i*9,py+ph-19,5,5,2,i==shift?ACCENT:0xFF484848);}
        if(!notice.isBlank())drawText(g,component(clip(notice,cw-(busy?42:0))),cx+(busy?42:0),py+ph-20,notice.startsWith("!")?RED:GREEN);
        g.pose().popMatrix();
        // Tooltips are deferred by Minecraft and must use unscaled screen coordinates.
        AbstractWidget hovered=null;
        for(var widget:widgetTooltips.keySet())if(widget.isMouseOver(mx/uiScale,my/uiScale)){hovered=widget;break;}
        if(hovered!=tooltipTarget){tooltipTarget=hovered;tooltipSince=System.nanoTime();}
        if(hovered!=null&&System.nanoTime()-tooltipSince>350_000_000L)
            g.setTooltipForNextFrame(font,font.split(widgetTooltips.get(hovered),Math.max(40,Math.min(320,width-24))),mx,my);
    }
    private final class QButton extends AbstractWidget {
        private final Runnable action;
        private boolean selected,flat,iconOnly;private float hoverMix;private long hoverAt;private int icon=-1;private String detail;
        QButton(int x,int y,int w,int h,Component label,Runnable action){super(x,y,w,h,label);this.action=action;}
        @Override public void onClick(MouseButtonEvent event,boolean doubleClick){action.run();}
        @Override protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor g,int mx,int my){ /* QwenScreen schedules scaled tooltip */ }
        @Override public boolean keyPressed(KeyEvent event){if(active&&isFocused()&&(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_SPACE||event.key()==GLFW.GLFW_KEY_KP_ENTER)){action.run();return true;}return false;}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            long now=System.nanoTime();float elapsed=hoverAt==0?1:Math.min(1,(now-hoverAt)/110_000_000f);hoverAt=now;
            hoverMix=cfg().animation?hoverMix+((isHoveredOrFocused()?1:0)-hoverMix)*elapsed:(isHoveredOrFocused()?1:0);
            int shade=48+Math.round(21*hoverMix);int color=!active?0xFF252525:selected?LIGHT:0xFF000000|(shade<<16)|(shade<<8)|shade;
            if(!flat||isHoveredOrFocused())round(g,getX(),getY(),getWidth(),getHeight(),6,color);
            if(iconOnly){drawIcon(g,getX()+(getWidth()-12)/2,getY()+(getHeight()-12)/2,icon,active?TEXT:MUTED);return;}
            if(detail!=null){if(icon>=0)drawIcon(g,getX()+8,getY()+10,icon,active?MUTED:0xFF666666);int indent=icon>=0?27:8;drawText(g,component(clip(getMessage().getString(),getWidth()-indent-8)),getX()+indent,getY()+5,active?TEXT:MUTED);drawText(g,component(clip(detail,getWidth()-indent-8)),getX()+indent,getY()+20,MUTED);return;}
            if(icon>=0){int motion=cfg().animation&&(isHoveredOrFocused()||(icon==5&&busy))?(int)Math.round(Math.sin(System.nanoTime()/180000000d)):0;drawIcon(g,getX()+8,getY()+(getHeight()-12)/2+motion,icon,selected?DARK:MUTED);}
            String label=clip(getMessage().getString(),getWidth()-(icon>=0?29:6));int tx=icon>=0?getX()+25:getX()+(getWidth()-font.width(component(label)))/2;
            drawText(g,component(label),tx,getY()+(getHeight()-9)/2+1,selected?DARK:active?TEXT:MUTED);
        }
    }
    private void drawText(GuiGraphicsExtractor g,Component c,int x,int y,int color){g.text(font,c,x,y,color,false);}
    private void drawText(GuiGraphicsExtractor g,FormattedCharSequence c,int x,int y,int color){g.text(font,c,x,y,color,false);}
    private static void drawIcon(GuiGraphicsExtractor g,int x,int y,int kind,int color){Gfx.icon(g,x,y,kind,color);}
    private static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){Gfx.roundRect(g,x,y,w,h,radius,color);}
}
