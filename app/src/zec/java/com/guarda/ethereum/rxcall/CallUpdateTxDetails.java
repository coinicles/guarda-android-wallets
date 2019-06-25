package com.guarda.ethereum.rxcall;

import com.guarda.ethereum.models.items.ZecTxResponse;
import com.guarda.zcash.sapling.db.DbManager;
import com.guarda.zcash.sapling.db.model.DetailsTxRoom;
import com.guarda.zcash.sapling.db.model.ReceivedNotesRoom;

import java.util.List;
import java.util.concurrent.Callable;


public class CallUpdateTxDetails implements Callable<String> {

    private DbManager dbManager;
    private ZecTxResponse tr;

    public CallUpdateTxDetails(DbManager dbManager, ZecTxResponse tr) {
        this.dbManager = dbManager;
        this.tr = tr;
    }

    @Override
    public String call() throws Exception {
        boolean isOut;
        Long txValue = 0L;

        List<String> nfList = dbManager.getAppDb().getTxInputDao().getNfByHash(tr.getHash());
        if (nfList != null) {
            for (String nf : nfList) {
                ReceivedNotesRoom note = dbManager.getAppDb().getReceivedNotesDao().getNoteByNf(nf);
                if (note != null) {
                    txValue += note.getValue();
                }
            }
        }

        List<String> cmuList = dbManager.getAppDb().getTxOutputDao().getCmByHash(tr.getHash());
        if (cmuList != null) {
            for (String cmu : cmuList) {
                ReceivedNotesRoom note = dbManager.getAppDb().getReceivedNotesDao().getNoteByCm(cmu);
                if (note != null) {
                    txValue -= note.getValue();
                }
            }
        }

        isOut = txValue > 0;

        dbManager
                .getAppDb()
                .getDetailsTxDao()
                .insertAll(new DetailsTxRoom(
                        tr.getHash(),
                        tr.getTime().longValue(),
                        Math.abs(txValue),
                        true,
                        tr.getConfirmations().longValue(),
                        "",
                        "",
                        isOut));

        return tr.getHash();
    }

}
