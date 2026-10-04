package dev.arc2.worldevents.events;

import dev.arc2.worldevents.WorldEventsPlugin;
import dev.arc2.worldevents.event.EventType;
import dev.arc2.worldevents.event.StopReason;
import dev.arc2.worldevents.event.WorldEvent;
import dev.arc2.worldevents.util.Msg;
import dev.arc2.worldevents.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Math questions in chat; the first correct answer wins (20 golden apples by default + a jackpot roll).
 * <p>Anti-abuse: answers are hidden from public chat, each player gets a limited number of
 * guesses per question with a cooldown (no brute-force spamming).</p>
 */
public final class SmartiePantsEvent extends WorldEvent {

    private int total;
    private int asked;
    private String question = "";
    private long answer;
    private volatile boolean open;
    private long closeAt;
    private long nextAsk;
    private long spacingMs;
    private boolean finished;
    private final Map<UUID, Integer> guesses = new HashMap<>();
    private final Map<UUID, Long> lastGuess = new HashMap<>();

    public SmartiePantsEvent(WorldEventsPlugin plugin) {
        super(plugin, EventType.SMARTIE_PANTS);
    }

    @Override
    public boolean usesTimer() {
        return false;
    }

    @Override
    public boolean canResume() {
        return false;
    }

    public boolean acceptingAnswers() {
        return open;
    }

    @Override
    public boolean onStart(Map<String, Object> options) {
        total = Math.max(1, cfg().getInt("questions", 5));
        long answerMs = Math.max(10, cfg().getInt("answer-seconds", 60)) * 1000L;
        spacingMs = Math.max(answerMs + 10_000L, (endMillis - startMillis) / total);
        nextAsk = System.currentTimeMillis() + 10_000L;
        Msg.broadcast(Msg.box("blue", List.of(
                "<blue><bold>  ✎ SMARTIE PANTS ✎</bold>",
                "  <gray><white>" + total + "</white> math questions will appear in chat.",
                "  <gray>Type the answer in chat — <yellow>first correct answer wins</yellow>!",
                "  <gray>Your guesses are private. You get <white>" + cfg().getInt("max-guesses", 3) + "</white> guesses per question.",
                "  <gray>First question in <white>10 seconds</white>..."
        )));
        return true;
    }

    @Override
    public void tickSecond() {
        long now = System.currentTimeMillis();
        if (open && now >= closeAt) {
            open = false;
            Msg.broadcast("<blue>✎</blue> <gray>Time's up! Nobody got it. The answer was <yellow>" + answer + "</yellow>.");
            afterQuestion();
            return;
        }
        if (!open && !finished && asked < total && now >= nextAsk) ask();
    }

    private void ask() {
        generate();
        asked++;
        open = true;
        long now = System.currentTimeMillis();
        closeAt = now + Math.max(10, cfg().getInt("answer-seconds", 60)) * 1000L;
        nextAsk = now + spacingMs;
        guesses.clear();
        lastGuess.clear();
        Msg.broadcast(Msg.box("blue", List.of(
                "<blue><bold>  ✎ SMARTIE PANTS — Question " + asked + "/" + total + "</bold>",
                "  <white><bold><q></bold> <gray>= <yellow>?",
                "  <gray>Type your answer in chat! <dark_gray>(" + cfg().getInt("answer-seconds", 60) + "s)"
        ), Placeholder.unparsed("q", question)));
        Msg.titleAll(Msg.mm("<blue><bold>" + Msg.escape(question) + " = ?"), Msg.mm("<gray>Answer in chat!"), 100, 3000, 500);
        Msg.playAll(Msg.sound("minecraft:block.note_block.chime", 1f, 1.2f));
    }

    private void afterQuestion() {
        if (asked >= total) {
            finished = true;
            complete();
        } else {
            Msg.broadcast("<blue>✎</blue> <gray>Next question in <white>" + Math.max(0, (nextAsk - System.currentTimeMillis()) / 1000) + "s</white>.");
        }
    }

