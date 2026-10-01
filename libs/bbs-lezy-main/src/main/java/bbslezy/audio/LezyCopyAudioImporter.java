package bbslezy.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.ImporterUtils;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.io.File;
import java.nio.file.Files;

/**
 * Copies dropped audio files into the audio folder byte-identical —
 * no conversion, no re-encode. Reading them back is the ffmpeg decode
 * in {@link LezyAudioCodecs}, so the originals stay untouched on disk.
 */
public class LezyCopyAudioImporter implements IImporter
{
    @Override
    public IKey getName()
    {
        return L10n.lang("bbslezy.importer.copy_audio");
    }

    @Override
    public File getDefaultFolder()
    {
        return BBSMod.getAudioFolder();
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        return ImporterUtils.checkFileExtension(context.files, LezyAudioCodecs.copyExtensions());
    }

    @Override
    public void importFiles(ImporterContext context)
    {
        for (File file : context.files)
        {
            try
            {
                File destination = context.getDestination(this);
                File target = new File(destination, ImporterUtils.getName(destination, file.getName()));

                Files.copy(file.toPath(), target.toPath());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
