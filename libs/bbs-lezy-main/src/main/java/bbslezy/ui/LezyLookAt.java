package bbslezy.ui;

import bbslod.LodSettings;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import java.util.Map;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.ReplayBatchProcessor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Inserts look-at rotation keyframes at the current timeline playhead tick
 * instead of rewriting the entire channel. Allows stacking multiple look-at
 * keyframes at different ticks across multiple replays.
 *
 * <p>Uses multithreaded calculation across CPU cores for heavy math, and applies
 * keyframe mutations in small batches on the Minecraft main thread with frame yields
 * to prevent UI freezes and eliminate ConcurrentModificationException.</p>
 */
public class LezyLookAt
{
    private static final String[] ROTATION_CHANNELS = {"yaw", "pitch", "headYaw", "bodyYaw"};


    public static class BakedChannel
    {
        public final KeyframeChannel<Double> channel;
        public final float tick;
        public final double angle;

        public BakedChannel(KeyframeChannel<Double> channel, float tick, double angle)
        {
            this.channel = channel;
            this.tick = tick;
            this.angle = angle;
        }

        public void apply()
        {
            this.channel.insertInheriting(this.tick, this.angle);
        }
    }

    public static class BakedReplay
    {
        public final List<BakedChannel> channels = new ArrayList<>();

        public void apply()
        {
            for (BakedChannel bc : this.channels)
            {
                bc.apply();
            }
        }
    }

    public static ReplayBatchProcessor.Error lookAt(List<ReplayBatchProcessor.VisibleReplay> selected, Replay target, float tick, List<String> channels)
    {
        return lookAt(selected, target, tick, channels, null);
    }

    public static ReplayBatchProcessor.Error lookAt(List<ReplayBatchProcessor.VisibleReplay> selected, Replay target, float tick, List<String> channels, UIFilmPanel filmPanel)
    {
        if (target == null)
        {
            return ReplayBatchProcessor.Error.NEED_TARGET;
        }

        if (filmPanel != null && filmPanel.getUndoHandler() != null)
        {
            filmPanel.getUndoHandler().submitUndo(true);
            LezyUndoHelper.setBatchLock(true);
        }

        try
        {
            boolean hasAnyRotation = false;

            if (channels != null)
            {
                for (String ch : channels)
                {
                    for (String rot : ROTATION_CHANNELS)
                    {
                        if (rot.equals(ch))
                        {
                            hasAnyRotation = true;
                            break;
                        }
                    }
                }
            }

            boolean allChannels = !hasAnyRotation;

            for (ReplayBatchProcessor.VisibleReplay replay : selected)
            {
                BakedReplay baked = computeForReplay(replay.replay, target, tick, allChannels, channels);

                if (baked != null)
                {
                    baked.apply();
                }
            }
        }
        finally
        {
            LezyUndoHelper.setBatchLock(false);

            if (filmPanel != null && filmPanel.getUndoHandler() != null)
            {
                filmPanel.getUndoHandler().submitUndo(true);
                updateEntitiesAfterBaking(filmPanel, selected);
                filmPanel.replayEditor.updateChannelsList();
                filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
            }
        }

        return null;
    }

    public static void lookAtAsync(
        List<ReplayBatchProcessor.VisibleReplay> selected,
        Replay target,
        float tick,
        List<String> channels,
        UIBakingProgressOverlayPanel progressPanel,
        UIFilmPanel filmPanel)
    {
        boolean hasAnyRotation = false;

        if (channels != null)
        {
            for (String ch : channels)
            {
                for (String rot : ROTATION_CHANNELS)
                {
                    if (rot.equals(ch))
                    {
                        hasAnyRotation = true;
                        break;
                    }
                }
            }
        }

        boolean allChannels = !hasAnyRotation;
        if (filmPanel != null && filmPanel.getUndoHandler() != null)
        {
            filmPanel.getUndoHandler().submitUndo(true);
            LezyUndoHelper.setBatchLock(true);
        }

        int total = selected.size();
        int percent = LodSettings.bakingBatchPercent != null ? LodSettings.bakingBatchPercent.get() : 5;
        percent = Math.max(1, Math.min(100, percent));
        int batchSize = Math.max(1, (int) Math.ceil(total * (percent / 100.0)));
        long delayMs = Math.max(10L, Math.min(30L, 450L / Math.max(1, (int) Math.ceil((double) total / batchSize))));

        if (progressPanel != null)
        {
            progressPanel.updateProgress(0.05F, "Calculating orientation vectors...");
        }

        ForkJoinPool.commonPool().execute(() ->
        {
            List<BakedReplay> bakedList = selected.parallelStream()
                .map(vr -> computeForReplay(vr.replay, target, tick, allChannels, channels))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            MinecraftClient.getInstance().execute(() ->
            {
                applyNextBatch(bakedList, selected, 0, batchSize, delayMs, progressPanel, filmPanel);
            });
        });
    }

