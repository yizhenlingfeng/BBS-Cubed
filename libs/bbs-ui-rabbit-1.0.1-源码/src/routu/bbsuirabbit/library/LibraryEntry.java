package routu.bbsuirabbit.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What the film library keeps about one film besides the film itself: its colour label, its cover
 * and the strip of frames shown when the card is hovered. The pictures live as files next to the
 * index (see {@link LibraryStore}); this only says which version of each there is and how good it is.
 *
 * <p>Captured pictures are graded by how far (in ticks) they were taken from where they should
 * be: frame {@code i} belongs at the middle of the {@code i}-th eighth of the film, the automatic
 * cover at 30% of it. A closer capture replaces a farther one, and one at the same spot replaces
 * an old one after {@link #REFRESH_MS}, so the pictures follow the film as it gets edited.</p>
 */
public class LibraryEntry
{
    public static final int SLOTS = 8;

    public static final int COVER_NONE = 0;
    public static final int COVER_AUTO = 1;
    public static final int COVER_MANUAL = 2;
    public static final int COVER_CUSTOM = 3;

    /** Where the automatic cover is taken, as a fraction of the film. */
    public static final float COVER_AT = 0.3F;
    /** A picture taken at the same spot replaces one older than this (the film was probably edited since). */
    public static final long REFRESH_MS = 120_000L;
    /** Colour labels, 1..8 (0 is none). */
    public static final int LABELS = 8;

    /** Names the picture files, so renaming a film only touches the index. */
    public String uid = "";
    public int color;

    public int coverKind = COVER_NONE;
    public long coverVer;
    public float coverDist;
    public long coverTime;
    public int coverSpan;

    public final long[] frameVer = new long[SLOTS];
    public final float[] frameDist = new float[SLOTS];
    public final long[] frameTime = new long[SLOTS];
    public int frameSpan;

    public static float slotCenter(int slot, int span)
    {
        return (slot + 0.5F) * span / SLOTS;
    }

    public static int slotAt(int tick, int span)
    {
        return Math.max(0, Math.min(SLOTS - 1, (int) ((long) tick * SLOTS / Math.max(1, span))));
    }

    /**
     * Whether a capture {@code dist} ticks off its spot, of a film {@code span} ticks long, should
     * replace what is there ({@code ver} 0 = nothing yet).
     */
    public static boolean improves(long ver, float oldDist, long oldTime, int oldSpan, float dist, int span, long now)
    {
        if (ver == 0 || oldSpan != span)
        {
            return true;
        }

        if (dist < oldDist - 0.25F)
        {
            return true;
        }

        return now - oldTime > REFRESH_MS && dist <= oldDist + 0.5F;
    }

    public boolean wantsFrame(int slot, float dist, int span, long now)
    {
        return improves(this.frameVer[slot], this.frameDist[slot], this.frameTime[slot], this.frameSpan, dist, span, now);
    }

    public boolean wantsAutoCover(float dist, int span, long now)
    {
        if (this.coverKind == COVER_MANUAL || this.coverKind == COVER_CUSTOM)
        {
            return false;
        }

        return improves(this.coverKind == COVER_NONE ? 0 : this.coverVer, this.coverDist, this.coverTime, this.coverSpan, dist, span, now);
    }

    public boolean hasCover()
    {
        return this.coverKind != COVER_NONE && this.coverVer != 0;
    }

    public boolean hasFrames()
    {
        for (long ver : this.frameVer)
        {
            if (ver != 0)
            {
                return true;
            }
        }

        return false;
    }

    public boolean isEmpty()
    {
        return this.color == 0 && !this.hasCover() && !this.hasFrames();
    }

    public LibraryEntry copy()
    {
        LibraryEntry entry = new LibraryEntry();

        entry.fromJson(this.toJson());

        return entry;
    }

    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();
        JsonArray frames = new JsonArray();

        object.addProperty("uid", this.uid);
        object.addProperty("color", this.color);
        object.addProperty("cover_kind", this.coverKind);
        object.addProperty("cover_ver", this.coverVer);
        object.addProperty("cover_dist", this.coverDist);
        object.addProperty("cover_time", this.coverTime);
        object.addProperty("cover_span", this.coverSpan);
        object.addProperty("frame_span", this.frameSpan);

        for (int i = 0; i < SLOTS; i++)
        {
            JsonArray frame = new JsonArray();

            frame.add(this.frameVer[i]);
            frame.add(this.frameDist[i]);
            frame.add(this.frameTime[i]);
            frames.add(frame);
        }

        object.add("frames", frames);

        return object;
    }

    public void fromJson(JsonObject object)
    {
        this.uid = object.has("uid") ? object.get("uid").getAsString() : "";
        this.color = object.has("color") ? Math.max(0, Math.min(LABELS, object.get("color").getAsInt())) : 0;
        this.coverKind = object.has("cover_kind") ? object.get("cover_kind").getAsInt() : COVER_NONE;
        this.coverVer = object.has("cover_ver") ? object.get("cover_ver").getAsLong() : 0;
        this.coverDist = object.has("cover_dist") ? object.get("cover_dist").getAsFloat() : 0;
        this.coverTime = object.has("cover_time") ? object.get("cover_time").getAsLong() : 0;
        this.coverSpan = object.has("cover_span") ? object.get("cover_span").getAsInt() : 0;
        this.frameSpan = object.has("frame_span") ? object.get("frame_span").getAsInt() : 0;

        if (object.has("frames"))
        {
            JsonArray frames = object.getAsJsonArray("frames");

            for (int i = 0; i < SLOTS && i < frames.size(); i++)
            {
                JsonArray frame = frames.get(i).getAsJsonArray();

                this.frameVer[i] = frame.get(0).getAsLong();
                this.frameDist[i] = frame.get(1).getAsFloat();
                this.frameTime[i] = frame.get(2).getAsLong();
            }
        }
    }
}