    /** Main thread. */
    public void handleGuess(Player p, long value) {
        if (!open || p.getGameMode() == GameMode.SPECTATOR) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        int max = Math.max(1, cfg().getInt("max-guesses", 3));
        int used = guesses.getOrDefault(id, 0);
        if (used >= max) {
            Msg.send(p, "<red>You're out of guesses for this question.");
            return;
        }
        long cd = Math.max(0, cfg().getLong("guess-cooldown-seconds", 2)) * 1000L;
        Long last = lastGuess.get(id);
        if (last != null && now - last < cd) {
            Msg.send(p, "<red>Slow down! Wait a moment before guessing again.");
            return;
        }
        lastGuess.put(id, now);
        guesses.put(id, used + 1);

        if (value != answer) {
            int left = max - used - 1;
            Msg.send(p, "<red>Wrong!</red> <gray>" + (left > 0 ? left + " guess" + (left == 1 ? "" : "es") + " left." : "No guesses left."));
            Msg.play(p, Msg.sound("minecraft:entity.villager.no", 0.8f, 1f));
            return;
        }

        open = false;
        plugin.rewards().give(id, plugin.rewards().all(cfgMapList("winner-rewards")));
        Msg.broadcast(Msg.box("gold", List.of(
                "<gold><bold>  ✎ CORRECT! ✎</bold>",
                "  <yellow><name></yellow> <gray>answered <white><q> = <a></white> first and wins the reward!"
        ), Placeholder.unparsed("name", p.getName()), Placeholder.unparsed("q", question), Placeholder.unparsed("a", String.valueOf(answer))));
        Msg.playAll(Msg.sound("minecraft:entity.player.levelup", 1f, 1.2f));
        plugin.rewards().rollJackpot(id, cfg().getDouble("winner-jackpot-chance", 0.20));
        afterQuestion();
    }

    // ------------------------------------------------------------ question generator

    private void generate() {
        int kind = Util.rnd().nextInt(6);
        switch (kind) {
            case 0 -> {
                int a = Util.range(12, 99), b = Util.range(3, 12), c = Util.range(3, 12);
                question = a + " + " + b + " × " + c;
                answer = a + (long) b * c;
            }
            case 1 -> {
                int a = Util.range(5, 30), b = Util.range(5, 30), c = Util.range(2, 9);
                question = "(" + a + " + " + b + ") × " + c;
                answer = (long) (a + b) * c;
            }
            case 2 -> {
                int a = Util.range(6, 25), b = Util.range(6, 25), c = Util.range(10, 99);
                question = a + " × " + b + " − " + c;
                answer = (long) a * b - c;
            }
            case 3 -> {
                int a = Util.range(4, 19), b = Util.range(10, 150);
                question = a + "² + " + b;
                answer = (long) a * a + b;
            }
            case 4 -> {
                int b = Util.range(3, 12), k = Util.range(4, 25), c = Util.range(5, 60);
                question = (b * k) + " ÷ " + b + " + " + c;
                answer = k + c;
            }
            default -> {
                int a = Util.range(100, 999), b = Util.range(100, 999), c = Util.range(10, 99);
                question = a + " + " + b + " − " + c;
                answer = (long) a + b - c;
            }
        }
    }

    @Override
    public Component bossBarName() {
        String state = open ? "<yellow>Answer now!</yellow>" : (asked >= total ? "<gray>Finishing..." : "<gray>Next question in <white>" + Math.max(0, (nextAsk - System.currentTimeMillis()) / 1000) + "s");
        return Msg.mm("<blue><bold>Smartie Pants</bold></blue> <gray>— Question <white>" + asked + "/" + total + "</white> — " + state);
    }

    @Override
    public float bossBarProgress() {
        return 1f - (float) asked / Math.max(1, total);
    }

    @Override
    public void onStop(StopReason reason) {
        open = false;
    }
}
