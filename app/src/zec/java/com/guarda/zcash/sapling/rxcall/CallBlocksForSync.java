package com.guarda.zcash.sapling.rxcall;

import com.guarda.zcash.sapling.db.DbManager;
import com.guarda.zcash.sapling.db.model.BlockRoom;

import java.util.List;
import java.util.concurrent.Callable;

import timber.log.Timber;

import static com.guarda.zcash.sapling.rxcall.CallLastBlock.FIRST_BLOCK_TO_SYNC_MAINNET;


public class CallBlocksForSync implements Callable<List<BlockRoom>> {

    private DbManager dbManager;

//    private Long startScanBlocksHeight = 620000L; //testnet
//    private Long startScanBlocksHeight = 551912L;
    private Long startScanBlocksHeight = 900000L;
    private Long nearStateHeightForStartSync = FIRST_BLOCK_TO_SYNC_MAINNET;

    public CallBlocksForSync(DbManager dbManager, long nearStateHeightForStartSync) {
        this.dbManager = dbManager;
        this.nearStateHeightForStartSync = nearStateHeightForStartSync;
    }

    @Override
    public List<BlockRoom> call() throws Exception {
        Timber.d("started");

        //get last with stored tree state
        BlockRoom lastBlockWithTree = dbManager.getAppDb().getBlockDao().getLatestBlockWithTree();
        if (lastBlockWithTree != null && !lastBlockWithTree.getTree().isEmpty()) {
            startScanBlocksHeight = lastBlockWithTree.getHeight();
        } else {
            startScanBlocksHeight = nearStateHeightForStartSync;
        }

        Timber.d("startScanBlocksHeight=%d", startScanBlocksHeight);

        //blocks after last block with tree state (excluded)
        List<BlockRoom> blocks = dbManager.getAppDb().getBlockDao().getBlocksOrderedFromHeight(startScanBlocksHeight);
        return blocks;
    }
}
