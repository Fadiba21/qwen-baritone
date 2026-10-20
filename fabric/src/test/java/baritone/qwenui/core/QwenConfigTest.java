package baritone.qwenui.core;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class QwenConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path path(){return temp.getRoot().toPath().resolve("qwenui.json");}
    @Test public void legacyConfigMigratesWithoutLosingWindow()throws Exception{
        Files.writeString(path(),"{\"language\":\"id_id\",\"uiWidth\":710,\"customWindowSize\":true,\"uiScale\":2}");
        var cfg=QwenConfig.load(path());assertTrue(cfg.blurBackground);assertEquals(0,cfg.uiScalePx);assertEquals(710,cfg.uiWidth);assertTrue(cfg.customWindowSize);assertEquals("minecraft",cfg.fontFamily);
    }
    @Test public void oldFontSettingIsIgnoredAndMinecraftFontIsAlwaysUsed()throws Exception{
        Files.writeString(path(),"{\"fontFamily\":\"sans\",\"blurBackground\":false,\"uiWidth\":710}");
        var cfg=QwenConfig.load(path());assertEquals("minecraft",cfg.fontFamily);assertFalse(cfg.blurBackground);assertEquals(710,cfg.uiWidth);
        cfg.fontFamily="invalid";cfg.save(path());assertEquals("minecraft",QwenConfig.load(path()).fontFamily);
    }
    @Test public void blurAndScaleRoundTrip()throws Exception{
        var cfg=new QwenConfig();cfg.blurBackground=false;cfg.uiScalePx=3;cfg.save(path());var read=QwenConfig.load(path());assertFalse(read.blurBackground);assertEquals(3,read.uiScalePx);
    }
    @Test public void scaleClampsOnLoad()throws Exception{
        Files.writeString(path(),"{\"uiScalePx\":9999}");assertEquals(8,QwenConfig.load(path()).uiScalePx);
        Files.writeString(path(),"{\"uiScalePx\":-5}");assertEquals(0,QwenConfig.load(path()).uiScalePx);
    }
    @Test public void scaleClampsOnSave()throws Exception{
        var cfg=new QwenConfig();cfg.uiScalePx=9999;cfg.save(path());assertEquals(8,QwenConfig.load(path()).uiScalePx);
    }
    @Test public void keyNeverPersistsWithoutOptIn()throws Exception{
        var cfg=new QwenConfig();cfg.apiKey="fake-test-key-not-a-credential";cfg.save(path());assertFalse(Files.readString(path()).contains(cfg.apiKey));assertEquals("",QwenConfig.load(path()).apiKey);assertFalse(cfg.toString().contains(cfg.apiKey));
    }
    @Test public void optedInKeyStillRoundTrips()throws Exception{
        var cfg=new QwenConfig();cfg.apiKey="fake-test-key-not-a-credential";cfg.rememberApiKey=true;cfg.save(path());assertEquals(cfg.apiKey,QwenConfig.load(path()).apiKey);
    }
    @Test public void corruptConfigUsesDefaultsAndPreservesBackup()throws Exception{
        Files.writeString(path(),"not-json");var cfg=QwenConfig.load(path());assertTrue(cfg.blurBackground);assertEquals(0,cfg.uiScalePx);try(var files=Files.list(temp.getRoot().toPath())){assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("qwenui.json.corrupt-")));}
    }
}