    private static void applyNextBatch(
        List<BakedReplay> baked,
        List<ReplayBatchProcessor.VisibleReplay> selected,
        int index,
        int batchSize,
        long delayMs,
        UIBakingProgressOverlayPanel progressPanel,
        UIFilmPanel filmPanel)
    {
        int total = baked.size();
        int end = Math.min(index + batchSize, total);

        for (int i = index; i < end; i++)
        {
            baked.get(i).apply();
        }

        float bakeFraction = total == 0 ? 1F : (float) end / total;
        float p = bakeFraction * 0.80F;
        int pct = Math.round(p * 100F);
        String status = "Baking keyframes: " + pct + "% (" + end + " / " + total + ")";

        if (progressPanel != null)
        {
            progressPanel.updateProgress(p, status);
        }

        if (end < total)
        {
            ForkJoinPool.commonPool().execute(() ->
            {
                try
                {
                    Thread.sleep(delayMs);
                }
                catch (InterruptedException ignored)
                {}

                MinecraftClient.getInstance().execute(() ->
                {
                    applyNextBatch(baked, selected, end, batchSize, delayMs, progressPanel, filmPanel);
                });
            });
        }
        else
        {
            if (progressPanel != null)
            {
                progressPanel.updateProgress(0.85F, "Updating actor rotations (" + total + " actors)...");
            }

            ForkJoinPool.commonPool().execute(() ->
            {
                try
                {
                    Thread.sleep(delayMs);
                }
                catch (InterruptedException ignored)
                {}

                MinecraftClient.getInstance().execute(() ->
                {
                    if (filmPanel != null)
                    {
                        updateEntitiesAfterBaking(filmPanel, selected);
                        filmPanel.replayEditor.updateChannelsList();
                    }
                    if (progressPanel != null)
                    {
                        progressPanel.updateProgress(0.95F, "Finalizing undo history...");
                    }

                    ForkJoinPool.commonPool().execute(() ->
                    {
                        try
                        {
                            Thread.sleep(delayMs);
                        }
                        catch (InterruptedException ignored)
                        {}

                        MinecraftClient.getInstance().execute(() ->
                        {
                            LezyUndoHelper.setBatchLock(false);

                            if (filmPanel != null && filmPanel.getUndoHandler() != null)
                            {
                                filmPanel.getUndoHandler().submitUndo(true);
                                filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
                            }

                            if (progressPanel != null)
                            {
                                progressPanel.updateProgress(1.0F, "Done! 100% (" + total + " / " + total + ")");
                            }

                            ForkJoinPool.commonPool().execute(() ->
                            {
                                try
                                {
                                    Thread.sleep(60L);
                                }
                                catch (InterruptedException ignored)
                                {}

                                MinecraftClient.getInstance().execute(() ->
                                {
                                    if (progressPanel != null)
                                    {
                                        progressPanel.markFinished();
                                        progressPanel.close();
                                    }
                                });
                            });
                        });
                    });
                });
            });
        }
    }
    public static void updateEntitiesAfterBaking(UIFilmPanel filmPanel, List<ReplayBatchProcessor.VisibleReplay> selected)
    {
        if (filmPanel == null || filmPanel.getData() == null || filmPanel.getController() == null)
        {
            return;
        }

        UIFilmController controller = filmPanel.getController();
        Map<String, IEntity> entities = controller.getEntities();

        if (entities == null || entities.isEmpty())
        {
            controller.createEntities();
            return;
        }

        int filmTick = filmPanel.getCursor();

        for (ReplayBatchProcessor.VisibleReplay vr : selected)
        {
            Replay replay = vr.replay;
            IEntity entity = entities.get(replay.getId());

            if (entity != null)
            {
                int ticks = replay.getTick(filmTick);
                replay.keyframes.apply(ticks, entity);
                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());
            }
        }
    }

    public static BakedReplay computeForReplay(
        Replay replay,
        Replay target,
        float tick,
        boolean allChannels,
        List<String> channels)
    {
        if (replay == target)
        {
            return null;
        }

        ReplayKeyframes src = replay.keyframes;
        BakedReplay baked = new BakedReplay();

        for (String id : ROTATION_CHANNELS)
        {
            if (!allChannels && !channels.contains(id))
            {
                continue;
            }

            KeyframeChannel<Double> channel = channel(src, id);

            if (channel == null)
            {
                continue;
            }

            boolean isPitch = id.equals("pitch");
            double angle = compute(tick, src, target.keyframes, isPitch);
            double unwrapped = unwrap(channel, tick, angle, isPitch);

            baked.channels.add(new BakedChannel(channel, tick, unwrapped));
        }

        return baked.channels.isEmpty() ? null : baked;
    }

    private static KeyframeChannel<Double> channel(ReplayKeyframes keyframes, String id)
    {
        return switch (id)
        {
            case "yaw" -> keyframes.yaw;
            case "pitch" -> keyframes.pitch;
            case "headYaw" -> keyframes.headYaw;
            case "bodyYaw" -> keyframes.bodyYaw;
            default -> null;
        };
    }

    public static double compute(float tick, ReplayKeyframes src, ReplayKeyframes target, boolean pitch)
    {
        double sx = src.x.interpolate(tick), sy = src.y.interpolate(tick), sz = src.z.interpolate(tick);
        double tx = target.x.interpolate(tick), ty = target.y.interpolate(tick), tz = target.z.interpolate(tick);
        double dx = tx - sx, dy = ty - sy, dz = tz - sz;

        if (pitch)
        {
            double h = Math.sqrt(dx * dx + dz * dz);

            return -Math.atan2(dy, h) * (180D / Math.PI);
        }

        return Math.atan2(dz, dx) * (180D / Math.PI) - 90D;
    }

    /**
     * Prevent angle wrapping from taking the long way around (e.g. 350° to 10° swinging through 180°).
     * Smooths relative to the keyframe immediately preceding the target tick.
     */
    public static double unwrap(KeyframeChannel<Double> channel, float tick, double angle, boolean isPitch)
    {
        if (isPitch)
        {
            return angle;
        }

        Keyframe<Double> prev = null;

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() < tick)
            {
                prev = keyframe;
            }
            else
            {
                break;
            }
        }

        if (prev == null)
        {
            return angle;
        }

        double diff = (angle - prev.getValue()) % 360D;

        if (diff < -180D)
        {
            diff += 360D;
        }
        else if (diff > 180D)
        {
            diff -= 360D;
        }

        return prev.getValue() + diff;
    }
}
