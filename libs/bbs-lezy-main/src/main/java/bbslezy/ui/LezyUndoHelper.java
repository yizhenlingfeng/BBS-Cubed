package bbslezy.ui;

public class LezyUndoHelper
{
    private static volatile boolean batchLock;

    public static void setBatchLock(boolean lock)
    {
        batchLock = lock;
    }

    public static boolean isBatchLocked()
    {
        return batchLock;
    }
}
