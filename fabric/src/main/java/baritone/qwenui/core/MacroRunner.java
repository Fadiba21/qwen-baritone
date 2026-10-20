package baritone.qwenui.core;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;

import java.util.Objects;

/**
 * Tick-driven macro executor. The mod entrypoint must call {@link #tick()} on the client thread;
 * constructing this object never starts a macro or registers a background loop.
 */
public final class MacroRunner {
    private final BaritoneBridge bridge;
    private QwenConfig.Macro macro;
    private int commandIndex;
    private int repetitionsLeft;
    private int ticksUntilNext;
    private boolean running;
    private boolean paused;
    private String lastFailure = "";
    private Thread clientThread;

    public MacroRunner() { this(new BaritoneBridge()); }
    public MacroRunner(BaritoneBridge bridge) { this.bridge = Objects.requireNonNull(bridge); }

    /** Starts one explicitly selected macro; repeat count is clamped again at this trust boundary. */
    public void start(QwenConfig.Macro selected) {
        requireClientThread();
        if (selected == null || selected.commands == null || selected.commands.isEmpty())
            throw new IllegalArgumentException("Macro harus memiliki setidaknya satu command.");
        this.macro = new QwenConfig.Macro(selected.name, selected.commands, selected.delayTicks, selected.repeatCount);
        this.commandIndex = 0;
        this.repetitionsLeft = this.macro.repeatCount;
        this.ticksUntilNext = 0;
        this.paused = false;
        this.lastFailure = "";
        this.running = true;
    }

    /** Call once per client tick. Commands are emitted at most once per tick and only while Baritone is idle. */
    public void tick() {
        requireClientThread();
        if (!running || paused) return;
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null || isBaritoneBusy(baritone)) return;
        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            return;
        }
        String command = macro.commands.get(commandIndex);
        try {
            if (!bridge.executeStrict(command)) {
                stopWithFailure("Baritone tidak mengenali atau tidak dapat menjalankan command macro.");
                return;
            }
        } catch (RuntimeException failure) {
            // Do not print exception messages: command arguments must not leak through diagnostics.
            stopWithFailure("Command macro gagal; eksekusi dihentikan.");
            return;
        }
        commandIndex++;
        if (commandIndex >= macro.commands.size()) {
            commandIndex = 0;
            repetitionsLeft--;
            if (repetitionsLeft <= 0) {
                running = false;
                macro = null;
                return;
            }
        }
        ticksUntilNext = macro.delayTicks;
    }

    public void pause() { requireClientThread(); if (running) paused = true; }
    public void resume() { requireClientThread(); if (running) paused = false; }
    public void cancel() {
        requireClientThread();
        running = false;
        paused = false;
        macro = null;
        commandIndex = 0;
        repetitionsLeft = 0;
        ticksUntilNext = 0;
    }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public String lastFailure() { return lastFailure; }
    public String currentMacroName() { return macro == null ? "" : macro.name; }

    private boolean isBaritoneBusy(IBaritone baritone) {
        // isPathing alone is false during calculation and pauses. A controlling process can
        // also be active before its next path is submitted, so wait instead of overriding it.
        if (baritone.getPathingBehavior().isPathing()
                || baritone.getPathingBehavior().getInProgress().isPresent()) return true;
        return baritone.getPathingControlManager().mostRecentInControl()
                .map(IBaritoneProcess::isActive).orElse(false);
    }

    private void stopWithFailure(String message) {
        running = false;
        paused = false;
        macro = null;
        lastFailure = message;
    }

    private void requireClientThread() {
        Thread now = Thread.currentThread();
        if (clientThread == null) clientThread = now;
        else if (clientThread != now) throw new IllegalStateException("MacroRunner harus dipanggil pada client thread.");
    }
}
